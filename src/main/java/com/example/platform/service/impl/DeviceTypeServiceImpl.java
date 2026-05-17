package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.exception.DeviceTypeNotFound;
import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.entity.DeviceType;
import com.example.platform.mapper.DeviceTypeMapper;
import com.example.platform.service.DeviceTypeService;
import org.springframework.stereotype.Service;

@Service
public class DeviceTypeServiceImpl extends ServiceImpl<DeviceTypeMapper, DeviceType> implements DeviceTypeService {
    @Override
    public void updateAlertConfig(Integer typeId, AlertRuleConfig alertRuleConfig) {
        DeviceType type = getTypeOrThrow(typeId);
        // 直接 set 对象，MyBatis-Plus 保存时自动转 JSON 存入数据库
        type.setAlertRuleConfig(alertRuleConfig);
        this.updateById(type);
    }
    public DeviceType getTypeOrThrow(int typeId) {
        DeviceType deviceType = getById(typeId);  // 直接根据主键查询，性能高
        if (deviceType == null) {
            throw new DeviceTypeNotFound(typeId);
        }
        return deviceType;
    }

    public DeviceType findByTypeCode(String typeCode) {
        LambdaQueryWrapper<DeviceType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceType::getTypeCode, typeCode);
        return getOne(wrapper);
    }
}
