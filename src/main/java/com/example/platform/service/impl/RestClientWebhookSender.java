package com.example.platform.service.impl;

import com.example.platform.service.WebhookSender;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

/*
 * 类功能:webhook HTTP 发送器实现（RestClient，短超时防止分发线程被慢目标拖死）
 *
 * 【超时】连接 3s / 读取 5s——分发链在 @Async 线程内做同步退避重试（S8-2 定案，
 *   不引入新调度器），目标不可达时单渠道最坏 4 次尝试 ≈ 40s，多渠道并行受线程池约束。
 * */
@Component
public class RestClientWebhookSender implements WebhookSender {

    private final RestClient restClient;

    public RestClientWebhookSender() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    public String send(String url, Map<String, String> headers, String body) {
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.forEach(httpHeaders::set);
        if (httpHeaders.getContentType() == null) {
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        }
        return restClient.post()
                .uri(url)
                .headers(h -> h.addAll(httpHeaders))
                .body(body)
                .retrieve()
                .body(String.class);
    }
}
