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
import static org.mockito.Mockito.atLeastOnce;
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
    @Mock
    private com.example.platform.ingest.wecom.WecomAibotClient wecomAibotClient;

    private AlarmWebhookDispatcherImpl dispatcher;

    @BeforeEach
    void setUp() {
        WebhookMessageRenderer renderer = new WebhookMessageRenderer(new tools.jackson.databind.ObjectMapper());
        dispatcher = new AlarmWebhookDispatcherImpl(channelMapper, renderer, webhookSender,
                notificationLogMapper, wecomAibotClient);
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

    private AlarmWebhookChannel aibotChannel(int maxLevel) {
        AlarmWebhookChannel channel = channel(AlarmWebhookChannel.TYPE_WECOM_AIBOT, maxLevel);
        channel.setCredentialJson("{\"botId\":\"bot-1\",\"secret\":\"sec-1\",\"chatid\":\"chat-1\"}");
        return channel;
    }

    private String capturedAibotMarkdown() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(wecomAibotClient, atLeastOnce()).sendMarkdown(eq("bot-1"), eq("sec-1"), eq("chat-1"), captor.capture());
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
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

    @Test
    @DisplayName("企微 aibot L1/L2：一级标题 + 紧急/严重 emoji，正文引用块含消息与时间")
    void should_renderAibotMarkdown_severeLevels() {
        when(channelMapper.selectList(any())).thenReturn(List.of(aibotChannel(5)));

        dispatcher.dispatch(event(1), "ALARM_CREATED");
        String l1 = capturedAibotMarkdown();
        assertThat(l1).startsWith("# ").contains("🔴").contains("【L1 紧急】").contains("🚨");
        assertThat(l1).contains("高温警告").contains("事件 #9");

        dispatcher.dispatch(event(2), "ALARM_UPGRADED");
        String l2 = capturedAibotMarkdown();
        assertThat(l2).startsWith("# ").contains("🟠").contains("【L2 严重】").contains("🔺");
    }

    @Test
    @DisplayName("企微 aibot L3：二级标题 + 🟡；L4/L5：引用块轻提示 + 🔵/⚪")
    void should_renderAibotMarkdown_minorLevels() {
        when(channelMapper.selectList(any())).thenReturn(List.of(aibotChannel(5)));

        dispatcher.dispatch(event(3), "ALARM_CREATED");
        String l3 = capturedAibotMarkdown();
        assertThat(l3).startsWith("## ").contains("🟡").contains("【L3 一般】");

        dispatcher.dispatch(event(4), "ALARM_CREATED");
        String l4 = capturedAibotMarkdown();
        assertThat(l4).startsWith("> ").contains("🔵").contains("【L4 警告】");
        assertThat(l4).doesNotContain("\n#");

        dispatcher.dispatch(event(5), "ALARM_RESEND");
        String l5 = capturedAibotMarkdown();
        assertThat(l5).startsWith("> ").contains("⚪").contains("【L5 提示】").contains("🔁");
    }

    @Test
    @DisplayName("企微 aibot 恢复事件：引用块轻提示 + ✅，高级别也不放大标题")
    void should_renderAibotMarkdown_recoveredAlwaysLightweight() {
        when(channelMapper.selectList(any())).thenReturn(List.of(aibotChannel(5)));

        dispatcher.dispatch(event(1), "ALARM_RECOVERED");
        String markdown = capturedAibotMarkdown();
        assertThat(markdown).startsWith("> ").contains("✅").contains("已恢复");
        assertThat(markdown).doesNotContain("\n#");
    }

    @Test
    @DisplayName("企微 aibot 推送成功/失败：写一行对应状态的通知日志")
    void should_logAibotAttemptOutcome() {
        when(channelMapper.selectList(any())).thenReturn(List.of(aibotChannel(5)));

        dispatcher.dispatch(event(2), "ALARM_CREATED");
        verify(notificationLogMapper).insert(any(AlarmNotificationLog.class));

        org.mockito.Mockito.doThrow(new RuntimeException("连接已断开"))
                .when(wecomAibotClient).sendMarkdown(anyString(), anyString(), anyString(), anyString());
        dispatcher.dispatch(event(2), "ALARM_CREATED");

        ArgumentCaptor<AlarmNotificationLog> captor = ArgumentCaptor.forClass(AlarmNotificationLog.class);
        verify(notificationLogMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo(1);
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(2);
        assertThat(captor.getAllValues().get(1).getErrorMessage()).contains("长连接推送失败");
    }
}
