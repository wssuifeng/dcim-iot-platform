package com.example.platform.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/*
 * 类功能:向指定用户的最小摘要队列发送消息，不提供全局敏感广播能力
 * */
@Component
@RequiredArgsConstructor
public class UserScopedWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendSummary(Long userId, WebSocketSummary summary) {
        if (userId == null) {
            throw new IllegalArgumentException("WebSocket摘要接收用户不能为空");
        }
        if (summary == null) {
            throw new IllegalArgumentException("WebSocket摘要内容不能为空");
        }
        messagingTemplate.convertAndSendToUser(
                String.valueOf(userId), WebSocketDestinations.SUMMARY_QUEUE, summary);
    }
}
