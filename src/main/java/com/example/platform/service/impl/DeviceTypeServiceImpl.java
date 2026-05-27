package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.exception.DeviceTypeCodeAlreadyExistsException;
import com.example.platform.common.exception.DeviceTypeNotFound;
import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.entity.Device;
import com.example.platform.entity.DeviceType;
import com.example.platform.mapper.DeviceTypeMapper;
import com.example.platform.service.AlertConfigService;
import com.example.platform.service.DeviceService;
import com.example.platform.service.DeviceTypeService;
import org.springframework.stereotype.Service;

import java.util.List;

/*
 * 类功能:设备类型服务实现类，负责设备类型的CRUD、编码唯一性校验、删除关联检查及告警配置更新
 * */
@Service
public class DeviceTypeServiceImpl extends ServiceImpl<DeviceTypeMapper, DeviceType> implements DeviceTypeService {

    private final DeviceService deviceService;
    private final AlertConfigService alertConfigService;

    public DeviceTypeServiceImpl(DeviceService deviceService, AlertConfigService alertConfigService) {
        this.deviceService = deviceService;
        this.alertConfigService = alertConfigService;
    }

    @Override
    public DeviceType getTypeOrThrow(Integer id) {
        DeviceType type = getById(id);
        if (type == null) {
            throw new DeviceTypeNotFound(id);
        }
        return type;
    }

    @Override
    public void updateAlertConfig(Integer id, AlertRuleConfig alertRuleConfig) {
        DeviceType type = getTypeOrThrow(id);
        type.setAlertRuleConfig(alertRuleConfig);
        updateById(type);
        alertConfigService.evictTypeAlertConfig(id);
    }

    @Override
    public DeviceType findByTypeCode(String typeCode) {
        LambdaQueryWrapper<DeviceType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceType::getTypeCode, typeCode);
        return getOne(wrapper);
    }

    @Override
    public List<DeviceType> listAll() {
        LambdaQueryWrapper<DeviceType> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(DeviceType::getUpdateTime);
        return list(wrapper);
    }

    @Override
    public DeviceType create(DeviceType deviceType) {
        validateTypeCodeUnique(deviceType.getTypeCode(), null);
        save(deviceType);
        return deviceType;
    }

    @Override
    public DeviceType updateById(Integer id, DeviceType deviceType) {
        DeviceType existing = getTypeOrThrow(id);
        if (deviceType.getTypeCode() != null) {
            validateTypeCodeUnique(deviceType.getTypeCode(), id);
            existing.setTypeCode(deviceType.getTypeCode());
        }
        if (deviceType.getTypeName() != null) {
            existing.setTypeName(deviceType.getTypeName());
        }
        if (deviceType.getDescription() != null) {
            existing.setDescription(deviceType.getDescription());
        }
        if (deviceType.getAlertRuleConfig() != null) {
            existing.setAlertRuleConfig(deviceType.getAlertRuleConfig());
        }
        updateById(existing);
        return existing;
    }

    @Override
    public void deleteById(Integer id) {
        getTypeOrThrow(id);
        List<Device> associatedDevices = deviceService.findByTypeId(id);
        if (!associatedDevices.isEmpty()) {
            throw new IllegalStateException("该类型下存在" + associatedDevices.size() + "个关联设备，无法删除");
        }
        removeById(id);
    }

    /*
     * 方法功能:校验设备类型编码唯一性，排除指定ID（用于更新场景）
     * 参数:typeCode-待校验的类型编码, excludeId-排除的设备类型ID（更新时排除自身）
     * 返回数据:无（编码重复时抛出DeviceTypeCodeAlreadyExistsException）
     * */
    private void validateTypeCodeUnique(String typeCode, Integer excludeId) {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            return;
        }
        LambdaQueryWrapper<DeviceType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceType::getTypeCode, typeCode);
        if (excludeId != null) {
            wrapper.ne(DeviceType::getId, excludeId);
        }
        if (count(wrapper) > 0) {
            throw new DeviceTypeCodeAlreadyExistsException(typeCode);
        }
    }
}
