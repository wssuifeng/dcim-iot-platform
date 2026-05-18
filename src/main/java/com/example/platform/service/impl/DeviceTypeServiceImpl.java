package com.example.platform.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.exception.DeviceTypeNotFound;
import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.entity.DeviceType;
import com.example.platform.mapper.DeviceTypeMapper;
import com.example.platform.service.DeviceTypeService;
import org.springframework.stereotype.Service;

/*
 * 类功能:设备类型服务实现类，负责设备类型的查询、存在性校验及告警配置更新
 * */
@Service
public class DeviceTypeServiceImpl extends ServiceImpl<DeviceTypeMapper, DeviceType> implements DeviceTypeService {

    /*
     * 方法功能:设备类型存在性检查，不存在则抛出DeviceTypeNotFound异常
     * 参数:id-设备类型ID
     * 返回数据:DeviceType-查询到的设备类型对象
     * */
    @Override
    public DeviceType getTypeOrThrow(Integer id) {
        DeviceType type = getById(id);
        if (type == null) {
            throw new DeviceTypeNotFound(id);
        }
        return type;
    }

    /*
     * 方法功能:更新设备类型的默认告警规则配置
     * 参数:id-设备类型ID, alertRuleConfig-告警规则配置对象
     * 返回数据:无
     * */
    @Override
    public void updateAlertConfig(Integer id, AlertRuleConfig alertRuleConfig) {
        DeviceType type = getTypeOrThrow(id);
        type.setAlertRuleConfig(alertRuleConfig);
        updateById(type);
    }
}
