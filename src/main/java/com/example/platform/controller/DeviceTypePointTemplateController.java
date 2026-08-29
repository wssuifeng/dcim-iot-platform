package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.DeviceTypePointTemplateBatchDTO;
import com.example.platform.entity.DeviceTypePointTemplate;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
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
 * 设备类型指标模板接口层
 *
 * <p>模板按设备类型整批覆盖保存；查询返回生效模板，按 sortOrder 升序。</p>
 */
@Tag(name = "设备类型指标模板", description = "类型指标模板读写与生效模板解析")
@RestController
@RequestMapping("/point-templates/device-types/{typeId}")
@Validated
public class DeviceTypePointTemplateController {

    private final DeviceTypePointTemplateService deviceTypePointTemplateService;
    private final CurrentUserContext currentUserContext;

    public DeviceTypePointTemplateController(DeviceTypePointTemplateService deviceTypePointTemplateService,
                                             CurrentUserContext currentUserContext) {
        this.deviceTypePointTemplateService = deviceTypePointTemplateService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "查询某设备类型的生效模板", description = "按 sortOrder 升序")
    @GetMapping
    public Result<List<DeviceTypePointTemplate>> list(
            @Parameter(description = "设备类型ID", required = true) @PathVariable Integer typeId) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(deviceTypePointTemplateService.listTemplates(typeId, user.userId()));
    }

    @Operation(summary = "批量覆盖设置模板", description = "先清空该类型旧模板再整批写入")
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "SAVE_TYPE_POINT_TEMPLATES", detail = "设备类型ID:#typeId")
    public Result<List<DeviceTypePointTemplate>> save(
            @Parameter(description = "设备类型ID", required = true) @PathVariable Integer typeId,
            @Valid @RequestBody DeviceTypePointTemplateBatchDTO batchDTO) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(deviceTypePointTemplateService.saveTemplates(typeId, batchDTO, user.userId()));
    }
}
