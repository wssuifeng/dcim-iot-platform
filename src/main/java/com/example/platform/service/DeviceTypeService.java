package com.example.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.entity.DeviceType;

/*
 * 类功能:设备类型服务接口，定义设备类型的查询及告警配置更新等业务方法
 * */
public interface DeviceTypeService extends IService<DeviceType> {

    /*
     * 方法功能:设备类型存在性检查，不存在则抛出DeviceTypeNotFound异常
     * 参数:id-设备类型ID
     * 返回数据:DeviceType-查询到的设备类型对象
     * */
    DeviceType getTypeOrThrow(Integer id);

    /*
     * 方法功能:更新设备类型的默认告警规则配置
     * 参数:id-设备类型ID, alertRuleConfig-告警规则配置对象
     * 返回数据:无
     * */
    void updateAlertConfig(Integer id, AlertRuleConfig alertRuleConfig);
}
