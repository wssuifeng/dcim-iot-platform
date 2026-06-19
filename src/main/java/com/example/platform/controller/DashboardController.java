package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.DashboardStatsDTO;
import com.example.platform.service.DashboardStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘接口层，提供仪表盘统计数据的查询接口
 */
@Tag(name = "仪表盘统计", description = "设备/告警聚合统计，含互斥锁防缓存击穿")
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardStatsService dashboardStatsService;

    public DashboardController(DashboardStatsService dashboardStatsService) {
        this.dashboardStatsService = dashboardStatsService;
    }

    @Operation(summary = "获取仪表盘统计数据", description = "返回设备状态分布 + 告警统计聚合数据；使用 Redis 缓存（stats:dashboard，TTL 30s）+ 互斥锁防击穿")
    @GetMapping("/stats")
    public Result<DashboardStatsDTO> getStats() {
        return Result.success(dashboardStatsService.getDashboardStats());
    }
}
