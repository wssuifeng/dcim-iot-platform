package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.DeviceTypePointTemplateBatchDTO;
import com.example.platform.dto.PointDefinitionCreateDTO;
import com.example.platform.dto.PointDefinitionUpdateDTO;
import com.example.platform.dto.PointResourceExportPackage;
import com.example.platform.entity.DeviceTypePointTemplate;
import com.example.platform.entity.PointDefinition;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.DeviceTypePointTemplateService;
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
 * 平台级点位资源管理接口层（PLATFORM_ADMIN 专属，挂 /platform/** 前缀，D26/D27）
 *
 * <p>管理 organization_id=0 的平台级全局点位与默认模板：
 * 所有组织可见可用；组织级资源不在此管理（组织角色走 /point-definitions）。
 * 导入包可将组织导出的资源提升为平台级（点位+模板）。</p>
 */
@Tag(name = "平台级点位资源", description = "平台级全局点位字典与默认模板管理（PLATFORM_ADMIN 专属）")
@RestController
@RequestMapping("/platform")
@Validated
public class PlatformPointController {

    private static final long PLATFORM_ORG = 0L;

    private final PointDefinitionService pointDefinitionService;
    private final DeviceTypePointTemplateService templateService;
    private final CurrentUserContext currentUserContext;

    public PlatformPointController(PointDefinitionService pointDefinitionService,
                                   DeviceTypePointTemplateService templateService,
                                   CurrentUserContext currentUserContext) {
        this.pointDefinitionService = pointDefinitionService;
        this.templateService = templateService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "新增平台级点位定义", description = "创建 organization_id=0 的全局共享点位")
    @PostMapping("/point-definitions")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @AuditLog(operation = "CREATE_PLATFORM_POINT", detail = "编码:#createDTO.pointCode")
    public Result<PointDefinition> createPoint(@Valid @RequestBody PointDefinitionCreateDTO createDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(pointDefinitionService.createPlatformPointDefinition(createDTO, user.userId()));
    }

    @Operation(summary = "更新平台级点位定义", description = "仅平台管理员（组织级资源拒绝，D26）")
    @PutMapping("/point-definitions/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @AuditLog(operation = "UPDATE_PLATFORM_POINT", detail = "点位ID:#id")
    public Result<PointDefinition> updatePoint(
            @Parameter(description = "点位ID", required = true) @PathVariable Long id,
            @Valid @RequestBody PointDefinitionUpdateDTO updateDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(pointDefinitionService.updatePointDefinition(id, updateDTO, user.userId()));
    }

    @Operation(summary = "删除平台级点位定义", description = "被引用时拒绝（4035）")
    @DeleteMapping("/point-definitions/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @AuditLog(operation = "DELETE_PLATFORM_POINT", detail = "点位ID:#id")
    public Result<Void> deletePoint(
            @Parameter(description = "点位ID", required = true) @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        pointDefinitionService.deletePointDefinition(id, user.userId());
        return Result.success();
    }

    @Operation(summary = "查询平台级默认模板", description = "返回 organization_id=0 的模板项")
    @GetMapping("/point-templates/device-types/{typeId}")
    public Result<List<DeviceTypePointTemplate>> listPlatformTemplates(
            @Parameter(description = "设备类型ID", required = true) @PathVariable Integer typeId) {
        return Result.success(templateService.listEffectiveTemplates(typeId, null));
    }

    @Operation(summary = "批量覆盖设置平台级默认模板", description = "目标层=平台级（organization_id=0），所有组织降级可用")
    @PutMapping("/point-templates/device-types/{typeId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @AuditLog(operation = "SAVE_PLATFORM_TYPE_TEMPLATES", detail = "设备类型ID:#typeId")
    public Result<List<DeviceTypePointTemplate>> savePlatformTemplates(
            @Parameter(description = "设备类型ID", required = true) @PathVariable Integer typeId,
            @Valid @RequestBody DeviceTypePointTemplateBatchDTO batchDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(templateService.saveTemplatesForOrganization(typeId, batchDTO, PLATFORM_ORG,
                user.userId()));
    }

    @Operation(summary = "导入资源包并提升为平台级", description = "将组织导出包（或自建包）导入为平台级资源，对所有组织可见")
    @PostMapping("/point-definitions/import")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @AuditLog(operation = "IMPORT_PLATFORM_POINT_RESOURCES", detail = "点位数:#pkg.points.size")
    public Result<Void> importAsPlatform(@Valid @RequestBody PointResourceExportPackage pkg) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        pointDefinitionService.importPackage(pkg, PLATFORM_ORG, user.userId());
        return Result.success();
    }
}
