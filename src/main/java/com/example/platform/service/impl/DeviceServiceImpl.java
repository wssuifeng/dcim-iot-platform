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
import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.dto.DeviceImportResult;
import com.example.platform.dto.DeviceImportRow;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.CloudRoom;
import com.example.platform.entity.Device;
import com.example.platform.entity.DeviceType;
import com.example.platform.entity.Organization;
import com.example.platform.mapper.DeviceMapper;
import com.example.platform.service.AlertConfigService;
import com.example.platform.service.DeviceService;
import com.example.platform.service.DeviceTypeService;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.OrganizationService;
import com.example.platform.service.CloudRoomService;
import com.example.platform.service.DevicePointMetricContract;
import com.example.platform.common.exception.DataScopeViolationException;
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
 * 类功能:设备服务实现类，负责设备的CRUD、状态管理、编码唯一性校验及告警配置更新
 * */
@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    private static final int IMPORT_MAX_SIZE = 500;
    private static final Pattern IP_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    private final DeviceTypeService deviceTypeService;
    private final AlertConfigService alertConfigService;
    private final DataScopeService dataScopeService;
    private final OrganizationService organizationService;
    private final CloudRoomService cloudRoomService;

    public DeviceServiceImpl(DeviceTypeService deviceTypeService,
                             AlertConfigService alertConfigService,
                             DataScopeService dataScopeService,
                             OrganizationService organizationService,
                             CloudRoomService cloudRoomService) {
        this.deviceTypeService = deviceTypeService;
        this.alertConfigService = alertConfigService;
        this.dataScopeService = dataScopeService;
        this.organizationService = organizationService;
        this.cloudRoomService = cloudRoomService;
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
    public Device getDeviceOrThrow(Integer id) {
        Device device = getById(id);
        if (device == null) {
            throw new DeviceNotFoundException(id);
        }
        return device;
    }

    /*
     * 方法功能:更新指定设备的告警规则配置
     * 参数:id-设备ID, alertRuleConfig-告警规则配置对象
     * 返回数据:无
     * */
    @Override
    public void updateAlertConfig(Integer id, AlertRuleConfig alertRuleConfig) {
        Device device = getDeviceOrThrow(id);
        device.setAlertRuleConfig(alertRuleConfig);
        this.updateById(device);
        alertConfigService.evictDeviceAlertConfig(id);
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
        Device device = getDeviceOrThrow(id);
        ensureDeviceAccess(device, currentUserId, false);
        return device;
    }

    @Override
    public Device getScopedForWrite(Integer id, Long currentUserId) {
        Device device = getDeviceOrThrow(id);
        ensureDeviceAccess(device, currentUserId, true);
        return device;
    }

    @Override
    public List<Device> listScoped(Long currentUserId) {
        return list(buildScopedWrapper(null, null, currentUserId));
    }

    @Override
    public List<Device> findByNameLikeScoped(String name, Long currentUserId) {
        return list(buildScopedWrapper(null, name, currentUserId));
    }

    @Override
    public List<Device> findOnlineDevicesScoped(Long currentUserId) {
        return list(buildScopedWrapper(1, null, currentUserId));
    }

    @Override
    public Page<Device> pageByConditionScoped(Integer current, Integer size, Integer status,
                                               String name, Long currentUserId) {
        return page(new Page<>(current, size), buildScopedWrapper(status, name, currentUserId));
    }

    @Override
    public List<Device> listByConditionScoped(Integer status, String name, Long currentUserId) {
        return list(buildScopedWrapper(status, name, currentUserId));
    }

    @Override
    public Set<Integer> authorizedDeviceIds(Long currentUserId) {
        return list(buildScopedWrapper(null, null, currentUserId)).stream()
                .map(Device::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createScoped(Device device, Long currentUserId) {
        validateDeviceOwnership(device, currentUserId, true);
        save(device);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateScoped(Integer id, Device update, Long currentUserId) {
        Device existing = getDeviceOrThrow(id);
        ensureDeviceAccess(existing, currentUserId, true);

        Device merged = new Device();
        merged.setId(id);
        merged.setOrganizationId(update.getOrganizationId() != null
                ? update.getOrganizationId() : existing.getOrganizationId());
        merged.setCloudRoomId(update.getCloudRoomId() != null
                ? update.getCloudRoomId() : existing.getCloudRoomId());
        merged.setTypeId(update.getTypeId() != null ? update.getTypeId() : existing.getTypeId());
        validateDeviceOwnership(merged, currentUserId, true);

        update.setId(id);
        update.setOrganizationId(merged.getOrganizationId());
        update.setCloudRoomId(merged.getCloudRoomId());
        if (!updateById(update)) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND,
                    "更新设备失败，数据可能已被他人修改");
        }
    }

    @Override
    public void updateStatusScoped(Integer id, Integer status, Long currentUserId) {
        Device device = getDeviceOrThrow(id);
        ensureDeviceAccess(device, currentUserId, true);
        if (!updateStatus(id, status)) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND, "更新状态失败，设备不存在");
        }
    }

    @Override
    public void updateAlertConfigScoped(Integer id, AlertRuleConfig alertRuleConfig, Long currentUserId) {
        Device device = getDeviceOrThrow(id);
        ensureDeviceAccess(device, currentUserId, true);
        updateAlertConfig(id, alertRuleConfig);
    }

    @Override
    public void deleteScoped(Integer id, Long currentUserId) {
        Device device = getDeviceOrThrow(id);
        ensureDeviceAccess(device, currentUserId, true);
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
                        Organization organization = organizationService.findByCode(row.getOrganizationCode());
                        CloudRoom room = cloudRoomService.findByOrganizationAndCode(
                                organization.getId(), row.getCloudRoomCode());
                        Device device = new Device();
                        device.setDeviceName(row.getDeviceName().trim());
                        device.setDeviceCode(row.getDeviceCode().trim());
                        device.setTypeId(deviceType.getId());
                        device.setOrganizationId(organization.getId());
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
        if (row.getOrganizationCode() == null || row.getOrganizationCode().trim().isEmpty()) {
            return "组织编码为空，旧版 Excel 模板不再兼容";
        }
        if (row.getCloudRoomCode() == null || row.getCloudRoomCode().trim().isEmpty()) {
            return "机房编码为空，旧版 Excel 模板不再兼容";
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
        Organization organization = organizationService.findByCode(row.getOrganizationCode().trim());
        if (organization == null) {
            return "组织编码不存在";
        }
        if (organization.getStatus() != null && organization.getStatus() != 0) {
            return "组织已停用";
        }
        CloudRoom room = cloudRoomService.findByOrganizationAndCode(
                organization.getId(), row.getCloudRoomCode().trim());
        if (room == null) {
            return "机房编码不存在或不属于指定组织";
        }
        if (room.getStatus() != null && room.getStatus() != 0) {
            return "机房已停用";
        }
        try {
            ensureTargetScope(organization.getId(), room.getId(), currentUserId, true);
        } catch (BusinessException e) {
            return "当前用户无权向该组织或机房导入设备";
        }
        if (row.getIpAddress() != null && !row.getIpAddress().trim().isEmpty()
                && !IP_PATTERN.matcher(row.getIpAddress().trim()).matches()) {
            return "IP 地址格式不正确";
        }
        return null;
    }

    private LambdaQueryWrapper<Device> buildScopedWrapper(Integer status, String name,
                                                           Long currentUserId) {
        LambdaQueryWrapper<Device> wrapper = buildConditionWrapper(status, name);
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        if (context.isPlatformAdmin()) {
            throw new DataScopeViolationException("Device", "business-list");
        }
        if ("ROOMS".equals(context.getScopeType())) {
            if (context.getCloudRoomIds().isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(Device::getCloudRoomId, context.getCloudRoomIds());
            }
        } else if (!context.getOrganizationIds().isEmpty()) {
            wrapper.in(Device::getOrganizationId, context.getOrganizationIds());
        } else {
            wrapper.apply("1 = 0");
        }
        return wrapper;
    }

    private void validateDeviceOwnership(Device device, Long currentUserId, boolean write) {
        if (device.getOrganizationId() == null || device.getCloudRoomId() == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "设备必须挂靠组织和机房");
        }
        Organization organization = organizationService.getById(device.getOrganizationId());
        if (organization == null || (organization.getStatus() != null && organization.getStatus() != 0)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "组织不存在或已停用");
        }
        CloudRoom room = cloudRoomService.getById(device.getCloudRoomId());
        if (room == null || (room.getStatus() != null && room.getStatus() != 0)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "机房不存在或已停用");
        }
        if (!device.getOrganizationId().equals(room.getOrganizationId())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "设备组织与机房归属不一致");
        }
        DeviceType type = deviceTypeService.getTypeOrThrow(device.getTypeId());
        if (!DevicePointMetricContract.isSupportedDeviceType(type.getTypeCode())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "设备类型不在五类支持范围");
        }
        ensureTargetScope(device.getOrganizationId(), device.getCloudRoomId(), currentUserId, write);
    }

    private void ensureDeviceAccess(Device device, Long currentUserId, boolean write) {
        if (device.getOrganizationId() == null || device.getCloudRoomId() == null) {
            throw new DataScopeViolationException("Device", String.valueOf(device.getId()));
        }
        ensureTargetScope(device.getOrganizationId(), device.getCloudRoomId(), currentUserId, write);
    }

    private void ensureTargetScope(Long organizationId, Long cloudRoomId,
                                   Long currentUserId, boolean write) {
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        if (context.isPlatformAdmin()) {
            throw new DataScopeViolationException("Device", "platform-no-business-penetration");
        }
        if (write && !context.hasWritePermission()) {
            throw new DataScopeViolationException("Device", "read-only");
        }
        if ("ROOMS".equals(context.getScopeType())) {
            if (!context.getCloudRoomIds().contains(cloudRoomId)) {
                throw new DataScopeViolationException("CloudRoom", String.valueOf(cloudRoomId));
            }
            return;
        }
        if (!context.getOrganizationIds().contains(organizationId)) {
            throw new DataScopeViolationException("Organization", String.valueOf(organizationId));
        }
    }
}
