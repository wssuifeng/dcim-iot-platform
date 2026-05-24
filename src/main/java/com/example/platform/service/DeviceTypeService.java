package com.example.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.entity.DeviceType;

import java.util.List;

/*
 * 类功能:设备类型服务接口，定义设备类型的CRUD、存在性校验及告警配置更新等业务方法
 * */
public interface DeviceTypeService extends IService<DeviceType> {

    DeviceType getTypeOrThrow(Integer id);

    void updateAlertConfig(Integer id, AlertRuleConfig alertRuleConfig);

    DeviceType findByTypeCode(String typeCode);

    List<DeviceType> listAll();

    DeviceType create(DeviceType deviceType);

    DeviceType updateById(Integer id, DeviceType deviceType);

    void deleteById(Integer id);
}
