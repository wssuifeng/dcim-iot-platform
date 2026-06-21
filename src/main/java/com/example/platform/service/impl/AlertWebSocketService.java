package com.example.platform.service.impl;

import com.example.platform.entity.Alert;
import com.example.platform.entity.NotificationLog;
import com.example.platform.mapper.NotificationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/*
 * 类功能:告警 WebSocket 实时推送服务，通过 STOMP 向订阅客户端广播告警消息
 * */
@Slf4j
@Service
public class AlertWebSocketService {

    private static final String ALERT_TOPIC = "/topic/alerts";

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationLogMapper notificationLogMapper;

    public AlertWebSocketService(SimpMessagingTemplate messagingTemplate,
                                  NotificationLogMapper notificationLogMapper) {
        this.messagingTemplate = messagingTemplate;
        this.notificationLogMapper = notificationLogMapper;
    }

    /*
     * 方法功能:向所有订阅 /topic/alerts 的客户端广播新告警，并记录推送日志
     * 参数:alert-已持久化的告警实体
     * 返回数据:无
     * */
    public void pushAlert(Alert alert) {
        String error = null;
        int status = 1;
        try {
            messagingTemplate.convertAndSend(ALERT_TOPIC, alert);
            log.info("WebSocket 告警推送成功: alertId={}", alert.getId());
        } catch (Exception e) {
            status = 2;
            error = e.getMessage();
            log.warn("WebSocket 告警推送失败: alertId={}, error={}", alert.getId(), error);
        } finally {
            saveLog(alert, status, error);
        }
    }

    private void saveLog(Alert alert, int status, String error) {
        try {
            NotificationLog logEntry = new NotificationLog();
            logEntry.setAlertId(alert.getId());
            logEntry.setNotificationType("WEBSOCKET");
            logEntry.setRecipient("/topic/alerts");
            logEntry.setContent(alert.getAlertMessage());
            logEntry.setStatus(status);
            logEntry.setErrorMessage(error);
            logEntry.setSendTime(LocalDateTime.now());
            notificationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("通知日志写入失败: alertId={}, type=WEBSOCKET, error={}", alert.getId(), e.getMessage());
        }
    }
}
