package com.example.platform.service.impl;

import com.example.platform.dto.AlarmStatsVO;
import com.example.platform.dto.DashboardAlarmStatsVO;
import com.example.platform.service.AlarmEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:DashboardAlarmStatsServiceImpl 单元测试，验证告警事件聚合服务委托
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardAlarmStatsService 单元测试")
class DashboardAlarmStatsServiceImplTest {

    @Mock
    private AlarmEventService alarmEventService;

    @InjectMocks
    private DashboardAlarmStatsServiceImpl dashboardAlarmStatsService;

    private static final Long CURRENT_USER_ID = 60L;

    @Test
    @DisplayName("委托告警统计与近7天趋势聚合")
    void should_delegateAggregation() {
        AlarmStatsVO stats = new AlarmStatsVO();
        stats.setStateDistribution(Map.of("TRIGGERED", 2L));
        stats.setLevelDistribution(Map.of("L2", 2L));
        stats.setDeviceRanking(List.of(Map.of("deviceId", 5, "alarmCount", 2L)));
        when(alarmEventService.countStats(eq(CURRENT_USER_ID), any(), any())).thenReturn(stats);
        when(alarmEventService.trendStats(eq(CURRENT_USER_ID), any(), any()))
                .thenReturn(Map.of(LocalDate.now().toString(), 2L));

        DashboardAlarmStatsVO result = dashboardAlarmStatsService.getAlarmDashboardStats(CURRENT_USER_ID);

        assertThat(result.getStateDistribution()).containsEntry("TRIGGERED", 2L);
        assertThat(result.getLevelDistribution()).containsEntry("L2", 2L);
        assertThat(result.getDeviceRanking()).hasSize(1);
        assertThat(result.getDailyTrend()).containsEntry(LocalDate.now().toString(), 2L);
        verify(alarmEventService).countStats(eq(CURRENT_USER_ID), any(), any());
        verify(alarmEventService).trendStats(eq(CURRENT_USER_ID), any(), any());
    }
}
