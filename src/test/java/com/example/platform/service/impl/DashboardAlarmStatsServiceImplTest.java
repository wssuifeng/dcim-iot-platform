package com.example.platform.service.impl;

import com.example.platform.dto.AlarmStatsVO;
import com.example.platform.dto.DashboardAlarmStatsVO;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.DataScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:DashboardAlarmStatsServiceImpl 单元测试，验证范围描述与告警事件聚合服务委托
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardAlarmStatsService 单元测试")
class DashboardAlarmStatsServiceImplTest {

    @Mock
    private AlarmEventService alarmEventService;
    @Mock
    private DataScopeService dataScopeService;

    @InjectMocks
    private DashboardAlarmStatsServiceImpl dashboardAlarmStatsService;

    private static final Long CURRENT_USER_ID = 60L;

    @Test
    @DisplayName("按授权范围委托告警统计与趋势聚合")
    void should_delegateScopedAggregation() {
        DataScopeContext context = DataScopeContext.builder()
                .userId(CURRENT_USER_ID)
                .role("VIEWER")
                .organizationIds(Set.of(1L))
                .scopeType("READ_ONLY")
                .build();
        AlarmStatsVO stats = new AlarmStatsVO();
        stats.setStateDistribution(Map.of("TRIGGERED", 2L));
        stats.setLevelDistribution(Map.of("L2", 2L));
        stats.setDeviceRanking(List.of(Map.of("deviceId", 5, "alarmCount", 2L)));
        when(dataScopeService.resolveDataScope(CURRENT_USER_ID)).thenReturn(context);
        when(alarmEventService.countStats(eq(CURRENT_USER_ID), any(), any())).thenReturn(stats);
        when(alarmEventService.trendStats(eq(CURRENT_USER_ID), any(), any()))
                .thenReturn(Map.of(LocalDate.now().toString(), 2L));

        DashboardAlarmStatsVO result = dashboardAlarmStatsService.getAlarmDashboardStats(CURRENT_USER_ID);

        assertThat(result.getScopeDescription()).contains("READ_ONLY");
        assertThat(result.getStateDistribution()).containsEntry("TRIGGERED", 2L);
        assertThat(result.getLevelDistribution()).containsEntry("L2", 2L);
        assertThat(result.getDeviceRanking()).hasSize(1);
        verify(alarmEventService).countStats(eq(CURRENT_USER_ID), any(), any());
        verify(alarmEventService).trendStats(eq(CURRENT_USER_ID), any(), any());
    }

    @Test
    @DisplayName("无组织授权返回空聚合且不访问告警服务")
    void should_returnEmptyWithoutOrganization() {
        DataScopeContext context = DataScopeContext.builder()
                .userId(CURRENT_USER_ID)
                .role("VIEWER")
                .organizationIds(Set.of())
                .scopeType("READ_ONLY")
                .build();
        when(dataScopeService.resolveDataScope(CURRENT_USER_ID)).thenReturn(context);

        DashboardAlarmStatsVO result = dashboardAlarmStatsService.getAlarmDashboardStats(CURRENT_USER_ID);

        assertThat(result.getStateDistribution()).isEmpty();
        assertThat(result.getLevelDistribution()).isEmpty();
        assertThat(result.getDeviceRanking()).isEmpty();
        assertThat(result.getDailyTrend()).hasSize(7);
    }

    @Test
    @DisplayName("平台管理员可以委托全量聚合")
    void should_allowPlatformAdmin() {
        DataScopeContext context = DataScopeContext.builder()
                .userId(CURRENT_USER_ID)
                .role("PLATFORM_ADMIN")
                .organizationIds(Set.of())
                .scopeType("PLATFORM")
                .build();
        AlarmStatsVO stats = new AlarmStatsVO();
        stats.setStateDistribution(Map.of("CLOSED", 1L));
        stats.setLevelDistribution(Map.of());
        stats.setDeviceRanking(List.of());
        when(dataScopeService.resolveDataScope(CURRENT_USER_ID)).thenReturn(context);
        when(alarmEventService.countStats(eq(CURRENT_USER_ID), any(), any())).thenReturn(stats);
        when(alarmEventService.trendStats(eq(CURRENT_USER_ID), any(), any())).thenReturn(Map.of());

        DashboardAlarmStatsVO result = dashboardAlarmStatsService.getAlarmDashboardStats(CURRENT_USER_ID);

        assertThat(result.getScopeDescription()).isEqualTo("PLATFORM: 全量");
        assertThat(result.getStateDistribution()).containsEntry("CLOSED", 1L);
    }
}
