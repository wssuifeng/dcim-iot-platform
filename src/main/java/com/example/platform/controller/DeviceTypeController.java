package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.entity.DeviceType;
import com.example.platform.service.DeviceTypeService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
 * 类功能:设备类型接口层，提供设备类型的完整CRUD及告警配置更新等RESTful接口
 * */
@RestController
@RequestMapping("/device-types")
@Validated
public class DeviceTypeController {

    private final DeviceTypeService deviceTypeService;

    public DeviceTypeController(DeviceTypeService deviceTypeService) {
        this.deviceTypeService = deviceTypeService;
    }

    @GetMapping
    public Result<List<DeviceType>> list() {
        return Result.success(deviceTypeService.listAll());
    }

    @GetMapping("/{id}")
    public Result<DeviceType> getById(@PathVariable Integer id) {
        return Result.success(deviceTypeService.getTypeOrThrow(id));
    }

    @PostMapping
    public Result<DeviceType> create(@Validated @RequestBody DeviceType deviceType) {
        return Result.success(deviceTypeService.create(deviceType));
    }

    @PutMapping("/{id}")
    public Result<DeviceType> update(@PathVariable Integer id,
                                      @RequestBody DeviceType deviceType) {
        return Result.success(deviceTypeService.updateById(id, deviceType));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        deviceTypeService.deleteById(id);
        return Result.success();
    }

    @PutMapping("/{id}/alert-config")
    public Result<Void> updateAlertConfig(
            @PathVariable Integer id,
            @RequestBody @Validated AlertRuleConfig alertRuleConfig) {
        deviceTypeService.updateAlertConfig(id, alertRuleConfig);
        return Result.success();
    }
}
