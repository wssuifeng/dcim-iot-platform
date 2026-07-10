package com.example.platform.security;

import java.time.Instant;

/*
 * 类功能:WebSocket 最小事件摘要，客户端收到后必须通过 REST 回查业务详情
 * */
public record WebSocketSummary(
        String eventType,
        String resourceType,
        String resourceId,
        String state,
        Instant occurredAt) {

    public WebSocketSummary {
        requireText(eventType, "eventType");
        requireText(resourceType, "resourceType");
        requireText(resourceId, "resourceId");
        requireText(state, "state");
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt不能为空");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
    }
}
