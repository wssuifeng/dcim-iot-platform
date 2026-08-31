package com.example.platform.service.impl;

import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.AlarmNotificationLog;
import com.example.platform.entity.AlarmTemplateItem;
import com.example.platform.mapper.AlarmNotificationLogMapper;
import com.example.platform.mapper.AlarmTemplateItemMapper;
import com.example.platform.service.WebhookSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:AlarmActionWebhookServiceImpl 单元测试（S8-9）
 * 覆盖：规则未配置 URL 不动作、命中回调平台标准 JSON、回调失败仅记日志不外抛、
 *   规则行不存在安全跳过
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlarmActionWebhookService 下行动作测试")
class AlarmActionWebhookServiceImplTest {

    @Mock
    private AlarmTemplateItemMapper templateItemMapper;
    @Mock
    private AlarmNotificationLogMapper notificationLogMapper;
    @Mock
    private WebhookSender webhookSender;

    @InjectMocks
    private AlarmActionWebhookServiceImpl service;

    private AlarmEvent event() {
        AlarmEvent event = new AlarmEvent();
        event.setId(9L);
        event.setDeviceId(2);
        event.setPointId(1L);
        event.setAlarmType("METRIC");
        event.setAlarmLevel(2);
        event.setState("TRIGGERED");
        event.setAlarmMessage("高温警告：温度=45.2℃");
        event.setTriggeredTime(LocalDateTime.of(2026, 8, 31, 10, 0));
        return event;
    }

    private AlarmTemplateItem ruleItem(String actionUrl) {
        AlarmTemplateItem item = new AlarmTemplateItem();
        item.setId(11L);
        item.setRuleType("METRIC");
        item.setActionWebhookUrl(actionUrl);
        return item;
    }

    @Test
    @DisplayName("templateItemId 为 null：无动作不查规则")
    void should_skip_whenNoRuleId() {
        service.invokeAction(event(), null);

        verify(templateItemMapper, never()).selectById(any(Long.class));
        verify(webhookSender, never()).send(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("规则未配置 action_webhook_url：不回调")
    void should_skip_whenRuleHasNoUrl() {
        when(templateItemMapper.selectById(11L)).thenReturn(ruleItem(null));

        service.invokeAction(event(), 11L);

        verify(webhookSender, never()).send(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("命中回调：POST 平台标准告警 JSON 并记成功日志")
    void should_invokeUrl_andLogSuccess() {
        when(templateItemMapper.selectById(11L)).thenReturn(ruleItem("https://ops.example.com/hook"));
        when(webhookSender.send(anyString(), any(), anyString())).thenReturn("ok");

        service.invokeAction(event(), 11L);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(webhookSender).send(eq("https://ops.example.com/hook"),
                eq(Map.of("Content-Type", "application/json")), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue())
                .contains("\"eventType\":\"ALARM_ACTION\"")
                .contains("\"alarmEventId\":9")
                .contains("高温警告");
        ArgumentCaptor<AlarmNotificationLog> logCaptor = ArgumentCaptor.forClass(AlarmNotificationLog.class);
        verify(notificationLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getChannel()).isEqualTo("ACTION_WEBHOOK");
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("回调失败：仅记失败日志不外抛不重试")
    void should_logFailure_only_whenSendFails() {
        when(templateItemMapper.selectById(11L)).thenReturn(ruleItem("https://ops.example.com/hook"));
        when(webhookSender.send(anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("connect timeout"));

        service.invokeAction(event(), 11L);

        // 不重试：仅一次发送
        verify(webhookSender).send(anyString(), any(), anyString());
        ArgumentCaptor<AlarmNotificationLog> logCaptor = ArgumentCaptor.forClass(AlarmNotificationLog.class);
        verify(notificationLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(2);
        assertThat(logCaptor.getValue().getErrorMessage()).contains("connect timeout");
    }

    @Test
    @DisplayName("规则行不存在：安全跳过")
    void should_skip_whenRuleItemMissing() {
        when(templateItemMapper.selectById(404L)).thenReturn(null);

        service.invokeAction(event(), 404L);

        verify(webhookSender, never()).send(anyString(), any(), anyString());
    }
}
