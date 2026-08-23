package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.DeviceTypePointTemplateBatchDTO;
import com.example.platform.entity.DeviceTypePointTemplate;
import com.example.platform.security.CurrentOrganizationContext;
import com.example.platform.service.DeviceTypePointTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备类型点位模板接口层（双层）
 *
 * <p>组织级模板（当前组织上下文）ORG_ADMIN/OPERATOR 可写；查询返回当前组织的
 * 生效模板（组织级优先降级平台级）。平台级默认模板管理见
 * /platform/point-templates/device-types/{typeId}。</p>
 */
@Tag(name = "设备类型点位模板", description = "组织级模板读写与生效模板解析（平台级模板见 /platform/**）")
@RestController
@RequestMapping("/point-templates/device-types/{typeId}")
@Validated
public class DeviceTypePointTemplateController {

    private final DeviceTypePointTemplateService deviceTypePointTemplateService;
    private final CurrentOrganizationContext currentOrganizationContext;

    public DeviceTypePointTemplateController(DeviceTypePointTemplateService deviceTypePointTemplateService,
                                             CurrentOrganizationContext currentOrganizationContext) {
        this.deviceTypePointTemplateService = deviceTypePointTemplateService;
        this.currentOrganizationContext = currentOrganizationContext;
    }

    @Operation(summary = "查询某设备类型的生效模板", description = "当前组织组织级模板优先，无则降级平台级；按 sortOrder 升序")
    @GetMapping
    public Result<List<DeviceTypePointTemplate>> list(
            @Parameter(description = "设备类型ID", required = true) @PathVariable Integer typeId) {
        return Result.success(deviceTypePointTemplateService.listTemplates(typeId));
    }

    @Operation(summary = "查询生效模板（显式组织参数）", description = "指定组织解析生效模板（与 GET / 相同逻辑，便于跨组织预览）")
    @GetMapping("/effective")
    public Result<List<DeviceTypePointTemplate>> effective(
            @Parameter(description = "设备类型ID", required = true) @PathVariable Integer typeId,
            @Parameter(description = "组织ID（可选，默认当前组织）") @RequestParam(required = false) Long organizationId) {
        Long orgId = organizationId != null ? organizationId
                : currentOrganizationContext.currentOrganizationId().orElse(null);
        return Result.success(deviceTypePointTemplateService.listEffectiveTemplates(typeId, orgId));
    }

    @Operation(summary = "批量覆盖设置组织级模板", description = "目标层=当前组织上下文；先清空同层旧模板再整批写入")
    @PutMapping
    @PreAuthorize("hasAnyRole('ORG_ADMIN','OPERATOR')")
    @AuditLog(operation = "SAVE_TYPE_POINT_TEMPLATES", detail = "设备类型ID:#typeId")
    public Result<List<DeviceTypePointTemplate>> save(
            @Parameter(description = "设备类型ID", required = true) @PathVariable Integer typeId,
            @Valid @RequestBody DeviceTypePointTemplateBatchDTO batchDTO) {
        return Result.success(deviceTypePointTemplateService.saveTemplates(typeId, batchDTO));
    }
}
