package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
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

    @GetMapping
    public Result<Page<Alert>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer deviceId,
            @RequestParam(required = false) Integer isHandled,
            @RequestParam(required = false) Integer alertLevel,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(alertService.pageByCondition(current, size, deviceId, isHandled, alertLevel, startTime, endTime));
    }

    @GetMapping("/{id}")
    public Result<Alert> getById(@PathVariable Integer id) {
        return Result.success(alertService.getById(id));
    }

    @GetMapping("/unhandled")
    public Result<List<Alert>> listUnhandled() {
        return Result.success(alertService.findUnhandled());
    }

    @PutMapping("/batch-handle")
    public Result<Integer> batchHandle(@Validated @RequestBody BatchHandleAlertDTO dto) {
        int count = alertService.batchHandle(dto.getIds(), dto.getHandleUser());
        return Result.success(count);
    }

    @GetMapping("/stats/device/{deviceId}")
    public Result<Map<String, Object>> getStatsByDeviceId(@PathVariable Integer deviceId) {
        return Result.success(alertService.getStatsByDeviceId(deviceId));
    }

    @GetMapping("/stats/time-range")
    public Result<Map<String, Object>> getStatsByTimeRange(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(alertService.getStatsByTimeRange(startTime, endTime));
    }

    @GetMapping("/stats/device-ranking")
    public Result<List<Map<String, Object>>> getDeviceAlertRanking(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(alertService.listDeviceAlertCountByTimeRange(startTime, endTime));
    }
}
