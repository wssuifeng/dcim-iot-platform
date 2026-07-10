package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.dto.DashboardAlarmStatsVO;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.AlarmEvent;
import com.example.platform.mapper.AlarmEventMapper;
import com.example.platform.service.DashboardAlarmStatsService;
import com.example.platform.service.DataScopeService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:DashboardAlarmStatsServiceImpl 单元测试
 * 覆盖 BIZ-TRAE-02：聚合范围过滤（不跨组织泄露）、状态分布、级别分布、近 7 日趋势、设备排名
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardAlarmStatsService 单元测试")
class DashboardAlarmStatsServiceImplTest {

    @Mock
    private AlarmEventMapper alarmEventMapper;
    @Mock
    private DataScopeService dataScopeService;

    @InjectMocks
    private DashboardAlarmStatsServiceImpl dashboardAlarmStatsService;

    private static final Long CURRENT_USER_ID = 60L;
    private static final Long ORG_ID = 1L;
    private static final Long OTHER_ORG_ID = 999L;

    @BeforeEach
    void setUp() throws Exception {
        initLambdaCache();
    }

    private void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(AlarmEventMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, AlarmEvent.class);
    }

    private DataScopeContext orgAdminContext(Long orgId) {
        return DataScopeContext.builder()
                .userId(CURRENT_USER_ID)
                .role("ORG_ADMIN")
                .organizationIds(Set.of(orgId))
                .scopeType("ORG_ALL")
                .build();
    }

    private DataScopeContext viewerContext(Long orgId) {
        return DataScopeContext.builder()
                .userId(CURRENT_USER_ID)
                .role("VIEWER")
                .organizationIds(Set.of(orgId))
                .scopeType("READ_ONLY")
                .build();
    }

    private AlarmEvent buildEvent(Long id, Long orgId, String state, Integer level, Integer deviceId, LocalDateTime triggeredTime) {
        AlarmEvent event = new AlarmEvent();
        event.setId(id);
        event.setDeviceId(deviceId);
        event.setAlarmType("test");
        event.setAlarmLevel(level);
        event.setState(state);
        event.setOrganizationId(orgId);
        event.setTriggeredTime(triggeredTime);
        return event;
    }

    @Nested
    @DisplayName("聚合范围过滤")
    class ScopeFiltering {

        @Test
        @DisplayName("VIEWER 只能看到授权组织数据，不跨组织泄露")
        void should_onlyShowAuthorizedOrg_for_viewer() {
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(viewerContext(ORG_ID));
            AlarmEvent scopedEvent = buildEvent(1L, ORG_ID, "TRIGGERED", 2, 5, LocalDateTime.now());
            when(alarmEventMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(scopedEvent));

            DashboardAlarmStatsVO vo = dashboardAlarmStatsService.getAlarmDashboardStats(CURRENT_USER_ID);
            assertThat(vo.getStateDistribution()).containsEntry("TRIGGERED", 1L);
            assertThat(vo.getLevelDistribution()).containsEntry("L2", 1L);
            // 仅返回授权组织数据
            verify(alarmEventMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("无组织授权返回空聚合")
        void should_returnEmpty_when_noOrgAssigned() {
            DataScopeContext context = DataScopeContext.builder()
                    .userId(CURRENT_USER_ID).role("VIEWER")
                    .organizationIds(Set.of()).scopeType("READ_ONLY").build();
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID)).thenReturn(context);

            DashboardAlarmStatsVO vo = dashboardAlarmStatsService.getAlarmDashboardStats(CURRENT_USER_ID);
            assertThat(vo.getStateDistribution()).isEmpty();
            assertThat(vo.getLevelDistribution()).isEmpty();
            assertThat(vo.getDeviceRanking()).isEmpty();
        }

        @Test
        @DisplayName("PLATFORM_ADMIN 看全量数据")
        void should_seeAll_for_platformAdmin() {
            DataScopeContext context = DataScopeContext.builder()
                    .userId(CURRENT_USER_ID).role("PLATFORM_ADMIN")
                    .organizationIds(Set.of()).scopeType("PLATFORM").build();
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID)).thenReturn(context);
            AlarmEvent e1 = buildEvent(1L, ORG_ID, "TRIGGERED", 1, 5, LocalDateTime.now());
            AlarmEvent e2 = buildEvent(2L, OTHER_ORG_ID, "CLOSED", 3, 6, LocalDateTime.now());
            when(alarmEventMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(e1, e2));

            DashboardAlarmStatsVO vo = dashboardAlarmStatsService.getAlarmDashboardStats(CURRENT_USER_ID);
            assertThat(vo.getStateDistribution()).containsEntry("TRIGGERED", 1L);
            assertThat(vo.getStateDistribution()).containsEntry("CLOSED", 1L);
            assertThat(vo.getDeviceRanking()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("聚合计算正确性")
    class AggregationCorrectness {

        @Test
        @DisplayName("状态分布、级别分布、设备排名聚合正确")
        void should_aggregateCorrectly() {
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));
            AlarmEvent e1 = buildEvent(1L, ORG_ID, "TRIGGERED", 2, 5, LocalDateTime.now());
            AlarmEvent e2 = buildEvent(2L, ORG_ID, "TRIGGERED", 2, 5, LocalDateTime.now().minusHours(1));
            AlarmEvent e3 = buildEvent(3L, ORG_ID, "ACKED", 3, 6, LocalDateTime.now().minusDays(1));
            when(alarmEventMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(e1, e2, e3));

            DashboardAlarmStatsVO vo = dashboardAlarmStatsService.getAlarmDashboardStats(CURRENT_USER_ID);
            assertThat(vo.getStateDistribution()).containsEntry("TRIGGERED", 2L);
            assertThat(vo.getStateDistribution()).containsEntry("ACKED", 1L);
            assertThat(vo.getLevelDistribution()).containsEntry("L2", 2L);
            assertThat(vo.getLevelDistribution()).containsEntry("L3", 1L);
            // 设备 5 出现 2 次，排名首位
            assertThat(vo.getDeviceRanking()).hasSize(2);
            @SuppressWarnings("unchecked")
            Map<String, Object> top = (Map<String, Object>) vo.getDeviceRanking().get(0);
            assertThat(top.get("deviceId")).isEqualTo(5);
            assertThat(top.get("alarmCount")).isEqualTo(2L);
        }

        @Test
        @DisplayName("近 7 日趋势含前 6 天空档")
        void should_includeEmptyDaysInTrend() {
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));
            AlarmEvent recent = buildEvent(1L, ORG_ID, "TRIGGERED", 2, 5, LocalDateTime.now());
            when(alarmEventMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(recent));

            DashboardAlarmStatsVO vo = dashboardAlarmStatsService.getAlarmDashboardStats(CURRENT_USER_ID);
            assertThat(vo.getDailyTrend()).hasSize(7);
            // 今日至少包含 1 条
            String today = java.time.LocalDate.now().toString();
            assertThat(vo.getDailyTrend()).containsEntry(today, 1L);
        }
    }
}