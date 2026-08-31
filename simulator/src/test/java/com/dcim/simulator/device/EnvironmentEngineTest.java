package com.dcim.simulator.device;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 环境引擎纯函数单元测试：四种函数项 + 多通道求和
 */
class EnvironmentEngineTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 1, 12, 0, 0);

    private EnvironmentChannel channel(String func, double base, double amp,
                                       int periodSec, double slope, LocalDateTime stepAt, Double stepTo) {
        EnvironmentChannel c = new EnvironmentChannel();
        c.setPointCode("temperature");
        c.setFuncType(func);
        c.setBaseValue(BigDecimal.valueOf(base));
        c.setAmplitude(BigDecimal.valueOf(amp));
        c.setPeriodSec(periodSec);
        c.setSlopePerSec(BigDecimal.valueOf(slope));
        c.setStepAt(stepAt);
        if (stepTo != null) c.setStepTo(BigDecimal.valueOf(stepTo));
        return c;
    }

    @Test
    void constantReturnsBase() {
        BigDecimal v = EnvironmentEngine.evaluate(
                List.of(channel("CONSTANT", 25, 0, 0, 0, null, null)), T0);
        assertThat(v.doubleValue()).isEqualTo(25.0);
    }

    @Test
    void sinusoidStaysWithinAmplitudeBand() {
        EnvironmentChannel sin = channel("SINUSOID", 24, 4, 3600, 0, null, null);
        for (int sec = 0; sec < 3600; sec += 60) {
            double v = EnvironmentEngine.evaluate(List.of(sin), T0.plusSeconds(sec)).doubleValue();
            assertThat(v).isBetween(20.0, 28.0);
        }
    }

    @Test
    void sinusoidIsPeriodic() {
        EnvironmentChannel sin = channel("SINUSOID", 24, 4, 600, 0, null, null);
        double v1 = EnvironmentEngine.evaluate(List.of(sin), T0).doubleValue();
        double v2 = EnvironmentEngine.evaluate(List.of(sin), T0.plusSeconds(600)).doubleValue();
        assertThat(v1).isEqualTo(v2);
    }

    @Test
    void rampAccumulatesSlopeFromT0() {
        EnvironmentChannel ramp = channel("RAMP", 20, 0, 0, 0.01, null, null);
        // update_time 由库维护，测试中为 null 时 t0=求值时刻 → 贡献为 base
        double v0 = EnvironmentEngine.evaluate(List.of(ramp), T0).doubleValue();
        assertThat(v0).isEqualTo(20.0);
    }

    @Test
    void stepSwitchesAfterStepAt() {
        LocalDateTime stepAt = T0.plusMinutes(1);
        EnvironmentChannel step = channel("STEP", 22, 0, 0, 0, stepAt, 40.0);
        assertThat(EnvironmentEngine.evaluate(List.of(step), T0).doubleValue()).isEqualTo(22.0);
        assertThat(EnvironmentEngine.evaluate(List.of(step), T0.plusMinutes(2)).doubleValue()).isEqualTo(40.0);
    }

    @Test
    void multipleChannelsSum() {
        BigDecimal v = EnvironmentEngine.evaluate(List.of(
                channel("CONSTANT", 20, 0, 0, 0, null, null),
                channel("CONSTANT", 5, 0, 0, 0, null, null)), T0);
        assertThat(v.doubleValue()).isEqualTo(25.0);
    }

    @Test
    void transitionInterpolatesLinearly() {
        EnvironmentChannel tr = channel("TRANSITION", 20, 0, 0, 0,
                T0.plusMinutes(1), 40.0);
        tr.setDurationSec(120); // T0+1min 起 120 秒内 20→40
        // 过渡前：基值
        assertThat(EnvironmentEngine.evaluate(List.of(tr), T0).doubleValue()).isEqualTo(20.0);
        // 过渡中点：30
        assertThat(EnvironmentEngine.evaluate(List.of(tr), T0.plusMinutes(2)).doubleValue())
                .isEqualTo(30.0);
        // 过渡完成后：目标值
        assertThat(EnvironmentEngine.evaluate(List.of(tr), T0.plusMinutes(5)).doubleValue())
                .isEqualTo(40.0);
    }

    @Test
    void transitionWithoutDurationFallsBackToBase() {
        EnvironmentChannel tr = channel("TRANSITION", 20, 0, 0, 0,
                T0.plusMinutes(1), 40.0); // durationSec=null
        assertThat(EnvironmentEngine.evaluate(List.of(tr), T0.plusMinutes(5)).doubleValue())
                .isEqualTo(20.0);
    }

    @Test
    void evaluateIsDeterministic() {
        EnvironmentChannel sin = channel("SINUSOID", 24, 3, 86400, 0, null, null);
        BigDecimal v1 = EnvironmentEngine.evaluate(List.of(sin), T0);
        BigDecimal v2 = EnvironmentEngine.evaluate(List.of(sin), T0);
        assertThat(v1).isEqualByComparingTo(v2);
    }
}
