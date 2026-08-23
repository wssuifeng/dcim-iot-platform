package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.entity.DeviceType;
import com.example.platform.service.DeviceTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备类型接口层，提供设备类型的完整CRUD等RESTful接口
 */
@Tag(name = "设备类型", description = "设备类型 CRUD")
@RestController
@RequestMapping("/device-types")
@Validated
public class DeviceTypeController {

    private final DeviceTypeService deviceTypeService;

    public DeviceTypeController(DeviceTypeService deviceTypeService) {
        this.deviceTypeService = deviceTypeService;
    }

    @Operation(summary = "查询所有设备类型", description = "返回全部设备类型列表，按更新时间倒序")
    @GetMapping
    public Result<List<DeviceType>> list() {
        return Result.success(deviceTypeService.listAll());
    }

    @Operation(summary = "根据ID查询设备类型", description = "设备类型不存在时抛出 DeviceTypeNotFound")
    @GetMapping("/{id}")
    public Result<DeviceType> getById(
            @Parameter(description = "设备类型ID", required = true) @PathVariable Integer id) {
        return Result.success(deviceTypeService.getTypeOrThrow(id));
    }

    @Operation(summary = "新增设备类型", description = "内部校验 type_code 全局唯一性；编码仅允许字母数字下划线")
    @PostMapping
    @AuditLog(operation = "CREATE_DEVICE_TYPE", detail = "类型名称:#deviceType.typeName, 类型编码:#deviceType.typeCode")
    public Result<DeviceType> create(@Validated @RequestBody DeviceType deviceType) {
        return Result.success(deviceTypeService.create(deviceType));
    }

    @Operation(summary = "更新设备类型", description = "支持部分字段更新；设备类型不存在时抛出 DeviceTypeNotFound")
    @PutMapping("/{id}")
    @AuditLog(operation = "UPDATE_DEVICE_TYPE", detail = "类型ID:#id, 类型名称:#deviceType.typeName")
    public Result<DeviceType> update(
            @Parameter(description = "设备类型ID", required = true) @PathVariable Integer id,
            @RequestBody DeviceType deviceType) {
        return Result.success(deviceTypeService.updateById(id, deviceType));
    }

    @Operation(summary = "删除设备类型", description = "若该类型下存在关联设备则返回 400 拒绝删除")
    @DeleteMapping("/{id}")
    @AuditLog(operation = "DELETE_DEVICE_TYPE", detail = "类型ID:#id")
    public Result<Void> delete(
            @Parameter(description = "设备类型ID", required = true) @PathVariable Integer id) {
        deviceTypeService.deleteById(id);
        return Result.success();
    }
}
