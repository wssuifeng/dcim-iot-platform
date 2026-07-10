package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.DeviceTypePointTemplateBatchDTO;
import com.example.platform.entity.DeviceTypePointTemplate;
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
 * 设备类型点位模板接口层
 *
 * <p>均以 typeId 为路径参数：查询某设备类型的默认点位；批量覆盖写模板。
 * 写操作仅 PLATFORM_ADMIN；读操作任何已认证用户（设备类型元数据共享）。</p>
 */
@Tag(name = "设备类型点位模板", description = "管理设备类型的默认点位集合（PLATFORM_ADMIN 写，已认证用户只读）")
@RestController
@RequestMapping("/point-templates/device-types/{typeId}")
@Validated
public class DeviceTypePointTemplateController {

    private final DeviceTypePointTemplateService deviceTypePointTemplateService;

    public DeviceTypePointTemplateController(DeviceTypePointTemplateService deviceTypePointTemplateService) {
        this.deviceTypePointTemplateService = deviceTypePointTemplateService;
    }

    @Operation(summary = "查询某设备类型的点位模板", description = "按 sortOrder 升序")
    @GetMapping
    public Result<List<DeviceTypePointTemplate>> list(
            @Parameter(description = "设备类型ID", required = true) @PathVariable Integer typeId) {
        return Result.success(deviceTypePointTemplateService.listTemplates(typeId));
    }

    @Operation(summary = "批量覆盖设置模板", description = "先清空旧模板，再按 items 整批写入；PLATFORM_ADMIN 专用")
    @PutMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @AuditLog(operation = "SAVE_TYPE_POINT_TEMPLATES", detail = "设备类型ID:#typeId")
    public Result<List<DeviceTypePointTemplate>> save(
            @Parameter(description = "设备类型ID", required = true) @PathVariable Integer typeId,
            @Valid @RequestBody DeviceTypePointTemplateBatchDTO batchDTO) {
        return Result.success(deviceTypePointTemplateService.saveTemplates(typeId, batchDTO));
    }

}
