package com.dcim.simulator.device;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 环境引擎：纯函数求值 value(pointCode, t) = Σ channelTerm(channel, t)，无内存状态
 *
 * 同指标多通道求和；任意时刻可回放求值，崩溃恢复不需要重放历史。
 * STEP 的动态性来自剧本/随机事件修改通道定义（写库），本类只读求值。
 */
public final class EnvironmentEngine {

    private EnvironmentEngine() {
    }

    /**
     * 求某指标在时刻 t 的环境真值：所有关联通道贡献之和
     */
    public static BigDecimal evaluate(List<EnvironmentChannel> channels, LocalDateTime t) {
        BigDecimal sum = BigDecimal.ZERO;
        for (EnvironmentChannel channel : channels) {
            sum = sum.add(termOf(channel, t));
        }
        return sum.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 单通道贡献：CONSTANT=base；SINUSOID=base+amp·sin(2πt/T+φ)；RAMP=base+slope·Δt；STEP=step_at 前 base 后 step_to
     */
    static BigDecimal termOf(EnvironmentChannel channel, LocalDateTime t) {
        String func = channel.getFuncType() == null ? "CONSTANT" : channel.getFuncType();
        BigDecimal base = nvl(channel.getBaseValue());
        return switch (func) {
            case "SINUSOID" -> {
                double period = channel.getPeriodSec() == null || channel.getPeriodSec() <= 0
                        ? 86400 : channel.getPeriodSec();
                // 相位由指标编码哈希派生（确定性，无需存储）
                double phase = (channel.getPointCode() == null ? 0
                        : channel.getPointCode().hashCode() % 360) * Math.PI / 180;
                double epochSec = t.atZone(java.time.ZoneOffset.UTC).toEpochSecond();
                double value = base.doubleValue() + nvl(channel.getAmplitude()).doubleValue()
                        * Math.sin(2 * Math.PI * epochSec / period + phase);
                yield BigDecimal.valueOf(value).setScale(4, java.math.RoundingMode.HALF_UP);
            }
            case "RAMP" -> {
                LocalDateTime t0 = channel.getUpdateTime() != null ? channel.getUpdateTime() : t;
                long elapsed = Math.max(0, Duration.between(t0, t).getSeconds());
                yield base.add(nvl(channel.getSlopePerSec())
                        .multiply(BigDecimal.valueOf(elapsed))
                        .setScale(4, java.math.RoundingMode.HALF_UP));
            }
            case "TRANSITION" -> {
                // 平滑过渡：base → stepTo，在 (stepAt, stepAt+duration] 内线性插值
                if (channel.getStepAt() != null && channel.getStepTo() != null
                        && channel.getDurationSec() != null && channel.getDurationSec() > 0) {
                    long elapsed = Duration.between(channel.getStepAt(), t).getSeconds();
                    if (elapsed <= 0) {
                        yield base;
                    }
                    if (elapsed >= channel.getDurationSec()) {
                        yield channel.getStepTo();
                    }
                    double ratio = (double) elapsed / channel.getDurationSec();
                    double value = base.doubleValue()
                            + (channel.getStepTo().doubleValue() - base.doubleValue()) * ratio;
                    yield BigDecimal.valueOf(value).setScale(4, java.math.RoundingMode.HALF_UP);
                }
                yield base;
            }
            case "STEP" -> {
                if (channel.getStepAt() != null && !t.isBefore(channel.getStepAt())
                        && channel.getStepTo() != null) {
                    yield channel.getStepTo();
                }
                yield base;
            }
            default -> base;
        };
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
