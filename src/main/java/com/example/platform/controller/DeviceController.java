package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.group.CreateGroup;
import com.example.platform.common.group.UpdateGroup;
import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.entity.Device;
import com.example.platform.service.DeviceService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
 * 类功能:设备管理接口层，提供设备的CRUD、状态更新、搜索分页及告警配置等RESTful接口
 * */
@RestController
@RequestMapping("/devices")
@Validated
public class DeviceController {
    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /*
     * 方法功能:查询所有设备列表
     * 参数:无
     * 返回数据:Result<List<Device>>-全部设备信息列表
     * */
    @GetMapping
    public Result<List<Device>> list() {
        return Result.success(deviceService.list());
    }

    /*
     * 方法功能:根据ID查询单个设备详情
     * 参数:id-设备ID（路径参数）
     * 返回数据:Result<Device>-查询到的设备对象
     * */
    @GetMapping("/{id}")
    public Result<Device> getById(@PathVariable Integer id) {
        return Result.success(deviceService.getDeviceOrThrow(id));
    }

    /*
     * 方法功能:新增设备
     * 参数:device-需符合CreateGroup校验规则的设备JSON对象
     * 返回数据:Result<Void>-操作结果
     * */
    @PostMapping
    public Result<Void> save(@Validated(CreateGroup.class) @RequestBody Device device) {
        deviceService.save(device);
        return Result.success();
    }

    /*
     * 方法功能:更新设备信息，以路径ID为准，防御性校验ID一致性
     * 参数:id-设备ID（路径参数）, device-需符合UpdateGroup校验规则的设备对象
     * 返回数据:Result<Void>-操作结果，ID不一致或更新失败时返回错误信息
     * */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Integer id,
                               @Validated(UpdateGroup.class) @RequestBody Device device) {
        deviceService.getDeviceOrThrow(id);
        if (device.getId() != null && !id.equals(device.getId())) {
            return Result.error(400, "请求ID不一致，无法更新");
        }
        device.setId(id);
        boolean updated = deviceService.updateById(device);
        if (!updated) {
            return Result.error("更新设备失败，数据可能已被他人修改");
        }
        return Result.success();
    }

    /*
     * 方法功能:查询所有在线设备列表
     * 参数:无
     * 返回数据:Result<List<Device>>-当前在线设备列表
     * */
    @GetMapping("/online")
    public Result<List<Device>> onlineList() {
        return Result.success(deviceService.findOnlineDevices());
    }

    /*
     * 方法功能:根据设备名称模糊搜索设备
     * 参数:name-搜索关键词
     * 返回数据:Result<List<Device>>-匹配名称的设备列表
     * */
    @GetMapping("/search")
    public Result<List<Device>> search(@RequestParam String name) {
        if (name == null || name.trim().isEmpty()) {
            return Result.error(400, "搜索关键词不能为空");
        }
        return Result.success(deviceService.findByNameLike(name));
    }

    /*
     * 方法功能:更新指定设备的状态
     * 参数:id-设备ID（路径参数）, status-状态值（0-离线,1-在线,2-故障）
     * 返回数据:Result<Void>-操作结果
     * */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Integer id,
            @RequestParam Integer status) {
        if (status < 0 || status > 2) {
            return Result.error(400, "状态值无效，允许值：0-离线，1-在线，2-故障");
        }
        boolean updated = deviceService.updateStatus(id, status);
        if (!updated) {
            return Result.error("更新状态失败");
        }
        return Result.success();
    }

    /*
     * 方法功能:分页查询设备列表，支持按状态和名称筛选
     * 参数:current-当前页码, size-每页大小, status-设备状态（可选）, name-设备名称（可选）
     * 返回数据:Result<Page<Device>>-分页查询结果
     * */
    @GetMapping("/page")
    public Result<Page<Device>> listByPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String name) {
        return Result.success(deviceService.pageByCondition(current, size, status, name));
    }

    /*
     * 方法功能:根据ID删除设备
     * 参数:id-设备ID（路径参数）
     * 返回数据:Result<Void>-操作结果
     * */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        deviceService.getDeviceOrThrow(id);
        deviceService.removeById(id);
        return Result.success();
    }

    /*
     * 方法功能:更新指定设备的告警规则配置
     * 参数:id-设备ID（路径参数）, alertRuleConfig-告警规则配置对象
     * 返回数据:Result<Void>-操作结果
     * */
    @PutMapping("/{id}/alert-config")
    public Result<Void> updateAlertConfig(
            @PathVariable Integer id,
            @RequestBody @Validated AlertRuleConfig alertRuleConfig) {
        deviceService.updateAlertConfig(id, alertRuleConfig);
        return Result.success();
    }
}
