package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.MetricSampleBatchDTO;
import com.example.platform.dto.MetricSampleDTO;
import com.example.platform.dto.MetricSampleVO;
import com.example.platform.entity.MetricSample;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.MetricSampleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 指标采样历史接口层
 *
 * <p>权限边界：ORG_ADMIN/OPERATOR 可写；任何已认证用户可读；
 * 写入与查询均必须指定 deviceId，由 Service 层按授权设备范围校验。</p>
 */
@Tag(name = "指标采样历史", description = "设备点位历史采样写入与查询（ORG_ADMIN/OPERATOR 写，已认证按范围读）")
@RestController
@RequestMapping("/metric-samples")
@Validated
public class MetricSampleController {

    private final MetricSampleService metricSampleService;
    private final CurrentUserContext currentUserContext;

    public MetricSampleController(MetricSampleService metricSampleService,
                                 CurrentUserContext currentUserContext) {
        this.metricSampleService = metricSampleService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "写入单条指标采样", description = "ORG_ADMIN/OPERATOR；触发 metric_latest 更新")
    @PostMapping
    @PreAuthorize("hasAnyRole('ORG_ADMIN','OPERATOR')")
    @AuditLog(operation = "WRITE_METRIC_SAMPLE", detail = "设备ID:#dto.deviceId, 点位ID:#dto.pointId")
    public Result<MetricSample> save(@Valid @RequestBody MetricSampleDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(metricSampleService.saveSample(dto, user.userId()));
    }

    @Operation(summary = "批量写入指标采样", description = "ORG_ADMIN/OPERATOR；逐条触发 metric_latest 更新")
    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','OPERATOR')")
    @AuditLog(operation = "WRITE_METRIC_SAMPLE_BATCH", detail = "条数:#dto.items.size")
    public Result<List<MetricSampleVO>> saveBatch(@Valid @RequestBody MetricSampleBatchDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(metricSampleService.saveBatch(dto, user.userId()));
    }

    @Operation(summary = "分页查询指标采样历史", description = "查询时间跨度上限 90 天；必须指定 deviceId")
    @GetMapping("/page")
    public Result<Page<MetricSampleVO>> page(
            @Parameter(description = "当前页码", example = "1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "设备ID", required = true) @RequestParam Integer deviceId,
            @Parameter(description = "点位ID（可选）") @RequestParam(required = false) Long pointId,
            @Parameter(description = "起始时间（可选，ISO yyyy-MM-dd'T'HH:mm:ss）")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间（可选，ISO yyyy-MM-dd'T'HH:mm:ss）")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(metricSampleService.pageSamples(
                user.userId(), current, size, deviceId, pointId, startTime, endTime));
    }
}