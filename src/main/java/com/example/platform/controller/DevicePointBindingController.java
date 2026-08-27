package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.DevicePointBindingDTO;
import com.example.platform.entity.DevicePointBinding;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.DevicePointBindingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备点位绑定接口层（每台具体设备的点位启用集合）
 *
 * <p>权限边界：
 * <ul>
 *   <li>ADMIN 可写（按 Service 层 ensureDeviceAccess 校验范围）</li>
 *   <li>已认证用户可读（按授权设备范围过滤）</li>
 *   <li>模板初始化端点用于设备创建后批量拷贝默认点位</li>
 * </ul>
 */
@Tag(name = "设备点位绑定", description = "设备实际点位启用集合管理（ADMIN 写，已认证按范围读）")
@RestController
@RequestMapping("/device-points")
@Validated
public class DevicePointBindingController {

    private final DevicePointBindingService devicePointBindingService;
    private final CurrentUserContext currentUserContext;

    public DevicePointBindingController(DevicePointBindingService devicePointBindingService,
                                        CurrentUserContext currentUserContext) {
        this.devicePointBindingService = devicePointBindingService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "查询某设备的全部点位绑定", description = "按授权设备范围过滤")
    @GetMapping("/device/{deviceId}")
    public Result<List<DevicePointBinding>> listByDevice(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer deviceId) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(devicePointBindingService.listByDevice(deviceId, user.userId()));
    }

    @Operation(summary = "按条件分页查询点位绑定", description = "按授权范围收窄；必须指定 deviceId，避免跨设备扫描")
    @GetMapping("/page")
    public Result<Page<DevicePointBinding>> page(
            @Parameter(description = "当前页码", example = "1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "设备ID（可选）") @RequestParam(required = false) Integer deviceId,
            @Parameter(description = "点位ID（可选）") @RequestParam(required = false) Long pointId,
            @Parameter(description = "状态：0-启用 1-停用（可选）") @RequestParam(required = false) Integer status) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(devicePointBindingService.pageByConditions(
                user.userId(), current, size, deviceId, pointId, status));
    }

    @Operation(summary = "为设备绑定点位", description = "ADMIN 专用；服务层校验设备归属")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "BIND_DEVICE_POINT", detail = "设备ID:#dto.deviceId, 点位ID:#dto.pointId")
    public Result<DevicePointBinding> bind(@Valid @RequestBody DevicePointBindingDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(devicePointBindingService.bindPoint(dto, user.userId()));
    }

    @Operation(summary = "更新绑定状态", description = "ADMIN 专用；status：0-启用 1-停用")
    @PutMapping("/{bindingId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "UPDATE_BINDING_STATUS", detail = "绑定ID:#bindingId, 状态:#status")
    public Result<DevicePointBinding> updateStatus(
            @Parameter(description = "绑定ID", required = true) @PathVariable Long bindingId,
            @Parameter(description = "状态：0-启用 1-停用", required = true) @RequestParam Integer status) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(devicePointBindingService.updateStatus(bindingId, status, user.userId()));
    }

    @Operation(summary = "移除设备点位绑定", description = "ADMIN 专用")
    @DeleteMapping("/{bindingId}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "UNBIND_DEVICE_POINT", detail = "绑定ID:#bindingId")
    public Result<Void> unbind(
            @Parameter(description = "绑定ID", required = true) @PathVariable Long bindingId) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        devicePointBindingService.unbindPoint(bindingId, user.userId());
        return Result.success();
    }

    @Operation(summary = "按类型模板初始化设备点位", description = "ADMIN 专用；又如默认点位尚未绑定，则按 type_id 模板批量插入新绑定")
    @PostMapping("/device/{deviceId}/init-from-template/{typeId}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "INIT_DEVICE_POINTS_FROM_TEMPLATE", detail = "设备ID:#deviceId, 类型ID:#typeId")
    public Result<List<DevicePointBinding>> initFromTemplate(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer deviceId,
            @Parameter(description = "设备类型ID", required = true) @PathVariable Integer typeId) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(
                devicePointBindingService.initBindingsFromTemplate(deviceId, typeId, user.userId()));
    }
}
