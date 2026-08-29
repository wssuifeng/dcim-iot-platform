package com.example.platform.ingest.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/*
 * 类功能:数据接收门内存观测采集器（阶段七压测配套，只读端点数据源）
 *
 * 说明:零写路径侵入——计数用 LongAdder、延迟用固定槽直方图，无锁竞争热点；
 *   采集点：批级（HTTP/MQTT 适配器，含耗时）+ 条目级（IngestCore：接受/去重/失败/自动注册）；
 *   计数为内存态，进程重启归零（快照带 since 时间戳供压测脚本差分）。
 * */
@Slf4j
@Component
public class IngestMetricsCollector {

    /** 批次耗时直方图槽位边界（ms），超出记入溢出槽 */
    private static final int[] LATENCY_SLOTS_MS = {1, 2, 5, 10, 25, 50, 100, 250, 500, 1000};

    private final Instant startedAt = Instant.now();

    // 条目级计数（IngestCore 处理链）
    private final LongAdder samplesAccepted = new LongAdder();
    private final LongAdder samplesDeduped = new LongAdder();
    private final LongAdder samplesFailed = new LongAdder();
    private final LongAdder autoRegistered = new LongAdder();

    // 批级计数（分传输）+ 耗时直方图（分传输）
    private final AtomicLong httpBatches = new AtomicLong();
    private final AtomicLong mqttBatches = new AtomicLong();
    private final long[] httpLatencySlots;
    private final long[] mqttLatencySlots;

    public IngestMetricsCollector() {
        this.httpLatencySlots = new long[LATENCY_SLOTS_MS.length + 1];
        this.mqttLatencySlots = new long[LATENCY_SLOTS_MS.length + 1];
    }

    /**
     * 记录一个上报批次（HTTP/MQTT 适配器在处理前后计时调用）
     * 参数:transport-http/mqtt, itemCount-批内条目数, durationMs-批处理耗时, accepted/deduped/failed-核心处理结果
     */
    public void recordBatch(String transport, int itemCount, long durationMs,
                            int accepted, int deduped, int failed) {
        boolean isMqtt = "mqtt".equalsIgnoreCase(transport);
        (isMqtt ? mqttBatches : httpBatches).incrementAndGet();
        long[] slots = isMqtt ? mqttLatencySlots : httpLatencySlots;
        recordLatency(slots, durationMs);
        samplesAccepted.add(accepted);
        samplesDeduped.add(deduped);
        samplesFailed.add(failed);
        if (durationMs > 200) {
            log.warn("慢批次 {}ms transport={} items={} accepted={} failed={}",
                    durationMs, transport, itemCount, accepted, failed);
        }
    }

    /** 自动注册计数（首包注册成功一次 +1） */
    public void incrementAutoRegistered() {
        autoRegistered.increment();
    }

    private void recordLatency(long[] slots, long durationMs) {
        int i = 0;
        while (i < LATENCY_SLOTS_MS.length && durationMs > LATENCY_SLOTS_MS[i]) {
            i++;
        }
        slots[i]++;
    }

    /**
     * 观测快照：计数 + 分传输批次与延迟分位（P50/P95/P99，直方图估算）
     */
    public java.util.Map<String, Object> snapshot() {
        long totalBatches = httpBatches.get() + mqttBatches.get();
        long totalLatencySamples = sum(httpLatencySlots) + sum(mqttLatencySlots);
        List<Long> boundaries = new ArrayList<>();
        for (int b : LATENCY_SLOTS_MS) {
            boundaries.add((long) b);
        }

        java.util.Map<String, Object> latency = new java.util.LinkedHashMap<>();
        latency.put("slotsMs", boundaries);
        latency.put("httpSlots", toBoxed(httpLatencySlots));
        latency.put("mqttSlots", toBoxed(mqttLatencySlots));
        latency.put("p50Ms", estimatePercentile(httpLatencySlots, mqttLatencySlots, 0.50));
        latency.put("p95Ms", estimatePercentile(httpLatencySlots, mqttLatencySlots, 0.95));
        latency.put("p99Ms", estimatePercentile(httpLatencySlots, mqttLatencySlots, 0.99));

        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("since", startedAt.toString());
        snapshot.put("uptimeSeconds", java.time.Duration.between(startedAt, Instant.now()).getSeconds());
        snapshot.put("samplesAccepted", samplesAccepted.sum());
        snapshot.put("samplesDeduped", samplesDeduped.sum());
        snapshot.put("samplesFailed", samplesFailed.sum());
        snapshot.put("autoRegistered", autoRegistered.sum());
        snapshot.put("batchesHttp", httpBatches.get());
        snapshot.put("batchesMqtt", mqttBatches.get());
        snapshot.put("batchesTotal", totalBatches);
        snapshot.put("latencySamples", totalLatencySamples);
        snapshot.put("latency", latency);
        return snapshot;
    }

    private long sum(long[] slots) {
        long total = 0;
        for (long v : slots) {
            total += v;
        }
        return total;
    }

    private java.util.List<Long> toBoxed(long[] slots) {
        java.util.List<Long> boxed = new ArrayList<>(slots.length);
        for (long v : slots) {
            boxed.add(v);
        }
        return boxed;
    }

    /** 分位估算：累计到目标分位的槽上边界；无样本返回 null */
    private Long estimatePercentile(long[] a, long[] b, double percentile) {
        long total = sum(a) + sum(b);
        if (total == 0) {
            return null;
        }
        long target = (long) Math.ceil(total * percentile);
        long cumulative = 0;
        for (int i = 0; i < LATENCY_SLOTS_MS.length; i++) {
            cumulative += a[i] + b[i];
            if (cumulative >= target) {
                return (long) LATENCY_SLOTS_MS[i];
            }
        }
        return -1L; // 溢出槽（>1000ms）
    }
}
