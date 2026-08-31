package com.dcim.simulator.gateway;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 北向客户端：把网关侧采样项按平台 ingest 契约上报（MQTT 主 / HTTP 备）
 *
 * 契约（与平台接收门一致）：
 *   HTTP  POST {base}/api/ingest/samples，头 Authorization: Gateway {ak}:{sha256(secret+rawBody)}
 *   MQTT  发布 dcim/{ak}/samples QoS1，报文 {"signature":sha256(secret+batchJson),"batch":{...}}
 * seq 每 deviceCode 单调递增（启动以时间戳为基准，重启不冲突）；MQTT 连接注册遗嘱 lwt topic。
 */
@Slf4j
public class NorthboundClient {

    public record SampleItem(String deviceCode, String pointCode, double value, LocalDateTime sampleTime) {
    }

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final String transport;
    private final String platformBaseUrl;
    private final String accessKey;
    private final String secret;

    private final RestClient restClient;
    private MqttClient mqttClient;
    private final Map<String, AtomicLong> seqByDevice = new ConcurrentHashMap<>();
    private final Stats stats = new Stats();

    public NorthboundClient(String transport, String platformBaseUrl, String accessKey, String secret) {
        this.transport = transport == null ? "HTTP" : transport.toUpperCase();
        this.platformBaseUrl = platformBaseUrl == null ? "" : platformBaseUrl.replaceAll("/$", "");
        this.accessKey = accessKey;
        this.secret = secret;
        this.restClient = RestClient.create();
    }

    /**
     * 上报一批采样项（选配置的传输通道）；返回是否成功送达
     */
    public boolean report(List<SampleItem> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }
        if ("MQTT".equals(transport)) {
            try {
                boolean ok = reportByMqtt(items);
                stats.recordPublish(ok);
                return ok;
            } catch (Exception ex) {
                // MQTT 不可达自动回退 HTTP（主备容灾：broker 故障时数据不断流）
                stats.recordMqttFallback();
                log.warn("MQTT 上报失败，回退 HTTP: {}", ex.getMessage());
                closeQuietly();
            }
        }
        long start = System.nanoTime();
        try {
            boolean ok = reportByHttp(items);
            stats.recordHttp(ok, (System.nanoTime() - start) / 1_000_000);
            return ok;
        } catch (Exception ex) {
            stats.recordHttp(false, (System.nanoTime() - start) / 1_000_000);
            log.warn("北向上报失败 transport=HTTP error={}", ex.getMessage());
            return false;
        }
    }

    /**
     * 网关心跳：恒走 HTTP（与数据传输解耦——MQTT 主通道故障时保活观测不中断）
     */
    public void heartbeat() {
        try {
            String body = "{\"ts\":\"" + ISO.format(LocalDateTime.now()) + "\"}";
            restClient.post()
                    .uri(platformBaseUrl + "/api/ingest/heartbeat")
                    .header("Authorization", "Gateway " + accessKey + ":" + signature(body))
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve().body(String.class);
        } catch (Exception ex) {
            log.debug("心跳失败: {}", ex.getMessage());
        }
    }

    /** 客户端统计快照（压测观测用） */
    public Stats stats() {
        return stats;
    }

    /**
     * 下一次序号（deviceCode 单调递增；重启以时间戳为基准不冲突）
     */
    public long nextSeq(String deviceCode) {
        AtomicLong seq = seqByDevice.computeIfAbsent(deviceCode,
                k -> new AtomicLong(System.currentTimeMillis()));
        return seq.incrementAndGet();
    }

    /**
     * 关闭 MQTT 连接
     */
    public void close() {
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) {
                    mqttClient.disconnect();
                }
                mqttClient.close();
            } catch (Exception ignored) {
            }
        }
    }

    private boolean reportByHttp(List<SampleItem> items) {
        String body = batchJson(items);
        String response = restClient.post()
                .uri(platformBaseUrl + "/api/ingest/samples")
                .header("Authorization", "Gateway " + accessKey + ":" + signature(body))
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve().body(String.class);
        log.debug("HTTP 上报回执: {}", response);
        return true;
    }

    private boolean reportByMqtt(List<SampleItem> items) throws Exception {
        ensureMqttConnected();
        String batchJson = batchJson(items);
        String payload = "{\"signature\":\"" + signature(batchJson) + "\",\"batch\":" + batchJson + "}";
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        mqttClient.publish("dcim/" + accessKey + "/samples", message);
        return true;
    }

    private void closeQuietly() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (Exception ignored) {
        }
    }

    private synchronized void ensureMqttConnected() throws Exception {
        if (mqttClient != null && mqttClient.isConnected()) {
            return;
        }
        if (mqttClient != null) {
            mqttClient.close();
        }
        // broker 地址取平台地址的 1883 端口（自部署 compose 内网约定）
        String broker = "tcp://" + platformBaseUrl.replaceFirst("^https?://", "").replaceAll(":\\d+$", "") + ":1883";
        mqttClient = new MqttClient(broker, "dcim-sim-gateway-" + accessKey, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        // 遗嘱：异常断连时 broker 代发，平台 LWT 监听标记网关离线
        options.setWill("dcim/" + accessKey + "/lwt", "offline".getBytes(StandardCharsets.UTF_8), 1, false);
        mqttClient.connect(options);
        log.info("MQTT 北向已连接 broker={}", broker);
    }

    private String batchJson(List<SampleItem> items) {
        StringBuilder sb = new StringBuilder("{\"items\":[");
        for (int i = 0; i < items.size(); i++) {
            SampleItem item = items.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"deviceCode\":\"").append(item.deviceCode())
                    .append("\",\"pointCode\":\"").append(item.pointCode())
                    .append("\",\"value\":").append(item.value())
                    .append(",\"sampleTime\":\"").append(ISO.format(item.sampleTime()))
                    .append("\",\"seq\":").append(nextSeq(item.deviceCode())).append('}');
        }
        return sb.append("]}").toString();
    }

    /**
     * 签名：hex(sha256(secret + payload))
     */
    String signature(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(secret.getBytes(StandardCharsets.UTF_8));
            digest.update(payload.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * 北向客户端内建统计：HTTP 请求/成功/失败、MQTT 发布/回退、
     * 最近 256 次 HTTP 往返延迟（环形缓冲）、近 10 秒成功滑动窗口（qps 观测）
     */
    public static final class Stats {
        private final java.util.concurrent.atomic.LongAdder httpRequests = new java.util.concurrent.atomic.LongAdder();
        private final java.util.concurrent.atomic.LongAdder httpSuccess = new java.util.concurrent.atomic.LongAdder();
        private final java.util.concurrent.atomic.LongAdder httpFailed = new java.util.concurrent.atomic.LongAdder();
        private final java.util.concurrent.atomic.LongAdder mqttPublished = new java.util.concurrent.atomic.LongAdder();
        private final java.util.concurrent.atomic.LongAdder mqttFallbacks = new java.util.concurrent.atomic.LongAdder();
        private final long[] latencies = new long[256];
        private final java.util.concurrent.atomic.AtomicInteger latencyCount = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.ArrayDeque<Long> successTimestamps = new java.util.ArrayDeque<>();

        void recordHttp(boolean ok, long durationMs) {
            httpRequests.increment();
            if (ok) {
                httpSuccess.increment();
                recordSuccessTimestamp();
            } else {
                httpFailed.increment();
            }
            int idx = latencyCount.getAndIncrement();
            synchronized (latencies) {
                latencies[Math.floorMod(idx, latencies.length)] = durationMs;
            }
        }

        void recordPublish(boolean ok) {
            if (ok) {
                mqttPublished.increment();
                recordSuccessTimestamp();
            }
        }

        void recordMqttFallback() {
            mqttFallbacks.increment();
        }

        private void recordSuccessTimestamp() {
            long now = System.currentTimeMillis();
            synchronized (successTimestamps) {
                successTimestamps.addLast(now);
                while (!successTimestamps.isEmpty() && now - successTimestamps.peekFirst() > 10_000) {
                    successTimestamps.pollFirst();
                }
            }
        }

        /** 近 10 秒成功请求速率（次/秒）；窗口内无请求返回 0 */
        public double qps10s() {
            long cutoff = System.currentTimeMillis() - 10_000;
            int count = 0;
            synchronized (successTimestamps) {
                for (Long ts : successTimestamps) {
                    if (ts >= cutoff) {
                        count++;
                    }
                }
            }
            return count / 10.0;
        }

        /** 最近延迟分位（ms），样本不足返回 null */
        public Long percentile(double p) {
            int count = Math.min(latencyCount.get(), latencies.length);
            if (count == 0) {
                return null;
            }
            long[] copy;
            synchronized (latencies) {
                copy = java.util.Arrays.copyOf(latencies, count);
            }
            java.util.Arrays.sort(copy);
            int idx = (int) Math.ceil(count * p) - 1;
            return copy[Math.max(0, Math.min(idx, count - 1))];
        }

        public long httpRequests() { return httpRequests.sum(); }
        public long httpSuccess() { return httpSuccess.sum(); }
        public long httpFailed() { return httpFailed.sum(); }
        public long mqttPublished() { return mqttPublished.sum(); }
        public long mqttFallbacks() { return mqttFallbacks.sum(); }
    }
}
