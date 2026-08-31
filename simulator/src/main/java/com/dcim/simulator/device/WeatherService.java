package com.dcim.simulator.device;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/*
 * 类功能:室外气象仿真——(seed, 时刻) 确定性纯函数，同种子同时刻结果恒定
 *
 * 【模型】南昌季节基线（月均温插值）+ 日周期（峰值约 14:00）+ 天气型修正
 *   + 平滑噪声。天气型按确定性窗口（3~8h）切换：SUNNY 晴 / CLOUDY 阴 /
 *   RAIN 雨 / STORM 暴雨·季风 / MUGGY 闷热（夏季权重高），各型影响温度修正、
 *   日较差系数与湿度基线。
 * 【确定性】哈希值噪声 + 余弦插值，重启/回放结果一致，无需持久化。
 * 【钳位】T_out ∈ [-8, 42]、RH_out ∈ [20, 100]——仿真护栏，防数值失控。
 * */
@Service
public class WeatherService {

    public enum Regime {
        SUNNY("晴"), CLOUDY("阴"), RAIN("雨"), STORM("暴雨·季风"), MUGGY("闷热");

        private final String label;

        Regime(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * 自然事件（V2）：低概率、长时长的天气异常，叠加在天气型之上
     * （寒潮/热浪/连阴雨/雷暴/桑拿天），6h 确定性窗口 + 边界 45 分钟缓动
     */
    public enum NaturalEvent {
        NONE("无"),
        COLD_SNAP("寒潮"),
        HEAT_WAVE("热浪"),
        PROLONGED_RAIN("连阴雨"),
        THUNDERSTORM("雷暴"),
        SAUNA("桑拿天");

        private final String label;

        NaturalEvent(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** 天气快照 */
    public record Weather(Regime regime, double tempOutC, double rhOut, NaturalEvent event) {
    }

    // 南昌月均温（℃）：1..12 月，气候常值参考（约 5~30℃ 区间，夏热冬冷）
    private static final double[] MONTHLY_MEAN_C = {5, 7, 12, 18, 23, 27, 30, 29, 25, 19, 13, 7};

    private static final double TEMP_MIN = -8;
    private static final double TEMP_MAX = 42;
    private static final double RH_MIN = 20;
    private static final double RH_MAX = 100;

    /*
     * 方法功能:求某机房（seed）在时刻 t 的室外天气快照
     * 参数:seed-机房种子, t-时刻
     * 返回数据:Weather-天气型 + 室外温度/湿度
     * */
    public Weather snapshot(long seed, LocalDateTime t) {
        Regime regime = regimeAt(seed, t);
        double seasonal = seasonalMeanC(t);
        double diurnalAmp = 4.5 * diurnalFactor(regime);
        double hour = t.getHour() + t.getMinute() / 60.0;
        // 日周期：峰值 14 时
        double diurnal = diurnalAmp * Math.sin((hour - 8) / 24 * 2 * Math.PI);
        double regimeTemp = regimeTempOffsetAt(seed, t);
        double noise = smoothNoise(seed ^ 0x57EAL, epochMinute(t), 3) * 1.2;

        // 自然事件层：低概率异常天气（边界 45 分钟缓动），温度修正 + 湿度偏置 + 可强制雨型
        NaturalEvent event = naturalEventAt(seed, t);
        double eventTemp = naturalEventTempOffsetAt(seed, t);
        double eventRh = naturalEventRhBiasAt(seed, t);
        Regime effective = naturalEventRegimeBias(event, regime, seed, t);

        double diurnalEffective = diurnalAmp / diurnalFactor(regime) * diurnalFactor(effective);

        double temp = clamp(seasonal + diurnalEffective + regimeTemp + eventTemp + noise, TEMP_MIN, TEMP_MAX);

        double rhBase = regimeRhBaseAt(seed, t);
        double rhNoise = smoothNoise(seed ^ 0xA11CE, epochMinute(t), 2) * 3;
        // 高温空气饱和所需水汽更多：温度每高基线 1℃，RH 约降 1.5%
        double rh = clamp(rhBase + eventRh + rhNoise - (temp - seasonal) * 1.5, RH_MIN, RH_MAX);
        return new Weather(effective, round4(temp), round4(rh), event);
    }

    // 天气型窗口（5 小时）与边界平滑过渡（45 分钟，模拟锋面过境）
    private static final long REGIME_WINDOW_MIN = 300;
    private static final long REGIME_TRANSITION_MIN = 45;

    // 天气型：按确定性 5h 窗口切换，权重随季节（夏闷热暴雨多、冬阴雨多）
    private Regime regimeAt(long seed, LocalDateTime t) {
        return regimeAtSlot(seed, epochMinute(t) / REGIME_WINDOW_MIN, t);
    }

    private Regime regimeAtSlot(long seed, long slot, LocalDateTime t) {
        int summerish = t.getMonthValue() >= 5 && t.getMonthValue() <= 9 ? 1 : 0;
        int roll = floorHash(seed, slot, 100);
        if (summerish == 1) {
            if (roll < 30) return Regime.SUNNY;
            if (roll < 50) return Regime.MUGGY;
            if (roll < 68) return Regime.CLOUDY;
            if (roll < 88) return Regime.RAIN;
            return Regime.STORM;
        }
        if (roll < 30) return Regime.CLOUDY;
        if (roll < 55) return Regime.SUNNY;
        if (roll < 85) return Regime.RAIN;
        return Regime.STORM;
    }

    private double seasonalMeanC(LocalDateTime t) {
        double monthFloat = t.getMonthValue() - 1 + (t.getDayOfMonth() - 1) / 31.0;
        int lo = (int) Math.floor(monthFloat) % 12;
        int hi = (lo + 1) % 12;
        double frac = monthFloat - Math.floor(monthFloat);
        // 余弦插值平滑季节过渡
        double w = (1 - Math.cos(frac * Math.PI)) / 2;
        return MONTHLY_MEAN_C[lo] + (MONTHLY_MEAN_C[hi] - MONTHLY_MEAN_C[lo]) * w;
    }

    private double diurnalFactor(Regime regime) {
        return switch (regime) {
            case SUNNY -> 1.0;
            case MUGGY -> 0.7;
            case CLOUDY -> 0.5;
            case RAIN -> 0.35;
            case STORM -> 0.25;
        };
    }

    private double regimeTempOffset(Regime regime, LocalDateTime t) {
        boolean summer = t.getMonthValue() >= 5 && t.getMonthValue() <= 9;
        return switch (regime) {
            case SUNNY -> summer ? 2 : 0.5;
            case MUGGY -> summer ? 3 : 0;
            case CLOUDY -> -0.5;
            case RAIN -> -3;
            case STORM -> -4.5;
        };
    }

    private double regimeRhBase(Regime regime) {
        return switch (regime) {
            case SUNNY -> 55;
            case MUGGY -> 80;
            case CLOUDY -> 70;
            case RAIN -> 90;
            case STORM -> 95;
        };
    }

    // 天气型温湿修正：窗口边界处 45 分钟线性过渡（锋面过境平滑，避免数值跳变）
    private double regimeTempOffsetAt(long seed, LocalDateTime t) {
        long minute = epochMinute(t);
        long slot = minute / REGIME_WINDOW_MIN;
        Regime cur = regimeAtSlot(seed, slot, t);
        Regime prev = regimeAtSlot(seed, slot - 1, t);
        double blend = Math.min(1.0, (minute % REGIME_WINDOW_MIN) / (double) REGIME_TRANSITION_MIN);
        return lerp(regimeTempOffset(prev, t), regimeTempOffset(cur, t), blend);
    }

    private double regimeRhBaseAt(long seed, LocalDateTime t) {
        long minute = epochMinute(t);
        long slot = minute / REGIME_WINDOW_MIN;
        Regime cur = regimeAtSlot(seed, slot, t);
        Regime prev = regimeAtSlot(seed, slot - 1, t);
        double blend = Math.min(1.0, (minute % REGIME_WINDOW_MIN) / (double) REGIME_TRANSITION_MIN);
        return lerp(regimeRhBase(prev), regimeRhBase(cur), blend);
    }

    private double lerp(double a, double b, double w) {
        return a + (b - a) * w;
    }

    // ==================== 自然事件层（V2） ====================

    private static final long EVENT_WINDOW_MIN = 360;      // 自然事件窗口 6h
    private static final long EVENT_TRANSITION_MIN = 90;   // 事件边界 90 分钟缓动

    // 自然事件：6h 确定性窗口、约 25% 概率激活、类型按季节加权
    private NaturalEvent naturalEventAt(long seed, LocalDateTime t) {
        return naturalEventSlot(seed, epochMinute(t) / EVENT_WINDOW_MIN, t.getMonthValue());
    }

    private NaturalEvent naturalEventAtSlot(long seed, long slot, LocalDateTime t) {
        // 边界相邻窗口（±6h）季节一致，直接复用当前月份权重
        return naturalEventSlot(seed, slot, t.getMonthValue());
    }

    private NaturalEvent naturalEventSlot(long seed, long slot, int monthValue) {
        int roll = floorHash(seed ^ 0xE7E7L, slot, 100);
        boolean summer = monthValue >= 5 && monthValue <= 9;
        boolean winter = monthValue == 12 || monthValue <= 2;
        if (roll < 75) {
            return NaturalEvent.NONE;
        }
        int pick = floorHash(seed ^ 0x3E17L, slot, 100);
        if (summer) {
            if (pick < 35) return NaturalEvent.HEAT_WAVE;
            if (pick < 65) return NaturalEvent.PROLONGED_RAIN;
            if (pick < 85) return NaturalEvent.THUNDERSTORM;
            return NaturalEvent.SAUNA;
        }
        if (winter) {
            if (pick < 55) return NaturalEvent.COLD_SNAP;
            if (pick < 85) return NaturalEvent.PROLONGED_RAIN;
            return NaturalEvent.THUNDERSTORM;
        }
        if (pick < 45) return NaturalEvent.PROLONGED_RAIN;
        if (pick < 75) return NaturalEvent.THUNDERSTORM;
        return NaturalEvent.HEAT_WAVE;
    }

    // 事件温度修正（边界 90 分钟缓动）
    private double naturalEventTempOffsetAt(long seed, LocalDateTime t) {
        long minute = epochMinute(t);
        long slot = minute / EVENT_WINDOW_MIN;
        NaturalEvent cur = naturalEventAt(seed, t);
        NaturalEvent prev = naturalEventAtSlot(seed, slot - 1, t);
        double blend = Math.min(1.0, (minute % EVENT_WINDOW_MIN) / (double) EVENT_TRANSITION_MIN);
        return lerp(eventTempOffset(prev), eventTempOffset(cur), blend);
    }

    private double naturalEventRhBiasAt(long seed, LocalDateTime t) {
        long minute = epochMinute(t);
        long slot = minute / EVENT_WINDOW_MIN;
        NaturalEvent cur = naturalEventAt(seed, t);
        NaturalEvent prev = naturalEventAtSlot(seed, slot - 1, t);
        double blend = Math.min(1.0, (minute % EVENT_WINDOW_MIN) / (double) EVENT_TRANSITION_MIN);
        return lerp(eventRhBias(prev), eventRhBias(cur), blend);
    }

    private double eventTempOffset(NaturalEvent event) {
        return switch (event) {
            case COLD_SNAP -> -10;
            case HEAT_WAVE -> 7;
            case PROLONGED_RAIN -> -2;
            case THUNDERSTORM -> -5;
            case SAUNA -> 2;
            case NONE -> 0;
        };
    }

    private double eventRhBias(NaturalEvent event) {
        return switch (event) {
            case COLD_SNAP -> -5;
            case HEAT_WAVE -> -15;
            case PROLONGED_RAIN -> 10;
            case THUNDERSTORM -> 8;
            case SAUNA -> 12;
            case NONE -> 0;
        };
    }

    // 雨系事件强制雨型（连阴雨→雨、雷暴→暴雨·季风），其余不影响天气型
    private Regime naturalEventRegimeBias(NaturalEvent event, Regime regime, long seed, LocalDateTime t) {
        long minute = epochMinute(t);
        long slot = minute / EVENT_WINDOW_MIN;
        NaturalEvent cur = naturalEventAtSlot(seed, slot, t);
        double blend = Math.min(1.0, (minute % EVENT_WINDOW_MIN) / (double) EVENT_TRANSITION_MIN);
        Regime forced = switch (event) {
            case PROLONGED_RAIN -> Regime.RAIN;
            case THUNDERSTORM -> Regime.STORM;
            default -> regime;
        };
        if (forced == regime || blend >= 1.0) {
            return blend >= 1.0 ? forced : regime;
        }
        // 过渡期：概率混入强制雨型（确定性）
        return floorHash(seed ^ 0xF0F0L, minute / EVENT_TRANSITION_MIN, 2) < (int) (blend * 2)
                ? forced : regime;
    }

    // 平滑值噪声：整点格点哈希 + 余弦插值
    private double smoothNoise(long seed, long x, int lattice) {
        long x0 = x / lattice * lattice;
        long x1 = x0 + lattice;
        double a = unitHash(seed, x0);
        double b = unitHash(seed, x1);
        double w = (double) (x - x0) / lattice;
        double k = (1 - Math.cos(w * Math.PI)) / 2;
        return a + (b - a) * k;
    }

    private double unitHash(long seed, long x) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        // 取高 16 位映射到 [-1, 1)
        return ((h >>> 48) & 0xFFFF) / 32768.0 - 1.0;
    }

    private int floorHash(long seed, long x, int mod) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (int) (Math.floorMod(h, mod));
    }

    private long epochMinute(LocalDateTime t) {
        return t.atZone(java.time.ZoneOffset.UTC).toEpochSecond() / 60;
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private double round4(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}
