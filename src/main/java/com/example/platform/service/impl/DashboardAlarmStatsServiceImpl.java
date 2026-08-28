package com.example.platform.service.impl;

import com.example.platform.dto.AlarmStatsVO;
import com.example.platform.dto.DashboardAlarmStatsVO;
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.DashboardAlarmStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/*
 * 类功能:告警看板统计服务实现类，组合告警事件服务提供的聚合结果
 *
 * 【机房过滤】cloudRoomId 非空时聚合限定该机房（机房工作台概览数据源）；
 *   scopeDescription 同步填充机房名，便于前端展示当前统计口径。
 * */
@Service
@RequiredArgsConstructor
public class DashboardAlarmStatsServiceImpl implements DashboardAlarmStatsService {

    private static final int RECENT_DAYS = 7;

    private final AlarmEventService alarmEventService;

    /*
     * 方法功能:获取告警看板统计（全量口径）
     * 参数:currentUserId-当前用户ID
     * 返回数据:DashboardAlarmStatsVO-状态、级别、趋势和设备排名统计
     * */
    @Override
    public DashboardAlarmStatsVO getAlarmDashboardStats(Long currentUserId) {
        return getAlarmDashboardStats(currentUserId, null, null);
    }

    /*
     * 方法功能:获取告警看板统计（限定机房口径）
     * 参数:currentUserId-当前用户ID, cloudRoomId-机房ID（null=全量）, roomName-机房名（scope 展示用）
     * 返回数据:DashboardAlarmStatsVO-状态、级别、趋势和设备排名统计
     * */
    @Override
    public DashboardAlarmStatsVO getAlarmDashboardStats(Long currentUserId, Long cloudRoomId, String roomName) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(RECENT_DAYS - 1L);
        DashboardAlarmStatsVO vo = new DashboardAlarmStatsVO();
        AlarmStatsVO stats = alarmEventService.countStats(currentUserId, null, null, cloudRoomId);
        vo.setStateDistribution(stats.getStateDistribution());
        vo.setLevelDistribution(stats.getLevelDistribution());
        vo.setDailyTrend(alarmEventService.trendStats(currentUserId, startDate, today, cloudRoomId));
        vo.setDeviceRanking(stats.getDeviceRanking());
        vo.setScopeDescription(cloudRoomId != null ? "机房：" + roomName : null);
        return vo;
    }
}
