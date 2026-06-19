package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.entity.SysAuditLog;
import com.example.platform.service.SysAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 审计日志接口层，提供审计日志的分页查询和过期清理等RESTful接口
 */
@Tag(name = "审计日志", description = "审计日志分页查询与过期清理（查询需 ADMIN/OPERATOR，清理仅 ADMIN）")
@RestController
@RequestMapping("/audit-logs")
@Validated
public class SysAuditLogController {

    private final SysAuditLogService sysAuditLogService;

    public SysAuditLogController(SysAuditLogService sysAuditLogService) {
        this.sysAuditLogService = sysAuditLogService;
    }

    @Operation(summary = "分页查询审计日志", description = "多条件筛选：userId / operation / 时间范围；时间格式 yyyy-MM-dd HH:mm:ss")
    @GetMapping("/page")
    public Result<Page<SysAuditLog>> page(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "操作人ID（可选）") @RequestParam(required = false) Long userId,
            @Parameter(description = "操作类型（可选）") @RequestParam(required = false) String operation,
            @Parameter(description = "开始时间（可选）yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @Parameter(description = "结束时间（可选）yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        int[] pageParams = PaginationUtils.validate(current, size);
        return Result.success(
                sysAuditLogService.pageLogs(pageParams[0], pageParams[1], userId, operation, startTime, endTime));
    }

    @Operation(summary = "清理过期审计日志", description = "清理 beforeTime 之前的过期日志（数据保留策略）；受 SecurityConfig 限制仅 ADMIN 可执行")
    @DeleteMapping("/cleanup")
    @AuditLog(operation = "CLEANUP_AUDIT_LOG", detail = "清理截止时间:#beforeTime")
    public Result<Integer> cleanup(
            @Parameter(description = "保留截止时间 yyyy-MM-dd HH:mm:ss", required = true) @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime beforeTime) {
        int deleted = sysAuditLogService.cleanExpiredLogs(beforeTime);
        return Result.success(deleted);
    }
}