package com.example.platform.service;

import java.util.Map;

/*
 * 类功能:webhook HTTP 发送器接口（抽象 HTTP 层，便于分发器重试逻辑单元测试）
 * */
public interface WebhookSender {

    /*
     * 方法功能:发送 webhook 请求
     * 参数:url-目标地址, headers-请求头, body-请求体
     * 返回数据:响应体字符串（可能为空）
     * 抛出异常:HTTP 非 2xx 或连接失败时抛 RuntimeException（由分发器决定重试）
     * */
    String send(String url, Map<String, String> headers, String body);
}
