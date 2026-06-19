package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.BatchHandleAlertDTO;
import com.example.platform.entity.Alert;
import com.example.platform.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 告警管理接口层，提供告警的查询、筛选、处理、统计等RESTful接口
 */
@Tag(name = "告警管理", description = "告警查询、筛选、批量处理与多维度统计")
@RestController
@RequestMapping("/alerts")
@Validated
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @Operation(summary = "分页查询告警", description = "多条件筛选：deviceId / isHandled / alertLevel / 时间范围；时间格式 yyyy-MM-dd HH:mm:ss")
    @GetMapping
    public Result<Page<Alert>> list(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "设备ID筛选（可选）") @RequestParam(required = false) Integer deviceId,
            @Parameter(description = "处理状态筛选（可选）：0-未处理，1-已处理") @RequestParam(required = false) Integer isHandled,
            @Parameter(description = "告警级别筛选（可选）：1-紧急 … 5-提示") @RequestParam(required = false) Integer alertLevel,
            @Parameter(description = "开始时间（可选）yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间（可选）yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        int[] pageParams = PaginationUtils.validate(current, size);
        return Result.success(alertService.pageByCondition(pageParams[0], pageParams[1], deviceId, isHandled, alertLevel, startTime, endTime));
    }

    @Operation(summary = "根据ID查询告警详情", description = "返回单条告警记录")
    @GetMapping("/{id}")
    public Result<Alert> getById(
            @Parameter(description = "告警ID", required = true) @PathVariable Integer id) {
        return Result.success(alertService.getById(id));
    }

    @Operation(summary = "查询未处理告警列表", description = "返回所有 isHandled=0 的告警")
    @GetMapping("/unhandled")
    public Result<List<Alert>> listUnhandled() {
        return Result.success(alertService.findUnhandled());
    }

    @Operation(summary = "批量处理告警", description = "将指定 ID 的未处理告警标记为已处理，单次上限 50 条；返回成功处理的条数")
    @PutMapping("/batch-handle")
    @AuditLog(operation = "BATCH_HANDLE_ALERT", detail = "告警ID列表:#dto.ids, 处理人:#dto.handleUser")
    public Result<Integer> batchHandle(@Validated @RequestBody BatchHandleAlertDTO dto) {
        int count = alertService.batchHandle(dto.getIds(), dto.getHandleUser());
        return Result.success(count);
    }

    @Operation(summary = "按设备统计告警", description = "返回指定设备的告警总数、已处理数、未处理数及各级别数量分布")
    @GetMapping("/stats/device/{deviceId}")
    public Result<Map<String, Object>> getStatsByDeviceId(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer deviceId) {
        return Result.success(alertService.getStatsByDeviceId(deviceId));
    }

    @Operation(summary = "按时间段统计告警", description = "返回指定时间段内的全局告警统计")
    @GetMapping("/stats/time-range")
    public Result<Map<String, Object>> getStatsByTimeRange(
            @Parameter(description = "开始时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(alertService.getStatsByTimeRange(startTime, endTime));
    }

    @Operation(summary = "设备告警排行", description = "按时间段统计各设备的告警数量排名")
    @GetMapping("/stats/device-ranking")
    public Result<List<Map<String, Object>>> getDeviceAlertRanking(
            @Parameter(description = "开始时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(alertService.listDeviceAlertCountByTimeRange(startTime, endTime));
    }
}
