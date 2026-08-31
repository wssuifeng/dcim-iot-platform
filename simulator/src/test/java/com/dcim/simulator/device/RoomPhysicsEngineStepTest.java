package com.dcim.simulator.device;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/*
 * 类功能:RoomPhysicsEngine 单步积分纯函数测试
 * 覆盖：空调制冷收敛到设定值、停机后 IT 发热升温、常规钳位 55℃、
 *   火灾越限至 120℃ 上限、制冷除湿、热平衡方向（室外更冷时散热）
 * */
@DisplayName("RoomPhysicsEngine 单步积分测试")
class RoomPhysicsEngineStepTest {

    private static final WeatherService.Weather SUMMER =
            new WeatherService.Weather(WeatherService.Regime.SUNNY, 33, 70, WeatherService.NaturalEvent.NONE);

    private RoomEnvironmentProfile profile(double itKw, double acKw, boolean acEnabled) {
        RoomEnvironmentProfile profile = new RoomEnvironmentProfile();
        profile.setItLoadKw(itKw);
        profile.setAcRatedKw(acKw);
        profile.setAcSetpointC(24.0);
        profile.setAcSetpointRh(55.0);
        profile.setAcEnabled(acEnabled);
        return profile;
    }

    private RoomEnvironmentState state(double temp, double rh, boolean acRunning) {
        RoomEnvironmentState state = new RoomEnvironmentState();
        state.setIndoorTemp(temp);
        state.setIndoorRh(rh);
        state.setAcRunning(acRunning);
        state.setLastTickAt(java.time.LocalDateTime.now());
        return state;
    }

    // 连续积分 N 步（每步 5s，模拟 5*N 秒）
    private List<Double> run(RoomEnvironmentState state, RoomEnvironmentProfile profile,
                             double fireKw, int steps) {
        List<Double> temps = new ArrayList<>();
        double itKw = profile.itLoadKwOf();
        for (int i = 0; i < steps; i++) {
            RoomPhysicsEngine.step(state, profile, SUMMER, 5.0, fireKw, 0, itKw);
            temps.add(state.getIndoorTemp());
        }
        return temps;
    }

    @Test
    @DisplayName("空调开启：从 30℃ 收敛到设定 24℃ 附近（回差停机）")
    void should_coolTowardSetpoint() {
        RoomEnvironmentState state = state(30, 60, true);
        run(state, profile(40, 60, true), 0, 2400); // 约 3.3 小时
        assertThat(state.getIndoorTemp()).isCloseTo(24.0, within(1.0));
        assertThat(state.isAcRunning()).isFalse(); // 已低于回差下沿，压缩机停
    }

    @Test
    @DisplayName("空调停机：IT 发热驱动机房持续升温（约 0.8℃/分钟）")
    void should_heatUp_whenAcDisabled() {
        RoomEnvironmentState state = state(24, 60, false);
        double start = state.getIndoorTemp();
        run(state, profile(40, 60, false), 0, 240); // 20 分钟
        assertThat(state.getIndoorTemp()).isGreaterThan(start + 10);
    }

    @Test
    @DisplayName("常规钳位：长时间无制冷不超 55℃")
    void should_clampAt55() {
        RoomEnvironmentState state = state(24, 60, false);
        run(state, profile(40, 60, false), 0, 8640); // 12 小时
        assertThat(state.getIndoorTemp()).isLessThanOrEqualTo(55.0);
    }

    @Test
    @DisplayName("火灾演练：热注入可越过常规钳位但不超 120℃")
    void should_exceedClampDuringFire() {
        RoomEnvironmentState state = state(24, 60, false);
        List<Double> temps = run(state, profile(40, 60, false), 300, 2400);
        assertThat(temps.stream().max(Double::compare)).hasValueSatisfying(v ->
                assertThat(v).isBetween(55.0, 120.0));
    }

    @Test
    @DisplayName("制冷除湿：高湿机房在空调运行下 RH 下降")
    void should_dehumidify_whenAcRunning() {
        RoomEnvironmentState state = state(30, 85, true);
        double startRh = state.getIndoorRh();
        run(state, profile(40, 60, true), 0, 1200); // 100 分钟
        assertThat(state.getIndoorRh()).isLessThan(startRh - 10);
    }

    @Test
    @DisplayName("围护散热：室外低温时室内向室外散热（冬天不升温）")
    void should_loseHeat_whenOutdoorColder() {
        WeatherService.Weather winter = new WeatherService.Weather(WeatherService.Regime.RAIN, 5, 90, WeatherService.NaturalEvent.PROLONGED_RAIN);
        RoomEnvironmentState state = state(24, 60, false);
        // IT 负载很低（夜间值班负载），室外 5℃
        RoomEnvironmentProfile profile = profile(2, 60, false);
        RoomPhysicsEngine.step(state, profile, winter, 5.0, 0, 0, 2);
        assertThat(state.getIndoorTemp()).isLessThan(24.0);
    }
}
