package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.ResultCode;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.common.group.CreateGroup;
import com.example.platform.common.group.UpdateGroup;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.entity.Device;
import com.example.platform.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备管理接口层，提供设备的CRUD、状态更新、搜索分页及告警配置等RESTful接口
 */
@Tag(name = "设备管理", description = "设备 CRUD、状态更新、搜索分页及告警配置")
@RestController
@RequestMapping("/devices")
@Validated
public class DeviceController {
    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Operation(summary = "查询所有设备列表", description = "返回全部设备信息，不分页")
    @GetMapping
    public Result<List<Device>> list() {
        return Result.success(deviceService.list());
    }

    @Operation(summary = "根据ID查询设备详情", description = "设备不存在时抛出 DeviceNotFoundException，由全局异常处理器返回 404")
    @GetMapping("/{id}")
    public Result<Device> getById(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer id) {
        return Result.success(deviceService.getDeviceOrThrow(id));
    }

    @Operation(summary = "新增设备", description = "使用 CreateGroup 分组校验；Service 层内部校验设备编码唯一性与设备类型存在性")
    @PostMapping
    @AuditLog(operation = "CREATE_DEVICE", detail = "设备名称:#device.deviceName, 设备编码:#device.deviceCode")
    public Result<Void> save(@Validated(CreateGroup.class) @RequestBody Device device) {
        deviceService.save(device);
        return Result.success();
    }

    @Operation(summary = "更新设备信息", description = "以路径ID为准，防御性校验请求体ID一致性；乐观锁冲突时提示数据可能已被他人修改")
    @PutMapping("/{id}")
    @AuditLog(operation = "UPDATE_DEVICE", detail = "设备ID:#id, 设备名称:#device.deviceName")
    public Result<Void> update(@Parameter(description = "设备ID", required = true) @PathVariable Integer id,
                               @Validated(UpdateGroup.class) @RequestBody Device device) {
        deviceService.getDeviceOrThrow(id);
        if (device.getId() != null && !id.equals(device.getId())) {
            return Result.error(ResultCode.INVALID_PARAMETER, "请求ID不一致，无法更新");
        }
        device.setId(id);
        boolean updated = deviceService.updateById(device);
        if (!updated) {
            return Result.error(ResultCode.DEVICE_NOT_FOUND, "更新设备失败，数据可能已被他人修改");
        }
        return Result.success();
    }

    @Operation(summary = "查询在线设备列表", description = "返回当前 status=1（在线）的全部设备")
    @GetMapping("/online")
    public Result<List<Device>> onlineList() {
        return Result.success(deviceService.findOnlineDevices());
    }

    @Operation(summary = "模糊搜索设备", description = "按设备名称模糊匹配；关键词为空时返回 400")
    @GetMapping("/search")
    public Result<List<Device>> search(
            @Parameter(description = "搜索关键词", required = true) @RequestParam String name) {
        if (name == null || name.trim().isEmpty()) {
            return Result.error(ResultCode.INVALID_PARAMETER, "搜索关键词不能为空");
        }
        return Result.success(deviceService.findByNameLike(name));
    }

    @Operation(summary = "更新设备状态", description = "status 取值：0-离线，1-在线，2-故障；超出范围返回 400")
    @PutMapping("/{id}/status")
    @AuditLog(operation = "UPDATE_DEVICE_STATUS", detail = "设备ID:#id, 状态:#status")
    public Result<Void> updateStatus(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer id,
            @Parameter(description = "状态值：0-离线，1-在线，2-故障", required = true) @RequestParam Integer status) {
        if (status < 0 || status > 2) {
            return Result.error(ResultCode.INVALID_PARAMETER, "状态值无效，允许值：0-离线，1-在线，2-故障");
        }
        boolean updated = deviceService.updateStatus(id, status);
        if (!updated) {
            return Result.error(ResultCode.DEVICE_NOT_FOUND, "更新状态失败，设备不存在");
        }
        return Result.success();
    }

    @Operation(summary = "分页查询设备", description = "支持按状态、名称筛选，结果按 updateTime 倒序")
    @GetMapping("/page")
    public Result<Page<Device>> listByPage(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小，默认10，上限100") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "设备状态筛选（可选）") @RequestParam(required = false) Integer status,
            @Parameter(description = "设备名称模糊筛选（可选）") @RequestParam(required = false) String name) {
        int[] pageParams = PaginationUtils.validate(current, size);
        return Result.success(deviceService.pageByCondition(pageParams[0], pageParams[1], status, name));
    }

    @Operation(summary = "删除设备", description = "设备不存在时抛出 DeviceNotFoundException")
    @DeleteMapping("/{id}")
    @AuditLog(operation = "DELETE_DEVICE", detail = "设备ID:#id")
    public Result<Void> delete(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer id) {
        deviceService.getDeviceOrThrow(id);
        deviceService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "更新设备级告警规则配置", description = "配置通过 JacksonTypeHandler 以 JSON 存入 alert_rule_config 字段；设备级优先于类型级")
    @PutMapping("/{id}/alert-config")
    @AuditLog(operation = "UPDATE_DEVICE_ALERT_CONFIG", detail = "设备ID:#id")
    public Result<Void> updateAlertConfig(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer id,
            @RequestBody @Validated AlertRuleConfig alertRuleConfig) {
        deviceService.updateAlertConfig(id, alertRuleConfig);
        return Result.success();
    }
}
