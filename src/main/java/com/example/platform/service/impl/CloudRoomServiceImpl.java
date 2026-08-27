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
 * 类功能:云机房服务实现类，负责机房顶层资源的 CRUD 与编码唯一性校验
 *
 * 【业务约束】
 *   - roomCode 全局唯一
 *   - status 仅允许 0(正常)/1(停用)
 * */
@Slf4j
@Service
public class CloudRoomServiceImpl extends ServiceImpl<CloudRoomMapper, CloudRoom> implements CloudRoomService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CloudRoom createCloudRoom(CloudRoomCreateDTO createDTO, Long currentUserId) {
        checkRoomCodeUnique(createDTO.getRoomCode());

        CloudRoom cloudRoom = new CloudRoom();
        cloudRoom.setRoomName(createDTO.getRoomName());
        cloudRoom.setRoomCode(createDTO.getRoomCode());
        cloudRoom.setLocation(createDTO.getLocation());
        cloudRoom.setStatus(0);
        if (!save(cloudRoom)) {
            throw new BusinessException("创建机房失败，请稍后重试");
        }
        log.info("机房创建成功 roomCode={} roomName={}",
                cloudRoom.getRoomCode(), cloudRoom.getRoomName());
        return cloudRoom;
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
}
