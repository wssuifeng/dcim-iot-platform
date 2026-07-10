package com.example.platform.security;

/*
 * 类功能:WebSocket 用户隔离目的地契约，供配置、鉴权和后续业务发布器复用
 * */
public final class WebSocketDestinations {

    public static final String USER_PREFIX = "/user";
    public static final String BROKER_PREFIX = "/queue";
    public static final String SUMMARY_QUEUE = "/queue/summary";
    public static final String SUMMARY_SUBSCRIPTION = USER_PREFIX + SUMMARY_QUEUE;

    private WebSocketDestinations() {
    }
}
