package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.DashboardStatsDTO;
import com.example.platform.service.DashboardStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * 类功能:仪表盘接口层，提供仪表盘统计数据的查询接口
 * */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardStatsService dashboardStatsService;

    public DashboardController(DashboardStatsService dashboardStatsService) {
        this.dashboardStatsService = dashboardStatsService;
    }

    /*
     * 方法功能:获取仪表盘统计数据（设备统计+告警统计）
     * 参数:无
     * 返回数据:Result<DashboardStatsDTO>-仪表盘统计数据
     * */
    @GetMapping("/stats")
    public Result<DashboardStatsDTO> getStats() {
        return Result.success(dashboardStatsService.getDashboardStats());
    }
}
