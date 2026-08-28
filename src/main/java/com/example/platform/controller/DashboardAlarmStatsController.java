package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.DashboardAlarmStatsVO;
import com.example.platform.entity.CloudRoom;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.CloudRoomService;
import com.example.platform.service.DashboardAlarmStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 告警看板统计接口层
 *
 * <p>权限边界：BUSINESS_READ；cloudRoomId 非空时机房不存在直接 4040（机房工作台数据源）。</p>
 */
@Tag(name = "告警看板统计", description = "按授权范围（可选限定机房）的告警聚合统计：状态分布、级别分布、趋势、设备排名")
@RestController
@RequestMapping("/dashboard/alarm-stats")
@Validated
public class DashboardAlarmStatsController {

    private final DashboardAlarmStatsService dashboardAlarmStatsService;
    private final CloudRoomService cloudRoomService;
    private final CurrentUserContext currentUserContext;

    public DashboardAlarmStatsController(DashboardAlarmStatsService dashboardAlarmStatsService,
                                          CloudRoomService cloudRoomService,
                                          CurrentUserContext currentUserContext) {
        this.dashboardAlarmStatsService = dashboardAlarmStatsService;
        this.cloudRoomService = cloudRoomService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "获取告警看板聚合统计", description = "BUSINESS_READ；cloudRoomId 可选——非空时机房不存在返回 4040，统计限定该机房")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public Result<DashboardAlarmStatsVO> getAlarmStats(
            @Parameter(description = "机房ID（可选，限定机房口径）") @RequestParam(required = false) Long cloudRoomId) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        String roomName = null;
        if (cloudRoomId != null) {
            CloudRoom room = cloudRoomService.getCloudRoomOrThrow(cloudRoomId, user.userId());
            roomName = room.getRoomName();
        }
        return Result.success(dashboardAlarmStatsService.getAlarmDashboardStats(
                user.userId(), cloudRoomId, roomName));
    }
}
