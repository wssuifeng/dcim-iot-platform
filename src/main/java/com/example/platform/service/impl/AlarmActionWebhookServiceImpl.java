package com.example.platform.service.impl;

import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.AlarmNotificationLog;
import com.example.platform.entity.AlarmTemplateItem;
import com.example.platform.mapper.AlarmNotificationLogMapper;
import com.example.platform.mapper.AlarmTemplateItemMapper;
import com.example.platform.service.AlarmActionWebhookService;
import com.example.platform.service.WebhookSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/*
 * 类功能:告警下行动作 webhook 实现（S8-9）——规则级开关，命中触发/升级时回调
 *
 * 【定位边界】通知形态钩子，不做控制闭环：
 *   - 开关粒度=规则行（alarm_template_item.action_webhook_url 非空即启用）；
 *   - 报文=平台标准告警 JSON（与 S8-2 GENERIC 渠道同构），动作语义由外部系统解释；
 *   - 3s 超时（WebhookSender 短超时口径）、失败仅记通知日志（channel=ACTION_WEBHOOK），
 *     不重试不阻塞分发链——下行是"尽力而为"，可靠性属外部系统职责。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmActionWebhookServiceImpl implements AlarmActionWebhookService {

    private static final int LOG_STATUS_DELIVERED = 1;
    private static final int LOG_STATUS_FAILED = 2;

    private final AlarmTemplateItemMapper templateItemMapper;
    private final AlarmNotificationLogMapper notificationLogMapper;
    private final WebhookSender webhookSender;

    @Override
    public void invokeAction(AlarmEvent event, Long templateItemId) {
        if (templateItemId == null) {
            return;
        }
        try {
            AlarmTemplateItem item = templateItemMapper.selectById(templateItemId);
            if (item == null || item.getActionWebhookUrl() == null
                    || item.getActionWebhookUrl().isBlank()) {
                return;
            }
            String body = buildBody(event);
            try {
                webhookSender.send(item.getActionWebhookUrl(),
                        java.util.Map.of("Content-Type", "application/json"), body);
                saveLog(event, LOG_STATUS_DELIVERED, null, item.getActionWebhookUrl());
                log.info("下行动作回调完成 alarmEventId={} ruleItemId={} url={}",
                        event.getId(), templateItemId, item.getActionWebhookUrl());
            } catch (Exception ex) {
                // 失败仅记录：不重试、不影响其余分发通道
                saveLog(event, LOG_STATUS_FAILED, "回调失败: " + ex.getMessage(),
                        item.getActionWebhookUrl());
                log.warn("下行动作回调失败 alarmEventId={} ruleItemId={} error={}",
                        event.getId(), templateItemId, ex.getMessage());
            }
        } catch (Exception ex) {
            // 规则行查询等异常同样只记日志，动作链路永不外抛
            log.warn("下行动作执行异常 alarmEventId={} ruleItemId={} error={}",
                    event.getId(), templateItemId, ex.getMessage());
        }
    }

    // 报文与 S8-2 GENERIC 渠道同构（eventType=action 语义由外部系统解释）
    private String buildBody(AlarmEvent event) {
        return String.format(
                "{\"eventType\":\"ALARM_ACTION\",\"alarmEventId\":%d,\"alarmType\":\"%s\","
                        + "\"alarmLevel\":%d,\"state\":\"%s\",\"message\":\"%s\","
                        + "\"deviceId\":%s,\"pointId\":%s,\"triggeredTime\":\"%s\"}",
                event.getId(),
                event.getAlarmType(),
                event.getAlarmLevel() != null ? event.getAlarmLevel() : 0,
                event.getState() != null ? event.getState() : "",
                event.getAlarmMessage() != null
                        ? event.getAlarmMessage().replace("\\", "\\\\").replace("\"", "\\\"") : "",
                event.getDeviceId() != null ? String.valueOf(event.getDeviceId()) : "null",
                event.getPointId() != null ? String.valueOf(event.getPointId()) : "null",
                event.getTriggeredTime() != null ? event.getTriggeredTime().toString() : "");
    }

    private void saveLog(AlarmEvent event, int status, String error, String url) {
        try {
            AlarmNotificationLog logEntry = new AlarmNotificationLog();
            logEntry.setAlarmEventId(event.getId());
            logEntry.setChannel("ACTION_WEBHOOK");
            logEntry.setRecipient(url);
            logEntry.setContent("下行动作回调");
            logEntry.setStatus(status);
            logEntry.setErrorMessage(error);
            logEntry.setSentTime(java.time.LocalDateTime.now());
            notificationLogMapper.insert(logEntry);
        } catch (Exception ex) {
            log.warn("下行动作日志写入失败 alarmEventId={}", event.getId());
        }
    }
}
