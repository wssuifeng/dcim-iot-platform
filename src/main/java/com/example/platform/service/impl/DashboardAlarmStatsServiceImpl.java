package com.example.platform.service.impl;

import com.example.platform.dto.AlarmStatsVO;
import com.example.platform.dto.DashboardAlarmStatsVO;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.DashboardAlarmStatsService;
import com.example.platform.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/*
 * 类功能:告警看板统计服务实现类，组合告警事件服务提供的范围过滤聚合结果
 * */
@Service
@RequiredArgsConstructor
public class DashboardAlarmStatsServiceImpl implements DashboardAlarmStatsService {

    private static final int RECENT_DAYS = 7;

    private final AlarmEventService alarmEventService;
    private final DataScopeService dataScopeService;

    /*
     * 方法功能:获取当前用户授权范围内的告警看板统计
     * 参数:currentUserId-当前用户ID
     * 返回数据:DashboardAlarmStatsVO-状态、级别、趋势和设备排名统计
     * */
    @Override
    public DashboardAlarmStatsVO getAlarmDashboardStats(Long currentUserId) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(RECENT_DAYS - 1L);
        DashboardAlarmStatsVO vo = new DashboardAlarmStatsVO();
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        vo.setScopeDescription(buildScopeDescription(context));
        if (!context.isPlatformAdmin() && context.getOrganizationIds().isEmpty()) {
            vo.setStateDistribution(java.util.Map.of());
            vo.setLevelDistribution(java.util.Map.of());
            vo.setDailyTrend(emptyTrend(startDate, today));
            vo.setDeviceRanking(java.util.List.of());
            return vo;
        }
        AlarmStatsVO stats = alarmEventService.countStats(currentUserId, null, null);
        vo.setStateDistribution(stats.getStateDistribution());
        vo.setLevelDistribution(stats.getLevelDistribution());
        vo.setDailyTrend(alarmEventService.trendStats(currentUserId, startDate, today));
        vo.setDeviceRanking(stats.getDeviceRanking());
        return vo;
    }

    private java.util.Map<String, Long> emptyTrend(LocalDate start, LocalDate end) {
        java.util.Map<String, Long> trend = new java.util.LinkedHashMap<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            trend.put(day.toString(), 0L);
        }
        return trend;
    }

    private String buildScopeDescription(DataScopeContext context) {
        if (context.isPlatformAdmin()) {
            return "PLATFORM: 全量";
        }
        String scope = context.getScopeType() == null ? "UNKNOWN" : context.getScopeType();
        return scope + ": 组织" + context.getOrganizationIds();
    }
}
