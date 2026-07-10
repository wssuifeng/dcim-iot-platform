package com.example.platform.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.ResultCode;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.common.group.CreateGroup;
import com.example.platform.common.util.ExcelExportUtil;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.DeviceDataBatchDTO;
import com.example.platform.dto.DeviceDataExportRow;
import com.example.platform.entity.DeviceData;
import com.example.platform.service.DeviceDataService;
import com.example.platform.service.DeviceService;
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
 * 设备数据接口层，提供设备数据上报、查询、统计及分页等RESTful接口
 */
@Tag(name = "设备数据", description = "设备数据上报、历史查询、统计聚合与分页")
@RestController
@RequestMapping("/device-data")
@Validated
public class DeviceDataController {

    private final DeviceDataService deviceDataService;
    private final DeviceService deviceService;
    private final CurrentUserContext currentUserContext;

    public DeviceDataController(DeviceDataService deviceDataService,
                                DeviceService deviceService,
                                CurrentUserContext currentUserContext) {
        this.deviceDataService = deviceDataService;
        this.deviceService = deviceService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "单条数据上报", description = "保存单条设备数据并触发告警规则检查；设备不存在时抛出 DeviceNotFoundException")
    @PostMapping
    public Result<Void> reportData(@Validated(CreateGroup.class) @RequestBody DeviceData deviceData) {
        deviceDataService.saveWithAlertCheckScoped(
                deviceData, currentUserContext.requireCurrentUser().userId());
        return Result.success();
    }

    @Operation(summary = "批量数据上报", description = "批量接收设备数据（1-1000条），逐条保存并触发告警检查")
    @PostMapping("/batch")
    public Result<Void> reportBatch(@Validated @RequestBody DeviceDataBatchDTO batchDTO) {
        deviceDataService.saveBatchWithAlertCheckScoped(
                batchDTO.getDataList(), currentUserContext.requireCurrentUser().userId());
        return Result.success();
    }

    @Operation(summary = "查询设备历史数据（分页）", description = "按采集时间倒序分页返回指定设备的历史数据")
    @GetMapping("/device/{deviceId}")
    public Result<Page<DeviceData>> listByDevice(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer deviceId,
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小，默认20") @RequestParam(defaultValue = "20") Integer size) {
        int[] pageParams = PaginationUtils.validate(current, size);
        return Result.success(deviceDataService.pageByDeviceIdScoped(
                deviceId, pageParams[0], pageParams[1], currentUserContext.requireCurrentUser().userId()));
    }

    @Operation(summary = "查询设备最新数据", description = "返回设备最新一条采集记录；无数据时返回 404")
    @GetMapping("/device/{deviceId}/latest")
    public Result<DeviceData> getLatest(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer deviceId) {
        DeviceData latest = deviceDataService.findLatestByDeviceIdScoped(
                deviceId, currentUserContext.requireCurrentUser().userId());
        if (latest == null) {
            return Result.error(ResultCode.RESOURCE_NOT_FOUND, "该设备暂无采集数据");
        }
        return Result.success(latest);
    }

    @Operation(summary = "按时间段查询数据", description = "返回指定设备在 [startTime, endTime] 区间内的全部数据，时间格式 yyyy-MM-dd HH:mm:ss")
    @GetMapping("/device/{deviceId}/range")
    public Result<List<DeviceData>> listByTimeRange(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer deviceId,
            @Parameter(description = "开始时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        List<DeviceData> list = deviceDataService.findByDeviceIdAndTimeRangeScoped(
                deviceId, startTime, endTime, currentUserContext.requireCurrentUser().userId());
        return Result.success(list);
    }

    @Operation(summary = "时间段统计数据", description = "返回 minVal/maxVal/avgVal/count 等统计指标；无数据时对应字段为 null")
    @GetMapping("/device/{deviceId}/statistics")
    public Result<Map<String, Object>> getStatistics(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer deviceId,
            @Parameter(description = "开始时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        Map<String, Object> stats = deviceDataService.getStatisticsScoped(
                deviceId, startTime, endTime, currentUserContext.requireCurrentUser().userId());
        return Result.success(stats);
    }

    @Operation(summary = "全局分页查询数据", description = "可选按 deviceId 筛选的全局数据分页")
    @GetMapping("/page")
    public Result<Page<DeviceData>> listByPage(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "设备ID筛选（可选）") @RequestParam(required = false) Integer deviceId) {
        int[] pageParams = PaginationUtils.validate(current, size);
        return Result.success(deviceDataService.pageByConditionScoped(
                deviceId, pageParams[0], pageParams[1], currentUserContext.requireCurrentUser().userId()));
    }

    @Operation(summary = "按时间粒度聚合统计", description = "支持按 hour/day/week 聚合设备数据，返回聚合统计列表")
    @GetMapping("/device/{deviceId}/aggregate")
    public Result<List<Map<String, Object>>> aggregate(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer deviceId,
            @Parameter(description = "开始时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @Parameter(description = "聚合粒度 hour/day/week，默认 day") @RequestParam(defaultValue = "day") String granularity) {
        return Result.success(deviceDataService.aggregateScoped(
                deviceId, startTime, endTime, granularity,
                currentUserContext.requireCurrentUser().userId()));
    }

    @Operation(summary = "导出设备数据", description = "按设备ID+时间范围导出 Excel，单次上限 10000 条，时间范围不超过 30 天")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "返回 .xlsx 文件流"),
            @ApiResponse(responseCode = "400", description = "时间范围超过30天/导出超过10000条(4065)"),
            @ApiResponse(responseCode = "403", description = "VIEWER 角色无权导出")
    })
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'OPERATOR')")
    @AuditLog(operation = "EXPORT_DEVICE_DATA", detail = "设备ID:#deviceId")
    public void exportData(
            @Parameter(description = "设备ID", required = true) @RequestParam Integer deviceId,
            @Parameter(description = "开始时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            HttpServletResponse response) throws IOException {
        List<DeviceData> dataList = deviceDataService.findByDeviceIdAndTimeRangeScoped(
                deviceId, startTime, endTime, currentUserContext.requireCurrentUser().userId());
        if (dataList.size() > 10000) {
            throw new com.example.platform.common.exception.BusinessException(
                    ResultCode.EXPORT_SIZE_EXCEED, "导出数据量超过 10000 条限制");
        }
        List<DeviceDataExportRow> rows = dataList.stream().map(d -> {
            DeviceDataExportRow row = new DeviceDataExportRow();
            row.setDeviceId(d.getDeviceId());
            row.setMetric(d.getMetric());
            row.setDataValue(d.getDataValue());
            row.setCollectTime(d.getCollectTime());
            return row;
        }).collect(Collectors.toList());
        ExcelExportUtil.setExcelResponse(response, "设备数据_" + deviceId);
        EasyExcel.write(response.getOutputStream(), DeviceDataExportRow.class)
                .sheet("设备数据")
                .doWrite(rows);
    }
}
