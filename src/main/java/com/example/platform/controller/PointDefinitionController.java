package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.PointDefinitionCreateDTO;
import com.example.platform.dto.PointDefinitionUpdateDTO;
import com.example.platform.dto.PointResourceExportPackage;
import com.example.platform.entity.PointDefinition;
import com.example.platform.security.CurrentOrganizationContext;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.PointDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 点位定义接口层（双层：平台级全局 + 组织私有）
 *
 * <p>写操作 ORG_ADMIN/OPERATOR（创建归属当前组织上下文，组织用户必须携带
 * X-Organization-Id）；平台级资源管理走 /platform/point-definitions。
 * 查询任何已认证用户按数据范围（平台级+本组织）过滤；删除仅 ORG_ADMIN。</p>
 */
@Tag(name = "点位定义", description = "双层点位字典（组织级读写 + 平台级只读展示；平台级管理见 /platform/point-definitions）")
@RestController
@RequestMapping("/point-definitions")
@Validated
public class PointDefinitionController {

    private final PointDefinitionService pointDefinitionService;
    private final CurrentUserContext currentUserContext;
    private final CurrentOrganizationContext currentOrganizationContext;

    public PointDefinitionController(PointDefinitionService pointDefinitionService,
                                     CurrentUserContext currentUserContext,
                                     CurrentOrganizationContext currentOrganizationContext) {
        this.pointDefinitionService = pointDefinitionService;
        this.currentUserContext = currentUserContext;
        this.currentOrganizationContext = currentOrganizationContext;
    }

    @Operation(summary = "分页查询点位定义", description = "返回平台级+本组织两层（organizationId 区分），支持关键字与数据类型过滤")
    @GetMapping("/page")
    public Result<Page<PointDefinition>> page(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "关键字（可选）") @RequestParam(required = false) String keyword,
            @Parameter(description = "数据类型 NUMBER/BOOLEAN/STRING（可选）") @RequestParam(required = false) String dataType) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(pointDefinitionService.pagePointDefinitions(
                user.userId(), current, size, keyword, dataType));
    }

    @Operation(summary = "查询点位详情")
    @GetMapping("/{id}")
    public Result<PointDefinition> getById(
            @Parameter(description = "点位ID", required = true) @PathVariable Long id) {
        return Result.success(pointDefinitionService.getPointDefinitionOrThrow(id));
    }

    @Operation(summary = "查询可绑点位下拉", description = "平台级+当前组织的私有点位，供绑定/模板/规则选择")
    @GetMapping("/available")
    public Result<List<PointDefinition>> available() {
        Long orgId = currentOrganizationContext.currentOrganizationId().orElse(null);
        return Result.success(pointDefinitionService.listAvailablePoints(orgId));
    }

    @Operation(summary = "新增点位定义（组织私有）", description = "归属当前组织上下文，组织用户必须携带 X-Organization-Id；编码组织内唯一")
    @PostMapping
    @PreAuthorize("hasAnyRole('ORG_ADMIN','OPERATOR')")
    @AuditLog(operation = "CREATE_POINT_DEFINITION", detail = "编码:#createDTO.pointCode")
    public Result<PointDefinition> create(@Valid @RequestBody PointDefinitionCreateDTO createDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(pointDefinitionService.createPointDefinition(createDTO, user.userId()));
    }

    @Operation(summary = "更新点位定义", description = "平台级仅平台管理员；组织级本组织可改")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','OPERATOR')")
    @AuditLog(operation = "UPDATE_POINT_DEFINITION", detail = "点位ID:#id")
    public Result<PointDefinition> update(
            @Parameter(description = "点位ID", required = true) @PathVariable Long id,
            @Valid @RequestBody PointDefinitionUpdateDTO updateDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(pointDefinitionService.updatePointDefinition(id, updateDTO, user.userId()));
    }

    @Operation(summary = "删除点位定义", description = "被模板/绑定/规则引用时拒绝；仅 ORG_ADMIN")
    @DeleteMapping("/{id}")
    @AuditLog(operation = "DELETE_POINT_DEFINITION", detail = "点位ID:#id")
    public Result<Void> delete(
            @Parameter(description = "点位ID", required = true) @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        pointDefinitionService.deletePointDefinition(id, user.userId());
        return Result.success();
    }

    @Operation(summary = "导出本组织点位资源包", description = "导出当前组织的私有点位+模板为 JSON 传输包；仅 ORG_ADMIN")
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ORG_ADMIN')")
    public Result<PointResourceExportPackage> export() {
        Long orgId = currentOrganizationContext.requireOrganizationId();
        return Result.success(pointDefinitionService.exportOrgPackage(orgId));
    }

    @Operation(summary = "导入点位资源包", description = "导入为当前组织的私有副本（编码冲突复用已有，模板覆盖写）；仅 ORG_ADMIN")
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ORG_ADMIN')")
    @AuditLog(operation = "IMPORT_POINT_RESOURCES", detail = "点位数:#pkg.points.size")
    public Result<Void> importPackage(@Valid @RequestBody PointResourceExportPackage pkg) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        Long orgId = currentOrganizationContext.requireOrganizationId();
        pointDefinitionService.importPackage(pkg, orgId, user.userId());
        return Result.success();
    }
}
