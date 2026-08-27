package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.PointAlarmRuleCreateDTO;
import com.example.platform.dto.PointAlarmRuleUpdateDTO;
import com.example.platform.dto.PointAlarmRuleVO;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.DevicePointAlarmRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 点位告警规则接口层（规则 CRUD 与设备生效规则降级解析）
 *
 * <p>权限边界：写操作 ADMIN/OPERATOR（删除仅 ADMIN，由 SecurityConfig DELETE 规则约束）；
 * 查询任何已认证用户按数据范围过滤；平台级模板规则仅 ADMIN 可挂载（D26 由服务层校验）。</p>
 */
@Tag(name = "点位告警规则", description = "点位告警规则 CRUD 与三级降级解析（设备级覆盖→组织模板→平台模板）")
@RestController
@RequestMapping("/point-alarm-rules")
@Validated
public class PointAlarmRuleController {

    private final DevicePointAlarmRuleService ruleService;
    private final CurrentUserContext currentUserContext;

    public PointAlarmRuleController(DevicePointAlarmRuleService ruleService,
                                    CurrentUserContext currentUserContext) {
        this.ruleService = ruleService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "分页查询告警规则", description = "支持类型/设备/点位/规则类型/启用状态筛选，按数据范围过滤")
    @GetMapping("/page")
    public Result<Page<PointAlarmRuleVO>> page(
            @Parameter(description = "当前页码", example = "1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "设备类型ID") @RequestParam(required = false) Integer typeId,
            @Parameter(description = "设备ID（含该设备的覆盖规则与模板级规则）") @RequestParam(required = false) Integer deviceId,
            @Parameter(description = "点位ID") @RequestParam(required = false) Long pointId,
            @Parameter(description = "规则类型：METRIC/OFFLINE/COMM") @RequestParam(required = false) String ruleType,
            @Parameter(description = "启用状态：0停用 1启用") @RequestParam(required = false) Integer enabled) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(ruleService.pageRules(user.userId(), current, size,
                typeId, deviceId, pointId, ruleType, enabled));
    }

    @Operation(summary = "创建告警规则", description = "锚定模板指标项；METRIC 点位取模板项点位；设备覆盖需带 deviceId")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @AuditLog(operation = "CREATE_POINT_ALARM_RULE", detail = "模板项:#dto.templateItemId, 类型:#dto.ruleType")
    public Result<PointAlarmRuleVO> create(@Valid @RequestBody PointAlarmRuleCreateDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(ruleService.create(dto, user.userId()));
    }

    @Operation(summary = "更新告警规则", description = "模板指标项与规则类型不可变更")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @AuditLog(operation = "UPDATE_POINT_ALARM_RULE", detail = "规则ID:#id")
    public Result<PointAlarmRuleVO> update(
            @Parameter(description = "规则ID", required = true) @PathVariable Long id,
            @Valid @RequestBody PointAlarmRuleUpdateDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(ruleService.update(id, dto, user.userId()));
    }

    @Operation(summary = "删除告警规则", description = "逻辑删除，仅 ADMIN")
    @DeleteMapping("/{id}")
    @AuditLog(operation = "DELETE_POINT_ALARM_RULE", detail = "规则ID:#id")
    public Result<Void> delete(
            @Parameter(description = "规则ID", required = true) @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        ruleService.delete(id, user.userId());
        return Result.success();
    }

    @Operation(summary = "启用/停用告警规则")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @AuditLog(operation = "UPDATE_POINT_ALARM_RULE_STATUS", detail = "规则ID:#id")
    public Result<Void> updateStatus(
            @Parameter(description = "规则ID", required = true) @PathVariable Long id,
            @Parameter(description = "目标状态", required = true) @RequestParam boolean enabled) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        ruleService.updateStatus(id, enabled, user.userId());
        return Result.success();
    }

    @Operation(summary = "查询设备生效规则集", description = "三级降级解析最终生效规则（设备级覆盖→组织模板→平台模板，按点位替换式），含 source 层级标记")
    @GetMapping("/effective")
    public Result<List<PointAlarmRuleVO>> effective(
            @Parameter(description = "设备ID", required = true) @RequestParam Integer deviceId,
            @Parameter(description = "点位ID（可选，不传返回全部点位规则）") @RequestParam(required = false) Long pointId) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(ruleService.findEffectiveRules(deviceId, pointId, user.userId()));
    }
}
