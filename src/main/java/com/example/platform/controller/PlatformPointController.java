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
 * 平台级点位资源管理接口层（ADMIN 专属，挂 /platform/** 前缀）
 *
 * <p>管理全局点位与默认模板：导入包可导入为全局资源（点位+模板）。</p>
 */
@Tag(name = "平台级点位资源", description = "全局点位字典与默认模板管理（ADMIN 专属）")
@RestController
@RequestMapping("/platform")
@Validated
public class PlatformPointController {

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

    @Operation(summary = "新增平台级点位定义", description = "创建全局共享点位")
    @PostMapping("/point-definitions")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "CREATE_PLATFORM_POINT", detail = "编码:#createDTO.pointCode")
    public Result<PointDefinition> createPoint(@Valid @RequestBody PointDefinitionCreateDTO createDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(pointDefinitionService.createPlatformPointDefinition(createDTO, user.userId()));
    }

    @Operation(summary = "更新平台级点位定义", description = "仅 ADMIN")
    @PutMapping("/point-definitions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "UPDATE_PLATFORM_POINT", detail = "点位ID:#id")
    public Result<PointDefinition> updatePoint(
            @Parameter(description = "点位ID", required = true) @PathVariable Long id,
            @Valid @RequestBody PointDefinitionUpdateDTO updateDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(pointDefinitionService.updatePointDefinition(id, updateDTO, user.userId()));
    }

    @Operation(summary = "删除平台级点位定义", description = "被引用时拒绝（4035）")
    @DeleteMapping("/point-definitions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "DELETE_PLATFORM_POINT", detail = "点位ID:#id")
    public Result<Void> deletePoint(
            @Parameter(description = "点位ID", required = true) @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        pointDefinitionService.deletePointDefinition(id, user.userId());
        return Result.success();
    }

    @Operation(summary = "查询平台级默认模板", description = "返回该类型的模板项")
    @GetMapping("/point-templates/device-types/{typeId}")
    public Result<List<DeviceTypePointTemplate>> listPlatformTemplates(
            @Parameter(description = "设备类型ID", required = true) @PathVariable Integer typeId) {
        return Result.success(templateService.listTemplates(typeId));
    }

    @Operation(summary = "批量覆盖设置平台级默认模板", description = "先清空该类型旧模板再整批写入")
    @PutMapping("/point-templates/device-types/{typeId}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "SAVE_PLATFORM_TYPE_TEMPLATES", detail = "设备类型ID:#typeId")
    public Result<List<DeviceTypePointTemplate>> savePlatformTemplates(
            @Parameter(description = "设备类型ID", required = true) @PathVariable Integer typeId,
            @Valid @RequestBody DeviceTypePointTemplateBatchDTO batchDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(templateService.saveTemplates(typeId, batchDTO, user.userId()));
    }

    @Operation(summary = "导入资源包", description = "导入点位资源包为全局资源")
    @PostMapping("/point-definitions/import")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "IMPORT_PLATFORM_POINT_RESOURCES", detail = "点位数:#pkg.points.size")
    public Result<Void> importAsPlatform(@Valid @RequestBody PointResourceExportPackage pkg) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        pointDefinitionService.importPackage(pkg, user.userId());
        return Result.success();
    }
}
