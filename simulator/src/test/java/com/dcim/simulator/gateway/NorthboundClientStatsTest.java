package com.dcim.simulator.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NorthboundClient 内建统计测试：HTTP 成功/失败计数、延迟分位、qps 滑动窗口
 */
class NorthboundClientStatsTest {

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ingest/samples", exchange -> {
            byte[] body = "{\"accepted\":1,\"duplicated\":0,\"failed\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private NorthboundClient.SampleItem item(String device) {
        return new NorthboundClient.SampleItem(device, "temperature", 23.5, LocalDateTime.now());
    }

    @Test
    @DisplayName("HTTP 成功上报：请求/成功计数累加，延迟分位可估算")
    void should_countSuccess_andRecordLatency() {
        int port = server.getAddress().getPort();
        NorthboundClient client = new NorthboundClient("HTTP",
                "http://127.0.0.1:" + port, "ak", "sk");

        boolean ok = client.report(List.of(item("TH-01")));

        assertThat(ok).isTrue();
        assertThat(client.stats().httpRequests()).isEqualTo(1);
        assertThat(client.stats().httpSuccess()).isEqualTo(1);
        assertThat(client.stats().httpFailed()).isZero();
        assertThat(client.stats().percentile(0.5)).isNotNull();
    }

    @Test
    @DisplayName("HTTP 失败上报：失败计数累加且失败也计入延迟样本")
    void should_countFailed_andRecordLatency() {
        NorthboundClient client = new NorthboundClient("HTTP",
                "http://127.0.0.1:1", "ak", "sk");

        boolean ok = client.report(List.of(item("TH-01")));

        assertThat(ok).isFalse();
        assertThat(client.stats().httpRequests()).isEqualTo(1);
        assertThat(client.stats().httpFailed()).isEqualTo(1);
        assertThat(client.stats().percentile(0.95)).isNotNull();
    }

    @Test
    @DisplayName("qps 滑动窗口：成功上报后窗口内速率大于 0")
    void should_reportQps_afterSuccess() {
        int port = server.getAddress().getPort();
        NorthboundClient client = new NorthboundClient("HTTP",
                "http://127.0.0.1:" + port, "ak", "sk");

        client.report(List.of(item("TH-01")));

        assertThat(client.stats().qps10s()).isGreaterThan(0);
    }
}
