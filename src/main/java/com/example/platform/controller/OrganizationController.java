package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.OrganizationCreateDTO;
import com.example.platform.dto.OrganizationUpdateDTO;
import com.example.platform.entity.Organization;
import com.example.platform.dto.PublicOrganizationVO;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 组织管理接口层（第四阶段产品化组织生命周期管理）
 *
 * <p>权限边界：
 * <ul>
 *   <li>写操作（创建/更新/停用）：仅 PLATFORM_ADMIN，方法级 {@code @PreAuthorize} 兜底</li>
 *   <li>读操作（分页/详情）：任何已认证用户可调用，由 Service 按当前用户数据范围过滤</li>
 * </ul>
 * 注意：HTTP DELETE /organizations 暂未提供，受 SecurityConfig 全局 DELETE 规则限制，
 * 平台停用组织请使用 PUT /organizations/{id}/status。</p>
 */
@Tag(name = "组织管理", description = "组织生命周期管理（PLATFORM_ADMIN 写，已认证用户按范围读）")
@RestController
@RequestMapping("/organizations")
@Validated
public class OrganizationController {

    private final OrganizationService organizationService;
    private final CurrentUserContext currentUserContext;

    public OrganizationController(OrganizationService organizationService,
                                  CurrentUserContext currentUserContext) {
        this.organizationService = organizationService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "分页查询组织", description = "PLATFORM_ADMIN 可见全部；其他角色仅可见被授权的组织")
    @GetMapping("/page")
    public Result<Page<Organization>> page(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "组织名称关键字（可选）") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态筛选：0-正常 1-停用（可选）") @RequestParam(required = false) Integer status) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(organizationService.pageOrganizations(
                user.userId(), current, size, keyword, status));
    }

    @GetMapping("/public")
    public Result<List<PublicOrganizationVO>> publicOrganizations(
            @RequestParam(required = false) String keyword) {
        return Result.success(organizationService.listPublicOrganizations(keyword));
    }

    @Operation(summary = "查询组织详情", description = "未授权组织返回 403 DataScopeViolation")
    @GetMapping("/{id}")
    public Result<Organization> getById(
            @Parameter(description = "组织ID", required = true) @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(organizationService.getOrganizationOrThrow(id, user.userId()));
    }

    @Operation(summary = "创建组织", description = "PLATFORM_ADMIN 专用；orgCode 全局唯一")
    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @AuditLog(operation = "CREATE_ORGANIZATION", detail = "组织编码:#createDTO.orgCode")
    public Result<Organization> create(@Valid @RequestBody OrganizationCreateDTO createDTO) {
        return Result.success(organizationService.createOrganization(createDTO));
    }

    @Operation(summary = "更新组织信息", description = "PLATFORM_ADMIN 专用；不允许修改 orgCode")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @AuditLog(operation = "UPDATE_ORGANIZATION", detail = "组织ID:#id")
    public Result<Organization> update(
            @Parameter(description = "组织ID", required = true) @PathVariable Long id,
            @Valid @RequestBody OrganizationUpdateDTO updateDTO) {
        return Result.success(organizationService.updateOrganization(id, updateDTO));
    }

    @Operation(summary = "启用/停用组织", description = "PLATFORM_ADMIN 专用，软停用；status：0-正常 1-停用")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @AuditLog(operation = "UPDATE_ORGANIZATION_STATUS", detail = "组织ID:#id, 状态:#status")
    public Result<Organization> updateStatus(
            @Parameter(description = "组织ID", required = true) @PathVariable Long id,
            @Parameter(description = "状态：0-正常，1-停用", required = true) @RequestParam Integer status) {
        return Result.success(organizationService.updateStatus(id, status));
    }
}
