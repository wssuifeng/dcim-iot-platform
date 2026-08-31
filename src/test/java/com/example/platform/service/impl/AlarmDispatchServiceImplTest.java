package com.example.platform.service.impl;

import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.AlarmNotificationLog;
import com.example.platform.entity.SysUser;
import com.example.platform.event.AlarmEventTriggeredEvent;
import com.example.platform.mapper.AlarmNotificationLogMapper;
import com.example.platform.mapper.SysUserMapper;
import com.example.platform.security.UserScopedWebSocketPublisher;
import com.example.platform.service.AlarmEmailSender;
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.AlarmRecipientResolver;
import com.example.platform.service.InAppNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:AlarmDispatchServiceImpl 单元测试
 * 覆盖：在线仅WS/离线邮件兜底（D23）、邮件开关关闭记失败日志（不静默）、
 *   WS单人失败继续其余、无收件人仍写站内
 * */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AlarmDispatchService 单元测试")
class AlarmDispatchServiceImplTest {

    @Mock
    private AlarmEventService alarmEventService;
    @Mock
    private AlarmRecipientResolver alarmRecipientResolver;
    @Mock
    private SimpUserRegistry simpUserRegistry;
    @Mock
    private UserScopedWebSocketPublisher webSocketPublisher;
    @Mock
    private AlarmEmailSender alarmEmailSender;
    @Mock
    private InAppNotificationService inAppNotificationService;
    @Mock
    private AlarmNotificationLogMapper notificationLogMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private com.example.platform.service.AlarmWebhookDispatcher webhookDispatcher;
    @Mock
    private com.example.platform.service.AlarmActionWebhookService actionWebhookService;

    @InjectMocks
    private AlarmDispatchServiceImpl dispatchService;

    private AlarmEvent event() {
        AlarmEvent event = new AlarmEvent();
        event.setId(1L);
        event.setDeviceId(2);
        event.setAlarmType("METRIC");
        event.setAlarmMessage("高温警告");
        event.setState("TRIGGERED");
        event.setCloudRoomId(10L);
        return event;
    }

    private SysUser user(long id, String email) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    @BeforeEach
    void setUp() {
        when(alarmEventService.getById(anyLong())).thenReturn(event());
        when(notificationLogMapper.insert(any(AlarmNotificationLog.class))).thenReturn(1);
    }

    @Nested
    @DisplayName("dispatch - 产生/升级分发")
    class Dispatch {

        @Test
        @DisplayName("在线用户走 WS、离线用户走邮件（D23 逐人兜底）")
        void should_pushWs_online_andEmail_offline() {
            ReflectionTestUtils.setField(dispatchService, "mailEnabled", true);
            when(alarmRecipientResolver.resolveRecipientUserIds())
                    .thenReturn(Set.of(20L, 30L));
            when(simpUserRegistry.getUser("20")).thenReturn(new org.springframework.messaging.simp.user.SimpUser() {
                @Override public String getName() { return "20"; }
                @Override public java.security.Principal getPrincipal() { return () -> "20"; }
                @Override public java.util.Set<org.springframework.messaging.simp.user.SimpSession> getSessions() { return java.util.Set.of(); }
                @Override public boolean hasSessions() { return true; }
                @Override public org.springframework.messaging.simp.user.SimpSession getSession(String sessionId) { return null; }
            });
            when(sysUserMapper.selectBatchIds(anyCollection()))
                    .thenReturn(List.of(user(30L, "offline@demo.local")));

            dispatchService.dispatch(new AlarmEventTriggeredEvent(1L, "CREATED", null));

            verify(webSocketPublisher).sendSummary(eq(20L), any());
            verify(webSocketPublisher, never()).sendSummary(eq(30L), any());
            verify(alarmEmailSender).send(any(AlarmEvent.class), eq(List.of("offline@demo.local")));
            verify(inAppNotificationService).notifyAlarmEvent(eq(1L), eq(Set.of(20L, 30L)), anyString(), anyString());
        }

        @Test
        @DisplayName("邮件开关关闭时记失败日志（不静默）且不调用发送器")
        void should_logFailed_whenMailDisabled() {
            ReflectionTestUtils.setField(dispatchService, "mailEnabled", false);
            when(alarmRecipientResolver.resolveRecipientUserIds()).thenReturn(Set.of(30L));
            when(sysUserMapper.selectBatchIds(anyCollection()))
                    .thenReturn(List.of(user(30L, "offline@demo.local")));

            dispatchService.dispatch(new AlarmEventTriggeredEvent(1L, "CREATED", null));

            verify(alarmEmailSender, never()).send(any(), any());
            ArgumentCaptor<AlarmNotificationLog> captor = ArgumentCaptor.forClass(AlarmNotificationLog.class);
            verify(notificationLogMapper).insert(captor.capture());
            assertThat(captor.getValue().getChannel()).isEqualTo("EMAIL");
            assertThat(captor.getValue().getStatus()).isEqualTo(2);
            assertThat(captor.getValue().getErrorMessage()).contains("邮件通道未启用");
        }

        @Test
        @DisplayName("WS 单人推送失败仅记日志，不影响其余通道与人")
        void should_continue_whenOneWsPushFails() {
            when(alarmRecipientResolver.resolveRecipientUserIds()).thenReturn(Set.of(20L));
            when(simpUserRegistry.getUser("20")).thenReturn(null);
            when(sysUserMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

            dispatchService.dispatch(new AlarmEventTriggeredEvent(1L, "CREATED", null));

            // 20L 离线且无邮箱 → 邮件空收件失败日志，但站内通知仍写
            verify(inAppNotificationService).notifyAlarmEvent(eq(1L), eq(Set.of(20L)), anyString(), anyString());
        }

        @Test
        @DisplayName("无收件人时仅记失败日志不发通知给空集")
        void should_handleEmptyRecipients() {
            when(alarmRecipientResolver.resolveRecipientUserIds()).thenReturn(Set.of());

            dispatchService.dispatch(new AlarmEventTriggeredEvent(1L, "CREATED", null));

            verify(webSocketPublisher, never()).sendSummary(any(), any());
            verify(alarmEmailSender, never()).send(any(), any());
            verify(notificationLogMapper).insert(any(AlarmNotificationLog.class));
        }

        @Test
        @DisplayName("分发整体异常不外抛（T3 容错）")
        void should_swallowAnyException() {
            when(alarmRecipientResolver.resolveRecipientUserIds())
                    .thenThrow(new IllegalStateException("resolver down"));

            dispatchService.dispatch(new AlarmEventTriggeredEvent(1L, "CREATED", null));
            // 不抛异常即通过
        }
    }
}
