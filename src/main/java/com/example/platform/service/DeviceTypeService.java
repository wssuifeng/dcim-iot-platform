package com.example.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.entity.DeviceType;

public interface DeviceTypeService extends IService<DeviceType> {

    void updateAlertConfig(Integer typeId, AlertRuleConfig alertRuleConfig);

    DeviceType getTypeOrThrow(int typeId);
}
