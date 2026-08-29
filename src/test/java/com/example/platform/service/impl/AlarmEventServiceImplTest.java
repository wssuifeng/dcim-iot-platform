package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.AlarmHandlingRecord;
import com.example.platform.entity.AlarmStateTransition;
import com.example.platform.mapper.AlarmEventMapper;
import com.example.platform.mapper.AlarmHandlingRecordMapper;
import com.example.platform.mapper.AlarmStateTransitionMapper;
import com.example.platform.service.AlarmRecipientResolver;
import com.example.platform.service.InAppNotificationService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:AlarmEventServiceImpl 单元测试
 * 覆盖 BIZ-TRAE-02：状态机合法/非法流转、重复关闭、更新失败回滚、站内通知收件人解析
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
    private InAppNotificationService inAppNotificationService;
    @Mock
    private AlarmRecipientResolver alarmRecipientResolver;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock
    private com.example.platform.service.DeviceService deviceService;
    @Mock
    private com.example.platform.service.PointDefinitionService pointDefinitionService;

    @InjectMocks
    private AlarmEventServiceImpl alarmEventService;

    private static final Long CURRENT_USER_ID = 40L;
    private static final Long CLOUD_ROOM_ID = 10L;

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

    private AlarmEvent buildEvent(Long id, String state) {
        AlarmEvent event = new AlarmEvent();
        event.setId(id);
        event.setDeviceId(5);
        event.setAlarmType("高温告警");
        event.setAlarmLevel(2);
        event.setAlarmMessage("温度超过阈值");
        event.setState(state);
        event.setTriggeredTime(LocalDateTime.now().minusHours(1));
        return event;
    }

    @Nested
    @DisplayName("状态机合法流转")
    class LegalTransitions {

        @Test
        @DisplayName("TRIGGERED → ACKED 合法")
        void should_transition_triggered_to_acked() {
            AlarmEvent event = buildEvent(1L, "TRIGGERED");
            when(alarmEventMapper.selectById(1L)).thenReturn(event);
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
            AlarmEvent event = buildEvent(1L, "ACKED");
            when(alarmEventMapper.selectById(1L)).thenReturn(event);
            when(alarmEventMapper.updateById((AlarmEvent) any())).thenReturn(1);
            when(alarmStateTransitionMapper.insert((AlarmStateTransition) any())).thenReturn(1);
            when(alarmHandlingRecordMapper.insert((AlarmHandlingRecord) any())).thenReturn(1);

            AlarmEvent result = alarmEventService.startHandling(1L, CURRENT_USER_ID, null);
            assertThat(result.getState()).isEqualTo("HANDLING");
        }

        @Test
        @DisplayName("RECOVERED → CLOSED 合法")
        void should_transition_recovered_to_closed() {
            AlarmEvent event = buildEvent(1L, "RECOVERED");
            event.setRecoveredTime(LocalDateTime.now().minusMinutes(1));
            when(alarmEventMapper.selectById(1L)).thenReturn(event);
            when(alarmEventMapper.updateById((AlarmEvent) any())).thenReturn(1);
            when(alarmStateTransitionMapper.insert((AlarmStateTransition) any())).thenReturn(1);
            when(alarmHandlingRecordMapper.insert((AlarmHandlingRecord) any())).thenReturn(1);

            AlarmEvent result = alarmEventService.close(1L, CURRENT_USER_ID, null);
            assertThat(result.getState()).isEqualTo("CLOSED");
            assertThat(result.getClosedTime()).isNotNull();
        }

        @Test
        @DisplayName("人工流转站内通知发给解析出的启用运维角色收件人（WS推送职责已移交分发服务）")
        void should_dispatch_to_resolved_recipients() {
            AlarmEvent event = buildEvent(1L, "TRIGGERED");
            event.setCloudRoomId(CLOUD_ROOM_ID);
            when(alarmEventMapper.selectById(1L)).thenReturn(event);
            when(alarmEventMapper.updateById((AlarmEvent) any())).thenReturn(1);
            when(alarmStateTransitionMapper.insert((AlarmStateTransition) any())).thenReturn(1);
            when(alarmHandlingRecordMapper.insert((AlarmHandlingRecord) any())).thenReturn(1);
            when(alarmRecipientResolver.resolveRecipientUserIds())
                    .thenReturn(Set.of(20L, 30L));

            alarmEventService.acknowledge(1L, CURRENT_USER_ID, null);

            verify(inAppNotificationService).notifyAlarmEvent(
                    eq(1L), eq(Set.of(20L, 30L)), any(String.class), any(String.class));
        }

        @Test
        @DisplayName("站内通知发送异常不阻断状态流转")
        void should_continue_after_notification_failure() {
            AlarmEvent event = buildEvent(1L, "TRIGGERED");
            event.setCloudRoomId(CLOUD_ROOM_ID);
            when(alarmEventMapper.selectById(1L)).thenReturn(event);
            when(alarmEventMapper.updateById((AlarmEvent) any())).thenReturn(1);
            when(alarmStateTransitionMapper.insert((AlarmStateTransition) any())).thenReturn(1);
            when(alarmHandlingRecordMapper.insert((AlarmHandlingRecord) any())).thenReturn(1);
            when(alarmRecipientResolver.resolveRecipientUserIds())
                    .thenReturn(new java.util.LinkedHashSet<>(List.of(20L, 30L)));
            org.mockito.Mockito.doThrow(new IllegalStateException("notify down"))
                    .when(inAppNotificationService)
                    .notifyAlarmEvent(eq(1L), any(), any(String.class), any(String.class));

            AlarmEvent result = alarmEventService.acknowledge(1L, CURRENT_USER_ID, null);

            assertThat(result.getState()).isEqualTo("ACKED");
        }
    }

    @Nested
    @DisplayName("非法流转与冲突")
    class IllegalTransitions {

        @Test
        @DisplayName("CLOSED → ACKED 非法流转拒绝")
        void should_reject_closed_to_acked() {
            AlarmEvent event = buildEvent(1L, "CLOSED");
            when(alarmEventMapper.selectById(1L)).thenReturn(event);

            assertThatThrownBy(() -> alarmEventService.acknowledge(1L, CURRENT_USER_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ResultCode.ALARM_STATE_INVALID_TRANSITION);
            verify(alarmEventMapper, org.mockito.Mockito.never()).updateById((AlarmEvent) any());
            verify(alarmStateTransitionMapper, org.mockito.Mockito.never()).insert((AlarmStateTransition) any());
        }

        @Test
        @DisplayName("重复关闭抛状态冲突")
        void should_reject_duplicate_close() {
            AlarmEvent event = buildEvent(1L, "CLOSED");
            when(alarmEventMapper.selectById(1L)).thenReturn(event);

            assertThatThrownBy(() -> alarmEventService.close(1L, CURRENT_USER_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ResultCode.ALARM_STATE_CONFLICT);
            verify(alarmEventMapper, org.mockito.Mockito.never()).updateById((AlarmEvent) any());
        }

        @Test
        @DisplayName("TRIGGERED → HANDLING 跳跃流转拒绝")
        void should_reject_skip_transition() {
            AlarmEvent event = buildEvent(1L, "TRIGGERED");
            when(alarmEventMapper.selectById(1L)).thenReturn(event);

            assertThatThrownBy(() -> alarmEventService.startHandling(1L, CURRENT_USER_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ResultCode.ALARM_STATE_INVALID_TRANSITION);
        }
    }

    @Nested
    @DisplayName("事件不存在")
    class NotFound {

        @Test
        @DisplayName("告警事件不存在抛 ALARM_EVENT_NOT_FOUND(4041)")
        void should_throw_when_eventNotFound() {
            when(alarmEventMapper.selectById(99L)).thenReturn(null);

            assertThatThrownBy(() -> alarmEventService.getDetail(99L, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ResultCode.ALARM_EVENT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("自动恢复")
    class AutoRecover {

        @Test
        @DisplayName("网关级告警（device_id 为空）可自动恢复：查询条件走 IS NULL 而非 eq null")
        void should_autoRecover_withNullDeviceId() {
            AlarmEvent event = buildEvent(92L, "ACKED");
            event.setDeviceId(null);
            when(alarmEventMapper.selectOne(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(event);
            when(alarmEventMapper.updateById((AlarmEvent) any())).thenReturn(1);
            when(alarmStateTransitionMapper.insert((AlarmStateTransition) any())).thenReturn(1);

            boolean recovered = alarmEventService.autoRecover(null, null, "GATEWAY_OFFLINE");

            assertThat(recovered).isTrue();
            org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<AlarmEvent>> captor =
                    org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
            verify(alarmEventMapper).selectOne(captor.capture(), org.mockito.ArgumentMatchers.eq(false));
            // device_id/point_id 双 null 必须生成 IS NULL（eq null 在 SQL 中恒不匹配）
            String sql = captor.getValue().getSqlSegment();
            assertThat(sql).contains("IS NULL");
            verify(alarmEventMapper).updateById((AlarmEvent) any());
        }

        @Test
        @DisplayName("无活动告警时自动恢复返回 false")
        void should_returnFalse_whenNoActiveEvent() {
            when(alarmEventMapper.selectOne(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(null);

            assertThat(alarmEventService.autoRecover(null, null, "GATEWAY_OFFLINE")).isFalse();
        }
    }
}
