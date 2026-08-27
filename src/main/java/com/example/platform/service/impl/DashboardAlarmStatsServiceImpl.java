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
 * */
@Service
@RequiredArgsConstructor
public class DashboardAlarmStatsServiceImpl implements DashboardAlarmStatsService {

    private static final int RECENT_DAYS = 7;

    private final AlarmEventService alarmEventService;

    /*
     * 方法功能:获取告警看板统计
     * 参数:currentUserId-当前用户ID
     * 返回数据:DashboardAlarmStatsVO-状态、级别、趋势和设备排名统计
     * */
    @Override
    public DashboardAlarmStatsVO getAlarmDashboardStats(Long currentUserId) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(RECENT_DAYS - 1L);
        DashboardAlarmStatsVO vo = new DashboardAlarmStatsVO();
        AlarmStatsVO stats = alarmEventService.countStats(currentUserId, null, null);
        vo.setStateDistribution(stats.getStateDistribution());
        vo.setLevelDistribution(stats.getLevelDistribution());
        vo.setDailyTrend(alarmEventService.trendStats(currentUserId, startDate, today));
        vo.setDeviceRanking(stats.getDeviceRanking());
        return vo;
    }
}
