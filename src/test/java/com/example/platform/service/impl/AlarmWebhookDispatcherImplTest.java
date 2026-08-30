package com.example.platform.service.impl;

import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.AlarmNotificationLog;
import com.example.platform.entity.AlarmWebhookChannel;
import com.example.platform.mapper.AlarmNotificationLogMapper;
import com.example.platform.mapper.AlarmWebhookChannelMapper;
import com.example.platform.service.WebhookSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:AlarmWebhookDispatcherImpl 单元测试
 * 覆盖：无启用渠道跳过、max_alarm_level 过滤（含边界）、成功写日志、
 *   失败重试 4 次后逐次写失败日志、业务码失败同样触发重试
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlarmWebhookDispatcher 分发器测试")
class AlarmWebhookDispatcherImplTest {

    @Mock
    private AlarmWebhookChannelMapper channelMapper;
    @Mock
    private WebhookSender webhookSender;
    @Mock
    private AlarmNotificationLogMapper notificationLogMapper;

    private AlarmWebhookDispatcherImpl dispatcher;

    @BeforeEach
    void setUp() {
        WebhookMessageRenderer renderer = new WebhookMessageRenderer(new tools.jackson.databind.ObjectMapper());
        dispatcher = new AlarmWebhookDispatcherImpl(channelMapper, renderer, webhookSender,
                notificationLogMapper);
        dispatcher.retryBackoffMs = new long[]{0L, 0L, 0L};
    }

    private AlarmWebhookChannel channel(String type, int maxLevel) {
        AlarmWebhookChannel channel = new AlarmWebhookChannel();
        channel.setId(1L);
        channel.setName("运维群");
        channel.setChannelType(type);
        channel.setWebhookUrl("https://example.com/hook");
        channel.setCredentialJson(null);
        channel.setMaxAlarmLevel(maxLevel);
        channel.setEnabled(1);
        return channel;
    }

    private AlarmEvent event(int level) {
        AlarmEvent event = new AlarmEvent();
        event.setId(9L);
        event.setAlarmType("METRIC");
        event.setAlarmLevel(level);
        event.setState("TRIGGERED");
        event.setAlarmMessage("高温警告：温度=45.2℃");
        event.setTriggeredTime(LocalDateTime.now());
        return event;
    }

    @Test
    @DisplayName("无启用渠道：不发送")
    void should_skip_whenNoEnabledChannels() {
        when(channelMapper.selectList(any())).thenReturn(List.of());

        dispatcher.dispatch(event(2), "ALARM_CREATED");

        verify(webhookSender, never()).send(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("max_alarm_level 过滤：级别数字大于上限不推送，等于上限推送")
    void should_filterByMaxAlarmLevel() {
        when(channelMapper.selectList(any())).thenReturn(List.of(channel(AlarmWebhookChannel.TYPE_WECOM, 3)));
        when(webhookSender.send(anyString(), any(), anyString()))
                .thenReturn("{\"errcode\":0}");

        dispatcher.dispatch(event(4), "ALARM_CREATED");
        verify(webhookSender, never()).send(anyString(), any(), anyString());
        verify(notificationLogMapper, never()).insert(any(AlarmNotificationLog.class));

        dispatcher.dispatch(event(3), "ALARM_CREATED");
        verify(webhookSender, times(1)).send(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("发送成功：写一行成功日志")
    void should_logSuccess_onFirstAttempt() {
        when(channelMapper.selectList(any())).thenReturn(List.of(channel(AlarmWebhookChannel.TYPE_WECOM, 5)));
        when(webhookSender.send(anyString(), any(), anyString()))
                .thenReturn("{\"errcode\":0,\"errmsg\":\"ok\"}");

        dispatcher.dispatch(event(2), "ALARM_CREATED");

        ArgumentCaptor<AlarmNotificationLog> captor = ArgumentCaptor.forClass(AlarmNotificationLog.class);
        verify(notificationLogMapper).insert(captor.capture());
        AlarmNotificationLog logEntry = captor.getValue();
        assertThat(logEntry.getStatus()).isEqualTo(1);
        assertThat(logEntry.getChannel()).isEqualTo("WEBHOOK_WECOM");
        assertThat(logEntry.getRecipient()).isEqualTo("运维群");
        assertThat(logEntry.getSentTime()).isNotNull();
    }

    @Test
    @DisplayName("连接失败：退避重试 4 次后逐次写失败日志")
    void should_retryAndLogFailures_whenTargetDown() {
        when(channelMapper.selectList(any())).thenReturn(List.of(channel(AlarmWebhookChannel.TYPE_WECOM, 5)));
        when(webhookSender.send(anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("connect timeout"));

        dispatcher.dispatch(event(2), "ALARM_CREATED");

        verify(webhookSender, times(4)).send(anyString(), any(), anyString());
        ArgumentCaptor<AlarmNotificationLog> captor = ArgumentCaptor.forClass(AlarmNotificationLog.class);
        verify(notificationLogMapper, times(4)).insert(captor.capture());
        List<AlarmNotificationLog> logs = captor.getAllValues();
        assertThat(logs).allMatch(log -> log.getStatus() == 2);
        assertThat(logs.get(0).getErrorMessage()).contains("第1次");
        assertThat(logs.get(3).getErrorMessage()).contains("最终失败");
    }

    @Test
    @DisplayName("HTTP 200 但业务码失败（钉钉关键词不匹配）同样触发重试")
    void should_retry_whenBusinessCodeFails() {
        when(channelMapper.selectList(any())).thenReturn(List.of(channel(AlarmWebhookChannel.TYPE_DINGTALK, 5)));
        when(webhookSender.send(anyString(), any(), anyString()))
                .thenReturn("{\"errcode\":310000,\"errmsg\":\"keyword not in content\"}");

        dispatcher.dispatch(event(2), "ALARM_CREATED");

        verify(webhookSender, times(4)).send(anyString(), any(), anyString());
        verify(notificationLogMapper, times(4)).insert(any(AlarmNotificationLog.class));
    }

    @Test
    @DisplayName("恢复事件推送：IM 文本含事件类型与恢复提示")
    void should_dispatchRecoveredEvent() {
        AlarmEvent recovered = event(3);
        recovered.setState("RECOVERED");
        when(channelMapper.selectList(any())).thenReturn(List.of(channel(AlarmWebhookChannel.TYPE_WECOM, 5)));
        when(webhookSender.send(anyString(), any(), anyString()))
                .thenReturn("{\"errcode\":0}");

        dispatcher.dispatch(recovered, "ALARM_RECOVERED");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(webhookSender).send(anyString(), any(), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("ALARM_RECOVERED").contains("已恢复");
        assertThat(bodyCaptor.getValue()).contains("高温警告");
    }
}
