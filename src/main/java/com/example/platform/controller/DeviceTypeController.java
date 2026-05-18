package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.service.DeviceTypeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/*
 * 类功能:设备类型接口层，提供设备类型的告警配置更新接口
 * */
@RestController
@RequestMapping("/device-types")
@Validated
public class DeviceTypeController {

    private final DeviceTypeService deviceTypeService;

    public DeviceTypeController(DeviceTypeService deviceTypeService) {
        this.deviceTypeService = deviceTypeService;
    }

    /*
     * 方法功能:更新设备类型的默认告警配置
     * 参数:id-设备类型ID（路径参数）, alertRuleConfig-告警规则配置对象
     * 返回数据:Result<Void>-操作结果
     * */
    @PutMapping("/{id}/alert-config")
    public Result<Void> updateAlertConfig(
            @PathVariable Integer id,
            @RequestBody @Validated AlertRuleConfig alertRuleConfig) {
        deviceTypeService.updateAlertConfig(id, alertRuleConfig);
        return Result.success();
    }
}
