package com.dcim.simulator.device;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 类功能:WeatherService 单元测试
 * 覆盖：确定性（同种子同时刻恒定）、季节性（7月均值高于1月）、
 *   天气型全覆盖、钳位、时间连续性（相邻分钟无跳变）
 * */
@DisplayName("WeatherService 单元测试")
class WeatherServiceTest {

    private final WeatherService weatherService = new WeatherService();
    private static final long SEED = 42L;

    @Test
    @DisplayName("同种子同时刻两次求值结果完全一致（可回放）")
    void should_beDeterministic() {
        LocalDateTime t = LocalDateTime.of(2026, 7, 15, 14, 0);
        WeatherService.Weather w1 = weatherService.snapshot(SEED, t);
        WeatherService.Weather w2 = weatherService.snapshot(SEED, t);
        assertThat(w1).isEqualTo(w2);
    }

    @Test
    @DisplayName("季节性：7月样本均温显著高于1月")
    void should_beHotterInJulyThanJanuary() {
        double julyMean = meanTemp(7);
        double janMean = meanTemp(1);
        assertThat(julyMean).as("南昌7月均温应远高于1月").isGreaterThan(janMean + 10);
    }

    private double meanTemp(int month) {
        double sum = 0;
        int n = 0;
        for (int day = 1; day <= 28; day++) {
            for (int hour = 0; hour < 24; hour += 3) {
                sum += weatherService.snapshot(SEED, LocalDateTime.of(2026, month, day, hour, 0)).tempOutC();
                n++;
            }
        }
        return sum / n;
    }

    @Test
    @DisplayName("全年样本温度湿度均在钳位内，天气型覆盖 3 种以上")
    void should_clampAndVaryRegimes() {
        Set<WeatherService.Regime> regimes = new HashSet<>();
        for (int day = 1; day <= 30; day++) {
            for (int hour = 0; hour < 24; hour += 2) {
                WeatherService.Weather w = weatherService.snapshot(SEED, LocalDateTime.of(2026, 6, day, hour, 17));
                assertThat(w.tempOutC()).isBetween(-8.0, 42.0);
                assertThat(w.rhOut()).isBetween(20.0, 100.0);
                regimes.add(w.regime());
            }
        }
        assertThat(regimes.size()).as("一个月内应出现多种天气型").isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("时间连续性：相邻分钟温度变化不超过 2℃（无数值跳变）")
    void should_beContinuousOverTime() {
        LocalDateTime t = LocalDateTime.of(2026, 4, 10, 9, 0);
        double prev = weatherService.snapshot(SEED, t).tempOutC();
        for (int i = 1; i <= 30; i++) {
            double cur = weatherService.snapshot(SEED, t.plusMinutes(i)).tempOutC();
            assertThat(Math.abs(cur - prev)).as("第 %d 分钟", i).isLessThan(2.0);
            prev = cur;
        }
    }

    @Test
    @DisplayName("不同种子同场景天气不同（机房间差异化）")
    void should_differAcrossSeeds() {
        LocalDateTime t = LocalDateTime.of(2026, 8, 20, 15, 30);
        WeatherService.Weather w1 = weatherService.snapshot(1L, t);
        WeatherService.Weather w2 = weatherService.snapshot(2L, t);
        assertThat(w1).isNotEqualTo(w2);
    }
}
