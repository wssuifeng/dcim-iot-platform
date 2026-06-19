package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.BatchHandleAlertDTO;
import com.example.platform.entity.Alert;
import com.example.platform.service.AlertService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/*
 * 类功能:告警管理接口层，提供告警的查询、筛选、处理、统计等RESTful接口
 * */
@RestController
@RequestMapping("/alerts")
@Validated
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /*
     * 方法功能:按条件分页查询告警列表，支持按设备ID、处理状态、告警级别、时间范围筛选
     * 参数:current-当前页码, size-每页大小, deviceId-设备ID（可选）, isHandled-处理状态（可选）, alertLevel-告警级别（可选）, startTime-开始时间（可选）, endTime-结束时间（可选）
     * 返回数据:Result<Page<Alert>>-分页告警数据
     * */
    @GetMapping
    public Result<Page<Alert>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer deviceId,
            @RequestParam(required = false) Integer isHandled,
            @RequestParam(required = false) Integer alertLevel,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        int[] pageParams = PaginationUtils.validate(current, size);
        return Result.success(alertService.pageByCondition(pageParams[0], pageParams[1], deviceId, isHandled, alertLevel, startTime, endTime));
    }

    /*
     * 方法功能:根据ID查询单条告警详情
     * 参数:id-告警ID（路径参数）
     * 返回数据:Result<Alert>-查询到的告警对象
     * */
    @GetMapping("/{id}")
    public Result<Alert> getById(@PathVariable Integer id) {
        return Result.success(alertService.getById(id));
    }

    /*
     * 方法功能:查询所有未处理的告警列表
     * 参数:无
     * 返回数据:Result<List<Alert>>-未处理告警列表
     * */
    @GetMapping("/unhandled")
    public Result<List<Alert>> listUnhandled() {
        return Result.success(alertService.findUnhandled());
    }

    /*
     * 方法功能:批量处理告警，将指定ID的未处理告警标记为已处理
     * 参数:dto-包含告警ID列表和处理人姓名的批量处理请求体
     * 返回数据:Result<Integer>-成功处理的告警条数
     * */
    @PutMapping("/batch-handle")
    @AuditLog(operation = "BATCH_HANDLE_ALERT", detail = "告警ID列表:#dto.ids, 处理人:#dto.handleUser")
    public Result<Integer> batchHandle(@Validated @RequestBody BatchHandleAlertDTO dto) {
        int count = alertService.batchHandle(dto.getIds(), dto.getHandleUser());
        return Result.success(count);
    }

    /*
     * 方法功能:获取指定设备的告警统计信息（总数、未处理数、级别分布）
     * 参数:deviceId-设备ID（路径参数）
     * 返回数据:Result<Map<String, Object>>-告警统计结果
     * */
    @GetMapping("/stats/device/{deviceId}")
    public Result<Map<String, Object>> getStatsByDeviceId(@PathVariable Integer deviceId) {
        return Result.success(alertService.getStatsByDeviceId(deviceId));
    }

    /*
     * 方法功能:获取指定时间段内的全局告警统计信息
     * 参数:startTime-开始时间, endTime-结束时间
     * 返回数据:Result<Map<String, Object>>-告警统计结果
     * */
    @GetMapping("/stats/time-range")
    public Result<Map<String, Object>> getStatsByTimeRange(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(alertService.getStatsByTimeRange(startTime, endTime));
    }

    /*
     * 方法功能:按时间段统计各设备的告警数量排行
     * 参数:startTime-开始时间, endTime-结束时间
     * 返回数据:Result<List<Map<String, Object>>>-设备告警排行列表
     * */
    @GetMapping("/stats/device-ranking")
    public Result<List<Map<String, Object>>> getDeviceAlertRanking(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(alertService.listDeviceAlertCountByTimeRange(startTime, endTime));
    }
}
