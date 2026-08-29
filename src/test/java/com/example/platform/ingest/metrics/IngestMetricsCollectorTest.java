package com.example.platform.ingest.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 类功能:IngestMetricsCollector 单元测试——批次/条目计数、直方图槽位、分位估算、快照结构
 * */
@DisplayName("IngestMetricsCollector 观测采集器单元测试")
class IngestMetricsCollectorTest {

    private IngestMetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new IngestMetricsCollector();
    }

    @Test
    @DisplayName("批次按传输分别计数，总数为两者之和")
    void should_countBatchesPerTransport() {
        collector.recordBatch("http", 10, 5, 10, 0, 0);
        collector.recordBatch("http", 10, 5, 10, 0, 0);
        collector.recordBatch("MQTT", 10, 5, 10, 0, 0);

        Map<String, Object> snapshot = collector.snapshot();
        assertThat(snapshot.get("batchesHttp")).isEqualTo(2L);
        assertThat(snapshot.get("batchesMqtt")).isEqualTo(1L);
        assertThat(snapshot.get("batchesTotal")).isEqualTo(3L);
    }

    @Test
    @DisplayName("条目计数累加：接受/去重/失败来自各批结果之和")
    void should_accumulateItemCounts() {
        collector.recordBatch("http", 3, 5, 2, 1, 0);
        collector.recordBatch("http", 3, 5, 1, 0, 2);
        collector.incrementAutoRegistered();

        Map<String, Object> snapshot = collector.snapshot();
        assertThat(snapshot.get("samplesAccepted")).isEqualTo(3L);
        assertThat(snapshot.get("samplesDeduped")).isEqualTo(1L);
        assertThat(snapshot.get("samplesFailed")).isEqualTo(2L);
        assertThat(snapshot.get("autoRegistered")).isEqualTo(1L);
    }

    @Test
    @DisplayName("延迟槽位：按边界落入对应槽，溢出进最后槽")
    void should_bucketLatency() {
        collector.recordBatch("http", 1, 1, 1, 0, 0);    // 1ms → 槽0（边界1，≤取等）
        collector.recordBatch("http", 1, 6, 1, 0, 0);    // 6ms → 槽3（>5 且 ≤10）
        collector.recordBatch("http", 1, 1500, 1, 0, 0); // >1000 → 溢出槽

        Map<String, Object> latency = (Map<String, Object>) collector.snapshot().get("latency");
        java.util.List<Number> slots = (java.util.List<Number>) latency.get("httpSlots");
        assertThat(slots.get(0).longValue()).isEqualTo(1);
        assertThat(slots.get(3).longValue()).isEqualTo(1);
        assertThat(slots.get(slots.size() - 1).longValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("分位估算：P50 取累计半数所在槽上边界")
    void should_estimatePercentileFromHistogram() {
        // 10 个批次：9 个 1ms、1 个 500ms → P50=1ms、P95=500ms
        for (int i = 0; i < 9; i++) {
            collector.recordBatch("http", 1, 1, 1, 0, 0);
        }
        collector.recordBatch("http", 1, 500, 1, 0, 0);

        Map<String, Object> latency = (Map<String, Object>) collector.snapshot().get("latency");
        assertThat(((Number) latency.get("p50Ms")).longValue()).isEqualTo(1L);
        assertThat(((Number) latency.get("p95Ms")).longValue()).isEqualTo(500L);
    }

    @Test
    @DisplayName("无样本时分位为 null 且延迟样本数为 0")
    void should_returnNullPercentile_whenNoSamples() {
        Map<String, Object> latency = (Map<String, Object>) collector.snapshot().get("latency");
        assertThat(latency.get("p50Ms")).isNull();
        assertThat(collector.snapshot().get("latencySamples")).isEqualTo(0L);
    }

    @Test
    @DisplayName("快照结构完整：since/uptime/槽位边界齐全")
    void should_exposeSnapshotStructure() {
        collector.recordBatch("mqtt", 1, 5, 1, 0, 0);

        Map<String, Object> snapshot = collector.snapshot();
        assertThat(snapshot.get("since")).isNotNull();
        assertThat(((Number) snapshot.get("uptimeSeconds")).intValue()).isGreaterThanOrEqualTo(0);
        Map<String, Object> latency = (Map<String, Object>) snapshot.get("latency");
        assertThat((java.util.List<?>) latency.get("slotsMs")).hasSize(10);
        assertThat((java.util.List<?>) latency.get("mqttSlots")).hasSize(11);
    }
}
