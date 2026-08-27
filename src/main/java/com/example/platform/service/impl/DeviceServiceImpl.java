package com.example.platform.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DeviceCodeAlreadyExistsException;
import com.example.platform.common.exception.DeviceNotFoundException;
import com.example.platform.dto.DeviceImportResult;
import com.example.platform.dto.DeviceImportRow;
import com.example.platform.entity.CloudRoom;
import com.example.platform.entity.Device;
import com.example.platform.entity.DeviceType;
import com.example.platform.mapper.DeviceMapper;
import com.example.platform.service.DeviceService;
import com.example.platform.service.DeviceTypeService;
import com.example.platform.service.CloudRoomService;
import com.example.platform.service.DevicePointMetricContract;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/*
 * 类功能:设备服务实现类，负责设备的CRUD、状态管理、编码唯一性校验
 * */
@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    private static final int IMPORT_MAX_SIZE = 500;
    private static final Pattern IP_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    private final DeviceTypeService deviceTypeService;
    private final CloudRoomService cloudRoomService;
    private final com.example.platform.mapper.DeviceMapper deviceMapper;

    public DeviceServiceImpl(DeviceTypeService deviceTypeService,
                             CloudRoomService cloudRoomService,
                             com.example.platform.mapper.DeviceMapper deviceMapper) {
        this.deviceTypeService = deviceTypeService;
        this.cloudRoomService = cloudRoomService;
        this.deviceMapper = deviceMapper;
    }

    /*
     * 方法功能:根据设备名称模糊查询设备列表
     * 参数:name-设备名称关键词
     * 返回数据:List<Device>-匹配的设备列表
     * */
    @Override
    public List<Device> findByNameLike(String name) {
        return baseMapper.findByNameLike(name);
    }

    /*
     * 方法功能:查询所有在线设备（status=1），按更新时间倒序排列
     * 参数:无
     * 返回数据:List<Device>-在线设备列表
     * */
    @Override
    public List<Device> findOnlineDevices() {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getStatus, 1)
                .orderByDesc(Device::getUpdateTime);
        return list(wrapper);
    }

    /*
     * 方法功能:更新指定设备的状态
     * 参数:id-设备ID, status-目标状态值
     * 返回数据:boolean-更新是否成功
     * */
    @Override
    public boolean updateStatus(Integer id, Integer status) {
        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Device::getId, id)
                .set(Device::getStatus, status);
        return update(wrapper);
    }

    /*
     * 方法功能:新增设备，保存前校验编码唯一性和设备类型有效性
     * 参数:device-待新增的设备对象
     * 返回数据:boolean-保存是否成功
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean save(Device device) {
        // 1. 校验设备编码唯一性
        deviceCodeExists(device.getDeviceCode());

        // 2. 校验设备类型是否存在
        deviceTypeService.getTypeOrThrow(device.getTypeId());

        // 3. 校验通过后执行INSERT
        return super.save(device);
    }

    /*
     * 方法功能:校验设备编码是否已存在，存在则抛出异常
     * 参数:deviceCode-设备编码
     * 返回数据:无
     * */
    public void deviceCodeExists(String deviceCode){
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceCode, deviceCode);

        if(count(wrapper) > 0){
            throw new DeviceCodeAlreadyExistsException(deviceCode);
        }
    }

    /*
     * 方法功能:根据设备类型ID查询设备列表
     * 参数:typeId-设备类型ID
     * 返回数据:List<Device>-该类型下的设备列表
     * */
    @Override
    public List<Device> findByTypeId(Integer typeId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getTypeId, typeId);
        return list(wrapper);
    }

    /*
     * 方法功能:按状态分组统计设备数量
     * 参数:无
     * 返回数据:List<Map<String, Object>>-每个Map包含status和count两个字段
     * */
    @Override
    public List<Map<String, Object>> countByStatus() {
        return baseMapper.countByStatus();
    }

    /*
     * 方法功能:设备存在性检查，不存在则抛出DeviceNotFoundException
     * 参数:id-设备ID
     * 返回数据:Device-查询到的设备对象
     * */
    @Override
    public void markIngestSource(java.util.Collection<Integer> deviceIds, Long credentialId) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return;
        }
        update(new LambdaUpdateWrapper<Device>()
                .in(Device::getId, deviceIds)
                .set(Device::getLastIngestCredentialId, credentialId));
    }

    @Override
    public Device findByCode(String deviceCode) {
        // 网关寻址视角：设备编码是物理身份，逻辑删除位不影响
        return deviceMapper.selectByCodeIgnoreDeleted(deviceCode);
    }

    /*
     * 方法功能:网关首包自动注册——系统建档并挂凭证机房（不依赖用户上下文）
     * 参数:deviceCode-设备编码, cloudRoomId-凭证绑定机房, typeId-凭证默认类型, credentialId-来源凭证
     * 返回数据:Device-新建的设备
     * */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Device autoRegisterDevice(String deviceCode, Long cloudRoomId, Integer typeId, Long credentialId) {
        // 逻辑删除的旧行占着唯一键：复活并改挂当前机房（网关世界里设备码是物理身份）
        Device tombstone = deviceMapper.selectByCodeIgnoreDeleted(deviceCode);
        if (tombstone != null && tombstone.getDeleted() != null && tombstone.getDeleted() == 1) {
            tombstone.setDeleted(0);
            tombstone.setCloudRoomId(cloudRoomId);
            tombstone.setTypeId(typeId);
            tombstone.setLastOnlineTime(LocalDateTime.now());
            tombstone.setLastIngestCredentialId(credentialId);
            deviceMapper.updateById(tombstone);
            return getById(tombstone.getId());
        }
        DeviceType type = deviceTypeService.getTypeOrThrow(typeId);
        if (!DevicePointMetricContract.isSupportedDeviceType(type.getTypeCode())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "自动注册默认类型不受支持: " + type.getTypeCode());
        }
        CloudRoom room = cloudRoomService.getById(cloudRoomId);
        if (room == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "凭证绑定机房不存在");
        }
        Device device = new Device();
        device.setDeviceCode(deviceCode);
        device.setDeviceName(deviceCode);
        device.setTypeId(typeId);
        device.setCloudRoomId(cloudRoomId);
        device.setStatus(1);
        device.setLocation("网关自动注册");
        device.setLastOnlineTime(LocalDateTime.now());
        device.setLastIngestCredentialId(credentialId);
        save(device);
        return device;
    }

    @Override
    public Device getDeviceOrThrow(Integer id) {
        Device device = getById(id);
        if (device == null) {
            throw new DeviceNotFoundException(id);
        }
        return device;
    }

    /*
     * 方法功能:查询最后在线时间早于指定阈值的所有设备（用于离线检测）
     * 参数:checkTime-判定阈值时间
     * 返回数据:List<Device>-离线设备列表
     * */
    @Override
    public List<Device> getOnlineDetection(LocalDateTime checkTime) {
        return baseMapper.getOnlineDetection(checkTime);
    }

    /*
     * 方法功能:按可选状态和名称条件分页查询设备，按更新时间倒序排列
     * 参数:current-当前页码, size-每页大小, status-设备状态（可选）, name-设备名称（可选，模糊匹配）
     * 返回数据:Page<Device>-分页设备数据
     * */
    @Override
    public Page<Device> pageByCondition(Integer current, Integer size, Integer status, String name) {
        Page<Device> page = new Page<>(current, size);
        LambdaQueryWrapper<Device> wrapper = buildConditionWrapper(status, name);
        return page(page, wrapper);
    }

    /*
     * 方法功能:按可选状态和名称条件查询全部设备，按更新时间倒序排列
     * 参数:status-设备状态（可选）, name-设备名称（可选，模糊匹配）
     * 返回数据:List<Device>-设备列表
     * */
    @Override
    public List<Device> listByCondition(Integer status, String name) {
        return list(buildConditionWrapper(status, name));
    }

    private LambdaQueryWrapper<Device> buildConditionWrapper(Integer status, String name) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Device::getStatus, status);
        }
        if (name != null && !name.trim().isEmpty()) {
            wrapper.like(Device::getDeviceName, name);
        }
        wrapper.orderByDesc(Device::getUpdateTime);
        return wrapper;
    }

    /*
     * 方法功能:更新设备最后在线时间并设置状态为在线
     * 参数:deviceId-设备ID
     * 返回数据:无
     * */
    @Override
    public void updateLastOnlineTime(Integer deviceId) {
        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Device::getId, deviceId)
                .set(Device::getLastOnlineTime, LocalDateTime.now())
                .set(Device::getStatus, 1);
        update(wrapper);
    }

    /*
     * 方法功能:从 Excel 文件批量导入设备
     * 说明:逐行校验，失败行跳过并记录原因，不使用整体事务以保证部分成功
     * 参数:file-Excel 文件
     * 返回数据:DeviceImportResult-导入结果
     * */
    @Override
    public DeviceImportResult importFromExcel(MultipartFile file) {
        throw new BusinessException(ResultCode.FORBIDDEN,
                "设备导入必须使用带当前用户数据范围校验的接口");
    }

    @Override
    public Device getScoped(Integer id, Long currentUserId) {
        return getDeviceOrThrow(id);
    }

    @Override
    public Device getScopedForWrite(Integer id, Long currentUserId) {
        return getDeviceOrThrow(id);
    }

    @Override
    public List<Device> listScoped(Long currentUserId) {
        return list(buildConditionWrapper(null, null));
    }

    @Override
    public List<Device> findByNameLikeScoped(String name, Long currentUserId) {
        return list(buildConditionWrapper(null, name));
    }

    @Override
    public List<Device> findOnlineDevicesScoped(Long currentUserId) {
        return list(buildConditionWrapper(1, null));
    }

    @Override
    public Page<Device> pageByConditionScoped(Integer current, Integer size, Integer status,
                                               String name, Long currentUserId) {
        return page(new Page<>(current, size), buildConditionWrapper(status, name));
    }

    @Override
    public List<Device> listByConditionScoped(Integer status, String name, Long currentUserId) {
        return list(buildConditionWrapper(status, name));
    }

    @Override
    public Set<Integer> authorizedDeviceIds(Long currentUserId) {
        return list(buildConditionWrapper(null, null)).stream()
                .map(Device::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createScoped(Device device, Long currentUserId) {
        validateDeviceOwnership(device);
        save(device);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateScoped(Integer id, Device update, Long currentUserId) {
        Device existing = getDeviceOrThrow(id);

        Device merged = new Device();
        merged.setId(id);
        merged.setCloudRoomId(update.getCloudRoomId() != null
                ? update.getCloudRoomId() : existing.getCloudRoomId());
        merged.setTypeId(update.getTypeId() != null ? update.getTypeId() : existing.getTypeId());
        validateDeviceOwnership(merged);

        update.setId(id);
        update.setCloudRoomId(merged.getCloudRoomId());
        if (!updateById(update)) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND,
                    "更新设备失败，数据可能已被他人修改");
        }
    }

    @Override
    public void updateStatusScoped(Integer id, Integer status, Long currentUserId) {
        Device device = getDeviceOrThrow(id);
        if (!updateStatus(id, status)) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND, "更新状态失败，设备不存在");
        }
    }

    @Override
    public void deleteScoped(Integer id, Long currentUserId) {
        getDeviceOrThrow(id);
        removeById(id);
    }

    @Override
    public DeviceImportResult importFromExcelScoped(MultipartFile file, Long currentUserId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_EMPTY, "上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException(ResultCode.FILE_FORMAT_ERROR, "仅支持 .xlsx 格式文件");
        }

        List<DeviceImportResult.FailDetail> failDetails = new ArrayList<>();
        List<Device> devicesToSave = new ArrayList<>();
        Set<String> seenDeviceCodes = new HashSet<>();

        try {
            EasyExcel.read(file.getInputStream(), DeviceImportRow.class, new ReadListener<DeviceImportRow>() {
                private int rowIndex = 1;

                @Override
                public void invoke(DeviceImportRow row, AnalysisContext context) {
                    if (rowIndex > IMPORT_MAX_SIZE) {
                        failDetails.add(DeviceImportResult.FailDetail.builder()
                                .rowIndex(rowIndex)
                                .deviceCode(row.getDeviceCode())
                                .reason("超过单次导入上限" + IMPORT_MAX_SIZE + "条")
                                .build());
                        rowIndex++;
                        return;
                    }

                    String reason = validateImportRow(row, seenDeviceCodes, currentUserId);
                    if (reason != null) {
                        failDetails.add(DeviceImportResult.FailDetail.builder()
                                .rowIndex(rowIndex)
                                .deviceCode(row.getDeviceCode())
                                .reason(reason)
                                .build());
                    } else {
                        seenDeviceCodes.add(row.getDeviceCode());
                        DeviceType deviceType = deviceTypeService.findByTypeCode(row.getTypeCode());
                        CloudRoom room = cloudRoomService.findByRoomCode(row.getCloudRoomCode());
                        Device device = new Device();
                        device.setDeviceName(row.getDeviceName().trim());
                        device.setDeviceCode(row.getDeviceCode().trim());
                        device.setTypeId(deviceType.getId());
                        device.setCloudRoomId(room.getId());
                        device.setLocation(row.getLocation());
                        device.setIpAddress(row.getIpAddress());
                        device.setStatus(0);
                        devicesToSave.add(device);
                    }
                    rowIndex++;
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // no-op
                }
            }).sheet().doRead();
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FILE_FORMAT_ERROR, "文件读取失败: " + e.getMessage());
        }

        int successCount = 0;
        for (Device device : devicesToSave) {
            try {
                super.save(device);
                successCount++;
            } catch (Exception e) {
                failDetails.add(DeviceImportResult.FailDetail.builder()
                        .deviceCode(device.getDeviceCode())
                        .reason("保存失败: " + e.getMessage())
                        .build());
            }
        }

        return DeviceImportResult.builder()
                .totalCount(successCount + failDetails.size())
                .successCount(successCount)
                .failCount(failDetails.size())
                .failDetails(failDetails)
                .build();
    }

    private String validateImportRow(DeviceImportRow row, Set<String> seenDeviceCodes,
                                     Long currentUserId) {
        if (row == null) {
            return "行数据为空";
        }
        if (row.getDeviceName() == null || row.getDeviceName().trim().isEmpty()) {
            return "设备名称为空";
        }
        if (row.getDeviceCode() == null || row.getDeviceCode().trim().isEmpty()) {
            return "设备编码为空";
        }
        if (row.getTypeCode() == null || row.getTypeCode().trim().isEmpty()) {
            return "类型编码为空";
        }
        if (row.getCloudRoomCode() == null || row.getCloudRoomCode().trim().isEmpty()) {
            return "机房编码为空";
        }
        String deviceCode = row.getDeviceCode().trim();
        if (seenDeviceCodes.contains(deviceCode)) {
            return "Excel 内设备编码重复";
        }
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceCode, deviceCode);
        if (count(wrapper) > 0) {
            return "设备编码已存在";
        }
        DeviceType deviceType = deviceTypeService.findByTypeCode(row.getTypeCode().trim());
        if (deviceType == null) {
            return "设备类型编码不存在";
        }
        if (!DevicePointMetricContract.isSupportedDeviceType(deviceType.getTypeCode())) {
            return "设备类型编码不在五类支持范围";
        }
        CloudRoom room = cloudRoomService.findByRoomCode(row.getCloudRoomCode().trim());
        if (room == null) {
            return "机房编码不存在";
        }
        if (room.getStatus() != null && room.getStatus() != 0) {
            return "机房已停用";
        }
        if (row.getIpAddress() != null && !row.getIpAddress().trim().isEmpty()
                && !IP_PATTERN.matcher(row.getIpAddress().trim()).matches()) {
            return "IP 地址格式不正确";
        }
        return null;
    }

    private void validateDeviceOwnership(Device device) {
        if (device.getCloudRoomId() == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "设备必须挂靠机房");
        }
        CloudRoom room = cloudRoomService.getById(device.getCloudRoomId());
        if (room == null || (room.getStatus() != null && room.getStatus() != 0)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "机房不存在或已停用");
        }
        DeviceType type = deviceTypeService.getTypeOrThrow(device.getTypeId());
        if (!DevicePointMetricContract.isSupportedDeviceType(type.getTypeCode())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "设备类型不在五类支持范围");
        }
    }
}
