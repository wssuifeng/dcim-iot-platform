package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.MetricRetentionPolicyDTO;
import com.example.platform.entity.MetricRetentionPolicy;
import com.example.platform.service.MetricRetentionPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 指标数据保留策略接口层
 *
 * <p>权限边界：仅 PLATFORM_ADMIN 可写（全局策略或单点策略）；任何已认证用户可读（策略本身不泄露业务数据）。
 * 保留天数上限 90 天，由 DTO 校验保证（共享契约：不得默认启用无限保留）。</p>
 */
@Tag(name = "指标保留策略", description = "全局/点位级指标数据保留策略管理（PLATFORM_ADMIN 写）")
@RestController
@RequestMapping("/metric-retention-policies")
@Validated
public class MetricRetentionPolicyController {

    private final MetricRetentionPolicyService metricRetentionPolicyService;

    public MetricRetentionPolicyController(MetricRetentionPolicyService metricRetentionPolicyService) {
        this.metricRetentionPolicyService = metricRetentionPolicyService;
    }

    @Operation(summary = "查询全局默认保留策略")
    @GetMapping("/global")
    public Result<MetricRetentionPolicy> getGlobal() {
        return Result.success(metricRetentionPolicyService.getGlobalPolicyOrThrow());
    }

    @Operation(summary = "按点位查询保留策略", description = "pointId 为 null 等价于查询全局策略")
    @GetMapping("/point/{pointId}")
    public Result<MetricRetentionPolicy> getByPoint(
            @Parameter(description = "点位ID", required = true) @PathVariable Long pointId) {
        MetricRetentionPolicy policy = metricRetentionPolicyService.findByPointId(pointId);
        return Result.success(policy);
    }

    @Operation(summary = "解析某点位的有效保留天数", description = "点位策略优先、回退全局、再回退 90 默认值；传 pointId=0 时视为全局")
    @GetMapping("/effective/{pointId}")
    public Result<Integer> effective(
            @Parameter(description = "点位ID（传 0 视为全局）", required = true) @PathVariable Long pointId) {
        Long effectivePointId = (pointId != null && pointId == 0L) ? null : pointId;
        return Result.success(metricRetentionPolicyService.resolveEffectiveRetentionDays(effectivePointId));
    }

    @Operation(summary = "列出全部保留策略")
    @GetMapping("/list")
    public Result<List<MetricRetentionPolicy>> list() {
        return Result.success(metricRetentionPolicyService.listAllPolicies());
    }

    @Operation(summary = "更新或创建保留策略", description = "PLATFORM_ADMIN 专用；retentionDays 1~90；pointId 为 null 表示全局")
    @PutMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @AuditLog(operation = "UPSERT_RETENTION_POLICY", detail = "点位ID:#dto.pointId, 天数:#dto.retentionDays")
    public Result<MetricRetentionPolicy> upsert(@Valid @RequestBody MetricRetentionPolicyDTO dto) {
        return Result.success(metricRetentionPolicyService.upsertPolicy(dto));
    }
}