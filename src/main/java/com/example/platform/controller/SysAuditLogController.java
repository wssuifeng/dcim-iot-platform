package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.entity.SysAuditLog;
import com.example.platform.service.SysAuditLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/*
 * 类功能:审计日志接口层，提供审计日志的分页查询和过期清理等RESTful接口
 * */
@RestController
@RequestMapping("/audit-logs")
@Validated
public class SysAuditLogController {

    private final SysAuditLogService sysAuditLogService;

    public SysAuditLogController(SysAuditLogService sysAuditLogService) {
        this.sysAuditLogService = sysAuditLogService;
    }

    /*
     * 方法功能:按条件分页查询审计日志，支持按用户ID、操作类型、时间范围筛选
     * 参数:current-当前页码, size-每页大小, userId-操作人ID（可选）, operation-操作类型（可选）, startTime-开始时间（可选）, endTime-结束时间（可选）
     * 返回数据:Result<Page<SysAuditLog>>-分页审计日志数据
     * */
    @GetMapping("/page")
    public Result<Page<SysAuditLog>> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        int[] pageParams = PaginationUtils.validate(current, size);
        return Result.success(
                sysAuditLogService.pageLogs(pageParams[0], pageParams[1], userId, operation, startTime, endTime));
    }

    /*
     * 方法功能:清理指定时间之前的过期审计日志（数据保留策略）
     * 参数:beforeTime-保留截止时间
     * 返回数据:Result<Integer>-删除的记录条数
     * */
    @DeleteMapping("/cleanup")
    @AuditLog(operation = "CLEANUP_AUDIT_LOG", detail = "清理截止时间:#beforeTime")
    public Result<Integer> cleanup(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime beforeTime) {
        int deleted = sysAuditLogService.cleanExpiredLogs(beforeTime);
        return Result.success(deleted);
    }
}