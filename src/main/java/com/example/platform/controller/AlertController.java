package com.example.platform.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.ResultCode;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.common.util.ExcelExportUtil;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.AlertExportRow;
import com.example.platform.dto.BatchHandleAlertDTO;
import com.example.platform.entity.Alert;
import com.example.platform.service.AlertService;
import com.example.platform.security.CurrentUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 告警管理接口层，提供告警的查询、筛选、处理、统计等RESTful接口
 */
@Tag(name = "告警管理", description = "告警查询、筛选、批量处理与多维度统计")
@RestController
@RequestMapping("/alerts")
@Validated
public class AlertController {

    private final AlertService alertService;
    private final CurrentUserContext currentUserContext;

    public AlertController(AlertService alertService, CurrentUserContext currentUserContext) {
        this.alertService = alertService;
        this.currentUserContext = currentUserContext;
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
        return Result.success(alertService.pageByConditionScoped(
                pageParams[0], pageParams[1], deviceId, isHandled, alertLevel,
                startTime, endTime, currentUserContext.requireCurrentUser().userId()));
    }

    @Operation(summary = "根据ID查询告警详情", description = "返回单条告警记录")
    @GetMapping("/{id}")
    public Result<Alert> getById(
            @Parameter(description = "告警ID", required = true) @PathVariable Integer id) {
        Alert alert = alertService.getScoped(id, currentUserContext.requireCurrentUser().userId());
        if (alert == null) {
            return Result.error(ResultCode.ALERT_NOT_FOUND, "告警不存在");
        }
        return Result.success(alert);
    }

    @Operation(summary = "查询未处理告警列表", description = "返回所有 isHandled=0 的告警")
    @GetMapping("/unhandled")
    public Result<List<Alert>> listUnhandled() {
        return Result.success(alertService.findUnhandledScoped(
                currentUserContext.requireCurrentUser().userId()));
    }

    @Operation(summary = "批量处理告警", description = "将指定 ID 的未处理告警标记为已处理，单次上限 50 条；返回成功处理的条数")
    @PutMapping("/batch-handle")
    @AuditLog(operation = "BATCH_HANDLE_ALERT", detail = "告警ID列表:#dto.ids, 处理人:#dto.handleUser")
    public Result<Integer> batchHandle(@Validated @RequestBody BatchHandleAlertDTO dto) {
        int count = alertService.batchHandleScoped(
                dto.getIds(), dto.getHandleUser(), currentUserContext.requireCurrentUser().userId());
        return Result.success(count);
    }

    @Operation(summary = "按设备统计告警", description = "返回指定设备的告警总数、已处理数、未处理数及各级别数量分布")
    @GetMapping("/stats/device/{deviceId}")
    public Result<Map<String, Object>> getStatsByDeviceId(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer deviceId) {
        return Result.success(alertService.getStatsByDeviceIdScoped(
                deviceId, currentUserContext.requireCurrentUser().userId()));
    }

    @Operation(summary = "按时间段统计告警", description = "返回指定时间段内的全局告警统计")
    @GetMapping("/stats/time-range")
    public Result<Map<String, Object>> getStatsByTimeRange(
            @Parameter(description = "开始时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(alertService.getStatsByTimeRangeScoped(
                startTime, endTime, currentUserContext.requireCurrentUser().userId()));
    }

    @Operation(summary = "设备告警排行", description = "按时间段统计各设备的告警数量排名")
    @GetMapping("/stats/device-ranking")
    public Result<List<Map<String, Object>>> getDeviceAlertRanking(
            @Parameter(description = "开始时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(alertService.listDeviceAlertCountByTimeRangeScoped(
                startTime, endTime, currentUserContext.requireCurrentUser().userId()));
    }

    @Operation(summary = "导出告警记录", description = "按筛选条件导出 Excel，单次上限 10000 条")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "返回 .xlsx 文件流"),
            @ApiResponse(responseCode = "400", description = "导出超过10000条(4065)"),
            @ApiResponse(responseCode = "403", description = "VIEWER 角色无权导出")
    })
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'OPERATOR')")
    @AuditLog(operation = "EXPORT_ALERT", detail = "导出告警记录")
    public void exportAlerts(
            @Parameter(description = "设备ID筛选（可选）") @RequestParam(required = false) Integer deviceId,
            @Parameter(description = "处理状态筛选（可选）") @RequestParam(required = false) Integer isHandled,
            @Parameter(description = "告警级别筛选（可选）") @RequestParam(required = false) Integer alertLevel,
            @Parameter(description = "开始时间（可选）yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间（可选）yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            HttpServletResponse response) throws IOException {
        List<Alert> alertList = alertService.listByConditionScoped(
                deviceId, isHandled, alertLevel, startTime, endTime,
                currentUserContext.requireCurrentUser().userId());
        if (alertList.size() > 10000) {
            throw new com.example.platform.common.exception.BusinessException(
                    ResultCode.EXPORT_SIZE_EXCEED, "导出数据量超过 10000 条限制");
        }
        List<AlertExportRow> rows = alertList.stream().map(a -> {
            AlertExportRow row = new AlertExportRow();
            row.setId(a.getId());
            row.setDeviceId(a.getDeviceId());
            row.setAlertType(a.getAlertType());
            row.setAlertLevel(a.getAlertLevel());
            row.setAlertMessage(a.getAlertMessage());
            row.setHandledText(a.getIsHandled() != null && a.getIsHandled() == 1 ? "已处理" : "未处理");
            row.setHandleUser(a.getHandleUser());
            row.setHandleTime(a.getHandleTime());
            row.setCreateTime(a.getCreateTime());
            return row;
        }).collect(Collectors.toList());
        ExcelExportUtil.setExcelResponse(response, ExcelExportUtil.buildFileName("告警记录"));
        EasyExcel.write(response.getOutputStream(), AlertExportRow.class)
                .sheet("告警记录")
                .doWrite(rows);
    }
}
