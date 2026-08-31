package com.dcim.simulator.gateway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上报策略单元测试：周期/死区/越限判定
 */
class ReportStrategyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 1, 12, 0, 0);

    @Test
    void periodicDueReportsAllPoints() {
        List<String> points = ReportStrategy.decide(
                Map.of("temperature", bd(22), "humidity", bd(60)),
                Map.of("temperature", bd(22), "humidity", bd(60)),
                NOW.minusSeconds(61), NOW, 60, 0.5, Map.of());
        assertThat(points).containsExactlyInAnyOrder("temperature", "humidity");
    }

    @Test
    void withinPeriodOnlyDeadbandChangesReport() {
        List<String> points = ReportStrategy.decide(
                Map.of("temperature", bd(25), "humidity", bd(60.2)),
                Map.of("temperature", bd(25), "humidity", bd(60)),
                NOW.minusSeconds(10), NOW, 60, 0.5, Map.of());
        // 湿度变化 0.2 < 死区 0.5：不报；温度无变化：不报
        assertThat(points).isEmpty();
    }

    @Test
    void deadbandExceededReports() {
        List<String> points = ReportStrategy.decide(
                Map.of("temperature", bd(25.6)),
                Map.of("temperature", bd(25)),
                NOW.minusSeconds(10), NOW, 60, 0.5, Map.of());
        assertThat(points).containsExactly("temperature");
    }

    @Test
    void alarmThresholdReportsRegardlessOfPeriod() {
        Map<String, List<ReportStrategy.Threshold>> thresholds =
                Map.of("temperature", List.of(new ReportStrategy.Threshold("GT", bd(35))));
        List<String> points = ReportStrategy.decide(
                Map.of("temperature", bd(40)),
                Map.of("temperature", bd(40)),
                NOW.minusSeconds(10), NOW, 60, 0.5, thresholds);
        assertThat(points).containsExactly("temperature");
    }

    @Test
    void firstSightingReportsWhenLastMissing() {
        List<String> points = ReportStrategy.decide(
                Map.of("voltage_a", bd(220)),
                Map.of(),
                NOW.minusSeconds(10), NOW, 60, 0.5, Map.of());
        assertThat(points).containsExactly("voltage_a");
    }

    @Test
    void violatesChecksBothOperators() {
        List<ReportStrategy.Threshold> ts = List.of(
                new ReportStrategy.Threshold("GT", bd(35)),
                new ReportStrategy.Threshold("LT", bd(10)));
        assertThat(ReportStrategy.violates(bd(40), ts)).isTrue();
        assertThat(ReportStrategy.violates(bd(5), ts)).isTrue();
        assertThat(ReportStrategy.violates(bd(22), ts)).isFalse();
        assertThat(ReportStrategy.violates(bd(22), null)).isFalse();
    }

    private BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
