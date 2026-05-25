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

    /*
     * 方法功能:查询所有设备类型列表，按更新时间倒序排列
     * 参数:无
     * 返回数据:Result<List<DeviceType>>-全部设备类型信息列表
     * */
    @GetMapping
    public Result<List<DeviceType>> list() {
        return Result.success(deviceTypeService.listAll());
    }

    /*
     * 方法功能:根据ID查询单个设备类型详情
     * 参数:id-设备类型ID（路径参数）
     * 返回数据:Result<DeviceType>-查询到的设备类型对象
     * */
    @GetMapping("/{id}")
    public Result<DeviceType> getById(@PathVariable Integer id) {
        return Result.success(deviceTypeService.getTypeOrThrow(id));
    }

    /*
     * 方法功能:创建新设备类型
     * 参数:deviceType-设备类型JSON对象
     * 返回数据:Result<DeviceType>-创建成功的设备类型对象
     * */
    @PostMapping
    public Result<DeviceType> create(@Validated @RequestBody DeviceType deviceType) {
        return Result.success(deviceTypeService.create(deviceType));
    }

    /*
     * 方法功能:更新设备类型信息
     * 参数:id-设备类型ID（路径参数）, deviceType-待更新的设备类型对象
     * 返回数据:Result<DeviceType>-更新后的设备类型对象
     * */
    @PutMapping("/{id}")
    public Result<DeviceType> update(@PathVariable Integer id,
                                      @RequestBody DeviceType deviceType) {
        return Result.success(deviceTypeService.updateById(id, deviceType));
    }

    /*
     * 方法功能:根据ID删除设备类型（存在关联设备时拒绝删除）
     * 参数:id-设备类型ID（路径参数）
     * 返回数据:Result<Void>-操作结果
     * */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        deviceTypeService.deleteById(id);
        return Result.success();
    }

    /*
     * 方法功能:更新指定设备类型的告警规则配置
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
