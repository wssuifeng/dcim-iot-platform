package com.example.platform.service.impl;

import com.example.platform.entity.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/*
 * 类功能:告警 WebSocket 实时推送服务，通过 STOMP 向订阅客户端广播告警消息
 * */
@Slf4j
@Service
public class AlertWebSocketService {

    private static final String ALERT_TOPIC = "/topic/alerts";

    private final SimpMessagingTemplate messagingTemplate;

    public AlertWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /*
     * 方法功能:向所有订阅 /topic/alerts 的客户端广播新告警
     * 参数:alert-已持久化的告警实体
     * 返回数据:无
     * */
    public void pushAlert(Alert alert) {
        try {
            messagingTemplate.convertAndSend(ALERT_TOPIC, alert);
            log.info("WebSocket 告警推送成功: alertId={}", alert.getId());
        } catch (Exception e) {
            log.warn("WebSocket 告警推送失败: alertId={}, error={}", alert.getId(), e.getMessage());
        }
    }
}
