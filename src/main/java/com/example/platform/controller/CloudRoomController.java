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
 * 云机房管理接口层（机房生命周期管理）
 *
 * <p>权限边界：
 * <ul>
 *   <li>写操作（创建/更新/停用）：仅 ADMIN（@PreAuthorize 兜底）</li>
 *   <li>读操作：任何已认证用户可调用</li>
 * </ul>
 * 机房删除须机房下无设备且无绑定网关凭证，由 Service 校验兜底。</p>
 */
@Tag(name = "云机房管理", description = "机房生命周期管理（ADMIN 写，已认证用户读）")
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

    @Operation(summary = "分页查询机房", description = "支持按名称关键字与状态筛选，按创建时间倒序")
    @GetMapping("/page")
    public Result<Page<CloudRoom>> page(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小，默认10，上限100") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "机房名称关键字（可选）") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态筛选：0-正常 1-停用（可选）") @RequestParam(required = false) Integer status) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(cloudRoomService.pageCloudRooms(
                user.userId(), current, size, keyword, status));
    }

    @Operation(summary = "查询机房详情", description = "机房不存在时返回 404")
    @GetMapping("/{id}")
    public Result<CloudRoom> getById(
            @Parameter(description = "机房ID", required = true) @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(cloudRoomService.getCloudRoomOrThrow(id, user.userId()));
    }

    @Operation(summary = "创建机房", description = "ADMIN 专用；roomCode 全局唯一")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "CREATE_CLOUD_ROOM", detail = "机房编码:#createDTO.roomCode")
    public Result<CloudRoom> create(@Valid @RequestBody CloudRoomCreateDTO createDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(cloudRoomService.createCloudRoom(createDTO, user.userId()));
    }

    @Operation(summary = "更新机房信息", description = "ADMIN 专用；不允许修改 roomCode")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "UPDATE_CLOUD_ROOM", detail = "机房ID:#id")
    public Result<CloudRoom> update(
            @Parameter(description = "机房ID", required = true) @PathVariable Long id,
            @Valid @RequestBody CloudRoomUpdateDTO updateDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(cloudRoomService.updateCloudRoom(id, updateDTO, user.userId()));
    }

    @Operation(summary = "删除机房", description = "ADMIN 专用；机房下存在设备或网关凭证时拒绝删除")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "DELETE_CLOUD_ROOM", detail = "机房ID:#id")
    public Result<Void> delete(
            @Parameter(description = "机房ID", required = true) @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        cloudRoomService.deleteCloudRoom(id, user.userId());
        return Result.success();
    }

    @Operation(summary = "启用/停用机房", description = "ADMIN 专用，软停用；status：0-正常 1-停用")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "UPDATE_CLOUD_ROOM_STATUS", detail = "机房ID:#id, 状态:#status")
    public Result<CloudRoom> updateStatus(
            @Parameter(description = "机房ID", required = true) @PathVariable Long id,
            @Parameter(description = "状态：0-正常，1-停用", required = true) @RequestParam Integer status) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(cloudRoomService.updateStatus(id, status, user.userId()));
    }
}