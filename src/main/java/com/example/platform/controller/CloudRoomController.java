package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.CloudRoomCreateDTO;
import com.example.platform.dto.CloudRoomUpdateDTO;
import com.example.platform.entity.CloudRoom;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.CloudRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 云机房管理接口层（第四阶段产品化机房生命周期管理）
 *
 * <p>权限边界：
 * <ul>
 *   <li>写操作（创建/更新/停用）：仅 ORG_ADMIN（@PreAuthorize 兜底 + Service 层范围校验）</li>
 *   <li>读操作：任何已认证用户可调用，由 Service 按当前用户数据范围过滤</li>
 * </ul>
 * 按 HTTP DELETE 删除机房的精细化权限（ORG_ADMIN 跨组织越权拦截）由 Service 兜底，
 * 下方 {@code delete} 端点不开放机房的物理删除，仅提供停用接口避免数据回溯丢失。</p>
 */
@Tag(name = "云机房管理", description = "机房生命周期管理（ORG_ADMIN 写，已认证用户按范围读）")
@RestController
@RequestMapping("/cloud-rooms")
@Validated
public class CloudRoomController {

    private final CloudRoomService cloudRoomService;
    private final CurrentUserContext currentUserContext;

    public CloudRoomController(CloudRoomService cloudRoomService,
                               CurrentUserContext currentUserContext) {
        this.cloudRoomService = cloudRoomService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "分页查询机房", description = "ORG_ADMIN 可见本组织机房；OPERATOR/VIEWER 仅可见授权机房；PLATFORM_ADMIN 默认不穿透业务，返回空")
    @GetMapping("/page")
    public Result<Page<CloudRoom>> page(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "组织ID筛选（可选，必须在当前用户范围内）") @RequestParam(required = false) Long organizationId,
            @Parameter(description = "机房名称关键字（可选）") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态筛选：0-正常 1-停用（可选）") @RequestParam(required = false) Integer status) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(cloudRoomService.pageCloudRooms(
                user.userId(), current, size, organizationId, keyword, status));
    }

    @Operation(summary = "查询机房详情", description = "未授权机房返回 403 DataScopeViolation")
    @GetMapping("/{id}")
    public Result<CloudRoom> getById(
            @Parameter(description = "机房ID", required = true) @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(cloudRoomService.getCloudRoomOrThrow(id, user.userId()));
    }

    @Operation(summary = "创建机房", description = "ORG_ADMIN 专用；roomCode 组织内唯一")
    @PostMapping
    @PreAuthorize("hasRole('ORG_ADMIN')")
    @AuditLog(operation = "CREATE_CLOUD_ROOM", detail = "组织ID:#createDTO.organizationId, 机房编码:#createDTO.roomCode")
    public Result<CloudRoom> create(@Valid @RequestBody CloudRoomCreateDTO createDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(cloudRoomService.createCloudRoom(createDTO, user.userId()));
    }

    @Operation(summary = "更新机房信息", description = "ORG_ADMIN 专用；不允许修改 organizationId 与 roomCode")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    @AuditLog(operation = "UPDATE_CLOUD_ROOM", detail = "机房ID:#id")
    public Result<CloudRoom> update(
            @Parameter(description = "机房ID", required = true) @PathVariable Long id,
            @Valid @RequestBody CloudRoomUpdateDTO updateDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(cloudRoomService.updateCloudRoom(id, updateDTO, user.userId()));
    }

    @Operation(summary = "启用/停用机房", description = "ORG_ADMIN 专用，软停用；status：0-正常 1-停用")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    @AuditLog(operation = "UPDATE_CLOUD_ROOM_STATUS", detail = "机房ID:#id, 状态:#status")
    public Result<CloudRoom> updateStatus(
            @Parameter(description = "机房ID", required = true) @PathVariable Long id,
            @Parameter(description = "状态：0-正常，1-停用", required = true) @RequestParam Integer status) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(cloudRoomService.updateStatus(id, status, user.userId()));
    }
}