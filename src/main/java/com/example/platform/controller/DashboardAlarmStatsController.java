package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.DashboardAlarmStatsVO;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.DashboardAlarmStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 告警看板统计接口层
 *
 * <p>权限边界：BUSINESS_READ；聚合数据不跨组织或机房泄露。</p>
 */
@Tag(name = "告警看板统计", description = "按授权范围的告警聚合统计（状态分布、级别分布、趋势、设备排名）")
@RestController
@RequestMapping("/dashboard/alarm-stats")
@Validated
public class DashboardAlarmStatsController {

    private final DashboardAlarmStatsService dashboardAlarmStatsService;
    private final CurrentUserContext currentUserContext;

    public DashboardAlarmStatsController(DashboardAlarmStatsService dashboardAlarmStatsService,
                                          CurrentUserContext currentUserContext) {
        this.dashboardAlarmStatsService = dashboardAlarmStatsService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "获取告警看板聚合统计", description = "BUSINESS_READ；按当前用户授权范围聚合")
    @GetMapping
    @PreAuthorize("hasAnyRole('ORG_ADMIN','OPERATOR','VIEWER')")
    public Result<DashboardAlarmStatsVO> getAlarmStats() {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(dashboardAlarmStatsService.getAlarmDashboardStats(user.userId()));
    }
}