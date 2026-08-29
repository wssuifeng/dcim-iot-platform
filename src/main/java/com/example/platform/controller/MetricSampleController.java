package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.excel.EasyExcel;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.common.util.ExcelExportUtil;
import com.example.platform.dto.MetricSampleBatchDTO;
import com.example.platform.dto.MetricSampleBatchResultVO;
import com.example.platform.dto.MetricSampleDTO;
import com.example.platform.dto.MetricSampleExportRow;
import com.example.platform.dto.MetricSampleTrendPointVO;
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
 * <p>权限边界：ADMIN/OPERATOR 可写；任何已认证用户可读；
 * 写入与查询均必须指定 deviceId，由 Service 层按授权设备范围校验。</p>
 */
@Tag(name = "指标采样历史", description = "设备指标历史采样写入与查询（ADMIN/OPERATOR 写，已认证按范围读）")
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

    @Operation(summary = "写入单条指标采样", description = "ADMIN/OPERATOR；T1事务（采样+最新值+设备在线），提交后异步触发告警评估")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @AuditLog(operation = "WRITE_METRIC_SAMPLE", detail = "设备ID:#dto.deviceId, 指标ID:#dto.pointId")
    public Result<MetricSample> save(@Valid @RequestBody MetricSampleDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        // HTTP 上报恒触发告警评估（模拟运行走服务层传开关值）
        return Result.success(metricSampleService.saveSample(dto, user.userId(), true));
    }

    @Operation(summary = "批量写入指标采样", description = "ADMIN/OPERATOR；逐条独立事务（非原子，部分成功合法），返回成功数与失败明细")
    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @AuditLog(operation = "WRITE_METRIC_SAMPLE_BATCH", detail = "条数:#dto.items.size")
    public Result<MetricSampleBatchResultVO> saveBatch(@Valid @RequestBody MetricSampleBatchDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(metricSampleService.saveBatch(dto, user.userId(), true));
    }

    @Operation(summary = "分页查询指标采样历史", description = "查询时间跨度上限 90 天；必须指定 deviceId")
    @GetMapping("/page")
    public Result<Page<MetricSampleVO>> page(
            @Parameter(description = "当前页码", example = "1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "设备ID", required = true) @RequestParam Integer deviceId,
            @Parameter(description = "指标ID（可选）") @RequestParam(required = false) Long pointId,
            @Parameter(description = "起始时间（可选，ISO yyyy-MM-dd'T'HH:mm:ss）")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间（可选，ISO yyyy-MM-dd'T'HH:mm:ss）")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(metricSampleService.pageSamples(
                user.userId(), current, size, deviceId, pointId, startTime, endTime));
    }

    @Operation(summary = "查询指标趋势（分桶聚合）", description = "趋势图专用：按桶宽聚合均值/最小/最大，桶数≤200；须指定 deviceId+pointId+时间范围，跨度≤90天")
    @GetMapping("/trend")
    public Result<List<MetricSampleTrendPointVO>> trend(
            @Parameter(description = "设备ID", required = true) @RequestParam Integer deviceId,
            @Parameter(description = "指标ID", required = true) @RequestParam Long pointId,
            @Parameter(description = "起始时间（ISO yyyy-MM-dd'T'HH:mm:ss）", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间（ISO yyyy-MM-dd'T'HH:mm:ss）", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(metricSampleService.trendSamples(
                user.userId(), deviceId, pointId, startTime, endTime));
    }

    @Operation(summary = "导出指标采样 Excel", description = "上限 10000 行；必须指定 deviceId，时间跨度上限 90 天")
    @GetMapping("/export")
    public void export(
            @Parameter(description = "设备ID", required = true) @RequestParam Integer deviceId,
            @Parameter(description = "指标ID（可选）") @RequestParam(required = false) Long pointId,
            @Parameter(description = "起始时间（可选）") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间（可选）") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        CurrentUser user = currentUserContext.requireCurrentUser();
        List<MetricSampleExportRow> rows = metricSampleService.exportRows(
                user.userId(), deviceId, pointId, startTime, endTime);
        ExcelExportUtil.setExcelResponse(response, "指标采样导出");
        EasyExcel.write(response.getOutputStream(), MetricSampleExportRow.class)
                .sheet("指标采样")
                .doWrite(rows);
    }
}