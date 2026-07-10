package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.DataScopeAssignmentDTO;
import com.example.platform.entity.DataScopeAssignment;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.UserDataScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户数据范围分配接口层（管理 /users/{id}/data-scopes）
 *
 * <p>权限边界：端点位于 /users/**，SecurityConfig 已要求 PLATFORM_ADMIN/ORG_ADMIN；
 * 此处 {@code @PreAuthorize} 兜底，Service 层再做范围与角色校验。</p>
 */
@Tag(name = "用户数据范围", description = "管理用户组织/机房数据范围分配（PLATFORM_ADMIN/ORG_ADMIN）")
@RestController
@RequestMapping("/users")
@Validated
public class UserDataScopeController {

    private final UserDataScopeService userDataScopeService;
    private final CurrentUserContext currentUserContext;

    public UserDataScopeController(UserDataScopeService userDataScopeService,
                                   CurrentUserContext currentUserContext) {
        this.userDataScopeService = userDataScopeService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "查询用户数据范围分配", description = "ORG_ADMIN 仅可见自身组织内的分配")
    @GetMapping("/{id}/data-scopes")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORG_ADMIN')")
    public Result<List<DataScopeAssignment>> list(
            @Parameter(description = "目标用户ID", required = true) @PathVariable Long id) {
        CurrentUser current = currentUserContext.requireCurrentUser();
        return Result.success(userDataScopeService.listAssignments(id, current.userId()));
    }

    @Operation(summary = "为用户新增数据范围分配",
            description = "scopeType=PLATFORM 仅平台管理员可分配；若 organizationId/cloud_room_id 与 scopeType 不匹配将返回 400")
    @PostMapping("/{id}/data-scopes")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORG_ADMIN')")
    @AuditLog(operation = "ASSIGN_DATA_SCOPE", detail = "目标用户ID:#id, scopeType:#dto.scopeType")
    public Result<DataScopeAssignment> assign(
            @Parameter(description = "目标用户ID", required = true) @PathVariable Long id,
            @Valid @RequestBody DataScopeAssignmentDTO dto) {
        CurrentUser current = currentUserContext.requireCurrentUser();
        return Result.success(userDataScopeService.assignScope(id, dto, current.userId()));
    }

    @Operation(summary = "移除用户的一条数据范围分配",
            description = "ORG_ADMIN 仅可移除自身组织内的分配")
    @DeleteMapping("/{id}/data-scopes/{assignmentId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ORG_ADMIN')")
    @AuditLog(operation = "REMOVE_DATA_SCOPE", detail = "目标用户ID:#id, 分配ID:#assignmentId")
    public Result<Void> remove(
            @Parameter(description = "目标用户ID", required = true) @PathVariable Long id,
            @Parameter(description = "数据范围分配记录ID", required = true) @PathVariable Long assignmentId) {
        CurrentUser current = currentUserContext.requireCurrentUser();
        userDataScopeService.removeAssignment(id, assignmentId, current.userId());
        return Result.success();
    }
}