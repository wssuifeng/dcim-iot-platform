package com.dcim.simulator.gateway;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 北向客户端单元测试：签名算法与 seq 生成（不实际联网）
 */
class NorthboundClientTest {

    @Test
    void signatureMatchesPlatformAlgorithm() throws Exception {
        NorthboundClient client = new NorthboundClient("HTTP", "http://x", "gw-1", "secret-1");
        String payload = "{\"items\":[]}";

        // 参考实现：hex(sha256("secret-1" + payload))
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update("secret-1".getBytes());
        digest.update(payload.getBytes());
        StringBuilder expected = new StringBuilder();
        for (byte b : digest.digest()) {
            expected.append(Character.forDigit((b >> 4) & 0xF, 16))
                    .append(Character.forDigit(b & 0xF, 16));
        }

        assertThat(client.signature(payload)).isEqualTo(expected.toString()).hasSize(64);
    }

    @Test
    void seqIsMonotonicPerDevice() {
        NorthboundClient client = new NorthboundClient("HTTP", "http://x", "gw-1", "s");
        long a1 = client.nextSeq("DEV-A");
        long a2 = client.nextSeq("DEV-A");
        long b1 = client.nextSeq("DEV-B");
        long b2 = client.nextSeq("DEV-B");

        // 各设备单调递增（基准为毫秒时间戳，不同设备基准可能相同但序列独立）
        assertThat(a2).isGreaterThan(a1);
        assertThat(b2).isGreaterThan(b1);
        assertThat(b1).isGreaterThan(0);
    }

    @Test
    void reportWithEmptyItemsIsNoopSuccess() {
        NorthboundClient client = new NorthboundClient("HTTP", "http://192.0.2.1:1", "gw-1", "s");
        assertThat(client.report(List.of())).isTrue();
    }

    @Test
    void heartbeatDisabledForMqttTransport() {
        // MQTT 传输心跳走连接保活，不应抛异常
        NorthboundClient client = new NorthboundClient("MQTT", "http://192.0.2.1:1", "gw-1", "s");
        client.heartbeat();
    }

    @Test
    void sampleItemRecordCarriesFields() {
        LocalDateTime t = LocalDateTime.of(2026, 9, 1, 12, 0, 0);
        NorthboundClient.SampleItem item = new NorthboundClient.SampleItem("DEV-1", "temperature", 22.5, t);
        assertThat(item.deviceCode()).isEqualTo("DEV-1");
        assertThat(item.value()).isEqualTo(22.5);
    }
}
