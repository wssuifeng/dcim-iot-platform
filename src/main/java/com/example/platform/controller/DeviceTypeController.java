package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.service.DeviceTypeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/device-types")
public class DeviceTypeController {

    private final DeviceTypeService deviceTypeService;

    public DeviceTypeController(DeviceTypeService deviceTypeService) {
        this.deviceTypeService = deviceTypeService;
    }

    /**
     * 更新设备类型的默认告警配置
     * 前端传入的是标准的 JSON 对象，Spring 自动封装为 AlertRuleConfig
     */
    @PutMapping("/{id}/alert-config")
    public Result<?> updateAlertConfig(
            @PathVariable Integer id,
            @RequestBody @Validated AlertRuleConfig alertRuleConfig) { // 加上 @Validated 自动校验

        deviceTypeService.updateAlertConfig(id, alertRuleConfig);
        return Result.success();
    }
}
