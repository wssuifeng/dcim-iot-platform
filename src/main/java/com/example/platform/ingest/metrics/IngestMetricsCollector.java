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
 * 说明:零写路径侵入——计数用 LongAdder、延迟用滑动窗口直方图，无锁竞争热点；
 *   延迟直方图为【滑动 5 分钟窗口】（S8-4）：5 个分钟桶环形复用，分钟翻转时清零
 *   复用桶；分位数反映最近约 5 分钟（4~6 分钟），不再随进程运行累计失真；
 *   采集点：批级（HTTP/MQTT 适配器，含耗时）+ 条目级（IngestCore：接受/去重/失败/自动注册）；
 *   计数为内存态，进程重启归零（快照带 since 时间戳供压测脚本差分）。
 * */
@Slf4j
@Component
public class IngestMetricsCollector {

    /** 批次耗时直方图槽位边界（ms），超出记入溢出槽 */
    private static final int[] LATENCY_SLOTS_MS = {1, 2, 5, 10, 25, 50, 100, 250, 500, 1000};

    /** 滑动窗口分钟桶数：5 桶 × 1 分钟 ≈ 最近 5 分钟（4~6 分钟） */
    private static final int WINDOW_MINUTE_BUCKETS = 5;

    private final Instant startedAt = Instant.now();

    // 条目级计数（IngestCore 处理链）——累计口径
    private final LongAdder samplesAccepted = new LongAdder();
    private final LongAdder samplesDeduped = new LongAdder();
    private final LongAdder samplesFailed = new LongAdder();
    private final LongAdder autoRegistered = new LongAdder();

    // 批级计数（分传输，累计口径）
    private final AtomicLong httpBatches = new AtomicLong();
    private final AtomicLong mqttBatches = new AtomicLong();

    // 延迟直方图（分传输，滑动 5 分钟窗口）：[分钟桶][槽位]
    private final LongAdder[][] httpLatencyRing;
    private final LongAdder[][] mqttLatencyRing;
    private final AtomicLong lastRotatedMinute = new AtomicLong(epochMinute());

    public IngestMetricsCollector() {
        this.httpLatencyRing = newRing();
        this.mqttLatencyRing = newRing();
    }

    /**
     * 记录一个上报批次（HTTP/MQTT 适配器在处理前后计时调用）
     * 参数:transport-http/mqtt, itemCount-批内条目数, durationMs-批处理耗时, accepted/deduped/failed-核心处理结果
     */
    public void recordBatch(String transport, int itemCount, long durationMs,
                            int accepted, int deduped, int failed) {
        boolean isMqtt = "mqtt".equalsIgnoreCase(transport);
        (isMqtt ? mqttBatches : httpBatches).incrementAndGet();
        rotateIfNeeded();
        LongAdder[] minuteBucket = (isMqtt ? mqttLatencyRing : httpLatencyRing)[currentMinuteIndex()];
        minuteBucket[slotIndex(durationMs)].increment();
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

    /**
     * 观测快照：累计计数 + 分传输批次与滑动窗口延迟分位（P50/P95/P99，直方图估算）
     */
    public java.util.Map<String, Object> snapshot() {
        long[] httpSlots = summedSlots(httpLatencyRing);
        long[] mqttSlots = summedSlots(mqttLatencyRing);
        long totalLatencySamples = sum(httpSlots) + sum(mqttSlots);
        List<Long> boundaries = new ArrayList<>();
        for (int b : LATENCY_SLOTS_MS) {
            boundaries.add((long) b);
        }

        java.util.Map<String, Object> latency = new java.util.LinkedHashMap<>();
        latency.put("windowMinutes", WINDOW_MINUTE_BUCKETS);
        latency.put("slotsMs", boundaries);
        latency.put("httpSlots", toBoxed(httpSlots));
        latency.put("mqttSlots", toBoxed(mqttSlots));
        latency.put("p50Ms", estimatePercentile(httpSlots, mqttSlots, 0.50));
        latency.put("p95Ms", estimatePercentile(httpSlots, mqttSlots, 0.95));
        latency.put("p99Ms", estimatePercentile(httpSlots, mqttSlots, 0.99));

        java.util.Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("since", startedAt.toString());
        snapshot.put("uptimeSeconds", java.time.Duration.between(startedAt, Instant.now()).getSeconds());
        snapshot.put("samplesAccepted", samplesAccepted.sum());
        snapshot.put("samplesDeduped", samplesDeduped.sum());
        snapshot.put("samplesFailed", samplesFailed.sum());
        snapshot.put("autoRegistered", autoRegistered.sum());
        snapshot.put("batchesHttp", httpBatches.get());
        snapshot.put("batchesMqtt", mqttBatches.get());
        snapshot.put("batchesTotal", httpBatches.get() + mqttBatches.get());
        snapshot.put("latencySamples", totalLatencySamples);
        snapshot.put("latency", latency);
        return snapshot;
    }

    // ===================== 滑动窗口实现 =====================

    private static long epochMinute() {
        return System.currentTimeMillis() / 60000L;
    }

    private static int currentMinuteIndex() {
        return (int) (epochMinute() % WINDOW_MINUTE_BUCKETS);
    }

    private LongAdder[][] newRing() {
        LongAdder[][] ring = new LongAdder[WINDOW_MINUTE_BUCKETS][LATENCY_SLOTS_MS.length + 1];
        for (LongAdder[] bucket : ring) {
            for (int i = 0; i < bucket.length; i++) {
                bucket[i] = new LongAdder();
            }
        }
        return ring;
    }

    // 分钟翻转时清零待复用的分钟桶（跨多分钟翻账时逐桶清）；并发下重复清零仅影响精度，可接受
    private void rotateIfNeeded() {
        long nowMinute = epochMinute();
        long last = lastRotatedMinute.get();
        if (nowMinute == last) {
            return;
        }
        if (lastRotatedMinute.compareAndSet(last, nowMinute)) {
            int steps = (int) Math.min(nowMinute - last, WINDOW_MINUTE_BUCKETS);
            for (int s = 1; s <= steps; s++) {
                int idx = (int) ((last + s) % WINDOW_MINUTE_BUCKETS);
                for (LongAdder adder : httpLatencyRing[idx]) {
                    adder.reset();
                }
                for (LongAdder adder : mqttLatencyRing[idx]) {
                    adder.reset();
                }
            }
        }
    }

    private int slotIndex(long durationMs) {
        int i = 0;
        while (i < LATENCY_SLOTS_MS.length && durationMs > LATENCY_SLOTS_MS[i]) {
            i++;
        }
        return i;
    }

    private long[] summedSlots(LongAdder[][] ring) {
        long[] summed = new long[LATENCY_SLOTS_MS.length + 1];
        for (LongAdder[] bucket : ring) {
            for (int i = 0; i < bucket.length; i++) {
                summed[i] += bucket[i].sum();
            }
        }
        return summed;
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
            boxed.add((Long) v);
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
