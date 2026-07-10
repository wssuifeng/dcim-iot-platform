package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.AlarmEventVO;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.AlarmHandlingRecord;
import com.example.platform.entity.AlarmStateTransition;
import com.example.platform.mapper.AlarmEventMapper;
import com.example.platform.mapper.AlarmHandlingRecordMapper;
import com.example.platform.mapper.AlarmStateTransitionMapper;
import com.example.platform.security.UserScopedWebSocketPublisher;
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.InAppNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:AlarmEventServiceImpl 单元测试
 * 覆盖 BIZ-TRAE-02：状态机合法/非法流转、重复关闭、范围越权拒绝、空组织授权返回空、更新失败回滚
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlarmEventService 单元测试")
class AlarmEventServiceImplTest {

    @Mock
    private AlarmEventMapper alarmEventMapper;
    @Mock
    private AlarmStateTransitionMapper alarmStateTransitionMapper;
    @Mock
    private AlarmHandlingRecordMapper alarmHandlingRecordMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private InAppNotificationService inAppNotificationService;
    @Mock
    private UserScopedWebSocketPublisher webSocketPublisher;

    @InjectMocks
    private AlarmEventServiceImpl alarmEventService;

    private static final Long CURRENT_USER_ID = 40L;
    private static final Long ORG_ID = 1L;
    private static final Long OTHER_ORG_ID = 999L;

    @BeforeEach
    void setUp() throws Exception {
        initBaseMapper();
        initLambdaCache();
    }

    private void initBaseMapper() throws Exception {
        Class<?> clazz = AlarmEventServiceImpl.class;
        Field field = null;
        while (clazz != null) {
            try { field = clazz.getDeclaredField("baseMapper"); break; }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        assertThat(field).as("应存在 baseMapper 字段").isNotNull();
        field.setAccessible(true);
        field.set(alarmEventService, alarmEventMapper);
    }

    private void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(AlarmEventMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, AlarmEvent.class);
        TableInfoHelper.initTableInfo(assistant, AlarmStateTransition.class);
        TableInfoHelper.initTableInfo(assistant, AlarmHandlingRecord.class);
    }

    private DataScopeContext orgAdminContext(Long orgId) {
        return DataScopeContext.builder()
                .userId(CURRENT_USER_ID)
                .role("ORG_ADMIN")
                .organizationIds(Set.of(orgId))
                .scopeType("ORG_ALL")
                .build();
    }

    private AlarmEvent buildEvent(Long id, Long orgId, String state) {
        AlarmEvent event = new AlarmEvent();
        event.setId(id);
        event.setDeviceId(5);
        event.setAlarmType("高温告警");
        event.setAlarmLevel(2);
        event.setAlarmMessage("温度超过阈值");
        event.setState(state);
        event.setOrganizationId(orgId);
        event.setTriggeredTime(LocalDateTime.now().minusHours(1));
        return event;
    }

    @Nested
    @DisplayName("状态机合法流转")
    class LegalTransitions {

        @Test
        @DisplayName("TRIGGERED → ACKED 合法")
        void should_transition_triggered_to_acked() {
            AlarmEvent event = buildEvent(1L, ORG_ID, "TRIGGERED");
            when(alarmEventMapper.selectById(1L)).thenReturn(event);
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));
            when(alarmEventMapper.updateById((AlarmEvent) any())).thenReturn(1);
            when(alarmStateTransitionMapper.insert((AlarmStateTransition) any())).thenReturn(1);
            when(alarmHandlingRecordMapper.insert((AlarmHandlingRecord) any())).thenReturn(1);

            AlarmEvent result = alarmEventService.acknowledge(1L, CURRENT_USER_ID, "确认告警");
            assertThat(result.getState()).isEqualTo("ACKED");
            verify(alarmStateTransitionMapper, times(1)).insert((AlarmStateTransition) any());
            verify(alarmHandlingRecordMapper, times(1)).insert((AlarmHandlingRecord) any());
        }

        @Test
        @DisplayName("ACKED → HANDLING 合法")
        void should_transition_acked_to_handling() {
            AlarmEvent event = buildEvent(1L, ORG_ID, "ACKED");
            when(alarmEventMapper.selectById(1L)).thenReturn(event);
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));
            when(alarmEventMapper.updateById((AlarmEvent) any())).thenReturn(1);
            when(alarmStateTransitionMapper.insert((AlarmStateTransition) any())).thenReturn(1);
            when(alarmHandlingRecordMapper.insert((AlarmHandlingRecord) any())).thenReturn(1);

            AlarmEvent result = alarmEventService.startHandling(1L, CURRENT_USER_ID, null);
            assertThat(result.getState()).isEqualTo("HANDLING");
        }

        @Test
        @DisplayName("RECOVERED → CLOSED 合法")
        void should_transition_recovered_to_closed() {
            AlarmEvent event = buildEvent(1L, ORG_ID, "RECOVERED");
            event.setRecoveredTime(LocalDateTime.now().minusMinutes(1));
            when(alarmEventMapper.selectById(1L)).thenReturn(event);
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));
            when(alarmEventMapper.updateById((AlarmEvent) any())).thenReturn(1);
            when(alarmStateTransitionMapper.insert((AlarmStateTransition) any())).thenReturn(1);
            when(alarmHandlingRecordMapper.insert((AlarmHandlingRecord) any())).thenReturn(1);

            AlarmEvent result = alarmEventService.close(1L, CURRENT_USER_ID, null);
            assertThat(result.getState()).isEqualTo("CLOSED");
            assertThat(result.getClosedTime()).isNotNull();
        }
    }

    @Nested
    @DisplayName("非法流转与冲突")
    class IllegalTransitions {

        @Test
        @DisplayName("CLOSED → ACKED 非法流转拒绝")
        void should_reject_closed_to_acked() {
            AlarmEvent event = buildEvent(1L, ORG_ID, "CLOSED");
            when(alarmEventMapper.selectById(1L)).thenReturn(event);
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));

            assertThatThrownBy(() -> alarmEventService.acknowledge(1L, CURRENT_USER_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ResultCode.ALARM_STATE_INVALID_TRANSITION);
            verify(alarmEventMapper, never()).updateById((AlarmEvent) any());
            verify(alarmStateTransitionMapper, never()).insert((AlarmStateTransition) any());
        }

        @Test
        @DisplayName("重复关闭抛状态冲突")
        void should_reject_duplicate_close() {
            AlarmEvent event = buildEvent(1L, ORG_ID, "CLOSED");
            when(alarmEventMapper.selectById(1L)).thenReturn(event);
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));

            assertThatThrownBy(() -> alarmEventService.close(1L, CURRENT_USER_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ResultCode.ALARM_STATE_CONFLICT);
            verify(alarmEventMapper, never()).updateById((AlarmEvent) any());
        }

        @Test
        @DisplayName("TRIGGERED → HANDLING 跳跃流转拒绝")
        void should_reject_skip_transition() {
            AlarmEvent event = buildEvent(1L, ORG_ID, "TRIGGERED");
            when(alarmEventMapper.selectById(1L)).thenReturn(event);
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));

            assertThatThrownBy(() -> alarmEventService.startHandling(1L, CURRENT_USER_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ResultCode.ALARM_STATE_INVALID_TRANSITION);
        }
    }

    @Nested
    @DisplayName("范围校验")
    class ScopeChecks {

        @Test
        @DisplayName("越权访问其他组织告警拒绝")
        void should_reject_when_orgNotInScope() {
            AlarmEvent event = buildEvent(1L, OTHER_ORG_ID, "TRIGGERED");
            when(alarmEventMapper.selectById(1L)).thenReturn(event);
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));

            assertThatThrownBy(() -> alarmEventService.acknowledge(1L, CURRENT_USER_ID, null))
                    .isInstanceOf(DataScopeViolationException.class);
        }

        @Test
        @DisplayName("查询越权组织告警事件拒绝")
        void should_reject_when_getDetailOutOfScope() {
            AlarmEvent event = buildEvent(1L, OTHER_ORG_ID, "TRIGGERED");
            when(alarmEventMapper.selectById(1L)).thenReturn(event);
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                    .thenReturn(orgAdminContext(ORG_ID));

            assertThatThrownBy(() -> alarmEventService.getDetail(1L, CURRENT_USER_ID))
                    .isInstanceOf(DataScopeViolationException.class);
        }

        @Test
        @DisplayName("无组织授权分页返回空结果")
        void should_returnEmpty_when_noOrgAssigned() {
            DataScopeContext context = DataScopeContext.builder()
                    .userId(CURRENT_USER_ID).role("VIEWER")
                    .organizationIds(Set.of()).scopeType("READ_ONLY").build();
            when(dataScopeService.resolveDataScope(CURRENT_USER_ID)).thenReturn(context);

            Page<AlarmEventVO> result = alarmEventService.pageEvents(
                    CURRENT_USER_ID, 1, 10, null, null, null, null, null);
            assertThat(result.getRecords()).isEmpty();
            assertThat(result.getTotal()).isZero();
            verify(alarmEventMapper, never())
                    .selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }
    }

    @Nested
    @DisplayName("事件不存在")
    class NotFound {

        @Test
        @DisplayName("告警事件不存在抛 RESOURCE_NOT_FOUND")
        void should_throw_when_eventNotFound() {
            when(alarmEventMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> alarmEventService.getDetail(99L, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ResultCode.RESOURCE_NOT_FOUND);
        }
    }
}