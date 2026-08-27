package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.CloudRoomCreateDTO;
import com.example.platform.dto.CloudRoomUpdateDTO;
import com.example.platform.entity.CloudRoom;
import com.example.platform.mapper.CloudRoomMapper;
import com.example.platform.service.CloudRoomService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * 类功能:云机房服务实现类——机房顶层资源与其配套网关凭证（1:1）的生命周期所有者
 *
 * 【合一模型】创建机房即建凭证（AK 自动生成、auto_register 默认开）；停用机房联动停
 *   凭证（网关上报全拒）；删除机房级联删凭证（机房下有设备时拒绝删除）。
 * 【业务约束】roomCode 全局唯一；status 仅允许 0(正常)/1(停用)
 * */
@Slf4j
@Service
public class CloudRoomServiceImpl extends ServiceImpl<CloudRoomMapper, CloudRoom> implements CloudRoomService {

    private final com.example.platform.mapper.DeviceMapper deviceMapper;
    private final com.example.platform.ingest.service.IngestCredentialService ingestCredentialService;

    public CloudRoomServiceImpl(com.example.platform.mapper.DeviceMapper deviceMapper,
                                com.example.platform.ingest.service.IngestCredentialService ingestCredentialService) {
        this.deviceMapper = deviceMapper;
        this.ingestCredentialService = ingestCredentialService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public com.example.platform.dto.CloudRoomCreatedVO createCloudRoom(CloudRoomCreateDTO createDTO,
                                                                       Long currentUserId) {
        checkRoomCodeUnique(createDTO.getRoomCode());

        CloudRoom cloudRoom = new CloudRoom();
        cloudRoom.setRoomName(createDTO.getRoomName());
        cloudRoom.setRoomCode(createDTO.getRoomCode());
        cloudRoom.setLocation(createDTO.getLocation());
        cloudRoom.setStatus(0);
        if (!save(cloudRoom)) {
            throw new BusinessException("创建机房失败，请稍后重试");
        }
        // 合一模型：同事务初始化该机房网关凭证（secret 仅本次返回）
        com.example.platform.ingest.dto.IngestCredentialCreatedVO credential =
                ingestCredentialService.createCredentialForRoom(cloudRoom.getId(), currentUserId);
        log.info("机房创建成功（含接入凭证）roomCode={} roomName={} accessKey={}",
                cloudRoom.getRoomCode(), cloudRoom.getRoomName(), credential.getAccessKey());
        return com.example.platform.dto.CloudRoomCreatedVO.of(cloudRoom, credential);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CloudRoom updateCloudRoom(Long id, CloudRoomUpdateDTO updateDTO, Long currentUserId) {
        CloudRoom cloudRoom = getCloudRoomOrThrow(id, currentUserId);

        if (updateDTO.getRoomName() != null) {
            cloudRoom.setRoomName(updateDTO.getRoomName());
        }
        if (updateDTO.getLocation() != null) {
            cloudRoom.setLocation(updateDTO.getLocation());
        }
        if (updateDTO.getStatus() != null) {
            validateStatus(updateDTO.getStatus());
            cloudRoom.setStatus(updateDTO.getStatus());
        }
        if (!updateById(cloudRoom)) {
            throw new BusinessException("更新机房失败，数据可能已被他人修改");
        }
        return cloudRoom;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CloudRoom updateStatus(Long id, Integer status, Long currentUserId) {
        if (status == null || status < 0 || status > 1) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "状态值无效，允许值：0-正常，1-停用");
        }
        CloudRoom cloudRoom = getCloudRoomOrThrow(id, currentUserId);
        cloudRoom.setStatus(status);
        if (!updateById(cloudRoom)) {
            throw new BusinessException("更新机房状态失败");
        }
        // 联动凭证：停用机房 = 网关上报全拒；启用恢复
        ingestCredentialService.updateStatusByRoom(id, status, currentUserId);
        return cloudRoom;
    }

    @Override
    public CloudRoom getCloudRoomOrThrow(Long id, Long currentUserId) {
        CloudRoom cloudRoom = getById(id);
        if (cloudRoom == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "机房不存在: " + id);
        }
        return cloudRoom;
    }

    @Override
    public Page<CloudRoom> pageCloudRooms(Long currentUserId, Integer current, Integer size,
                                          String keyword, Integer status) {
        int[] params = PaginationUtils.validate(current, size);
        Page<CloudRoom> page = new Page<>(params[0], params[1]);
        LambdaQueryWrapper<CloudRoom> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(CloudRoom::getRoomName, keyword.trim());
        }
        if (status != null) {
            wrapper.eq(CloudRoom::getStatus, status);
        }
        wrapper.orderByDesc(CloudRoom::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public CloudRoom findByRoomCode(String roomCode) {
        if (roomCode == null || roomCode.isBlank()) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<CloudRoom>()
                .eq(CloudRoom::getRoomCode, roomCode.trim()), false);
    }

    private void checkRoomCodeUnique(String roomCode) {
        LambdaQueryWrapper<CloudRoom> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CloudRoom::getRoomCode, roomCode);
        if (count(wrapper) > 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "机房编码已存在: " + roomCode);
        }
    }

    private void validateStatus(Integer status) {
        if (status == null || status < 0 || status > 1) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "状态值无效，允许值：0-正常，1-停用");
        }
    }

    /*
     * 方法功能:删除机房（物理删除，级联删除配套凭证；机房下有设备时拒绝）
     * 参数:id-机房ID, currentUserId-操作用户
     * 返回数据:无（存在设备时抛业务异常）
     * */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCloudRoom(Long id, Long currentUserId) {
        CloudRoom room = getById(id);
        if (room == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "机房不存在");
        }
        Long deviceCount = deviceMapper.selectCount(
                new LambdaQueryWrapper<com.example.platform.entity.Device>()
                        .eq(com.example.platform.entity.Device::getCloudRoomId, id));
        if (deviceCount != null && deviceCount > 0) {
            throw new BusinessException(ResultCode.ROOM_IN_USE_BY_DEVICE,
                    "机房下仍有 " + deviceCount + " 台设备，须先删除或迁移设备");
        }
        // 合一模型：级联删除配套凭证（无设备才走到这里）
        ingestCredentialService.deleteCredentialByRoom(id, currentUserId);
        removeById(id);
        log.info("机房删除（凭证级联）roomId={} roomCode={} operator={}", id, room.getRoomCode(), currentUserId);
    }
}
