package com.dcim.simulator.device;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 类功能:室内热湿平衡物理引擎——机房环境的一等状态机
 *
 * 【模型】每机房一个持久化状态（RoomEnvironmentState：室内温/湿/空调运行位），
 *   每秒对净热流积分：围护导热 + IT 负载发热 + 事件热流（火灾/负载激增）− 空调制冷；
 *   湿度向室外渗透松弛，空调制冷时冷凝除湿。空调按设定值 ±0.5℃ 回差启停。
 * 【变化值/真实值】事件与参数只写"变化值"（驱动侧：IT 负载斜坡、事件热流、空调设定），
 *   "真实值"（室内 T/RH）由热容惯性对变化值积分演化——事件永不直接赋值环境状态。
 * 【持久化】重启从 last_tick_at 续积分（断档按 5s 步长补算、上限 6h），
 *   绝不重置——设备发热/空调动作/事件的影响因此持久。
 * 【接管指标】temperature/humidity/supply_air_temp/return_air_temp/power_kw
 *   由室内状态派生，不再走环境通道；启动时清理这些指标的遗留通道。
 * 【演练】AC_OFF/AC_ON、FIRE 火灾（越常规钳位至 120℃）、IT_SPIKE 负载激增（梯形波）、
 *   NATURAL_* 人工自然事件注入（寒潮/热浪/连阴雨）——均为流/参数作用，内存态限时。
 * */
@Slf4j
@Component
public class RoomPhysicsEngine {

    /** 物理引擎接管的环境指标（设备读室内状态派生，通道表单禁保存） */
    public static final Set<String> PHYSICS_POINTS = Set.of(
            "temperature", "humidity", "supply_air_temp", "return_air_temp", "power_kw");

    // 热工常量（简化集中参数模型）
    private static final double ENVELOPE_UA_W_PER_K = 800;      // 围护导热系数积
    private static final double THERMAL_CAPACITY_J_PER_K = 3.0e6; // 空气+机柜有效热容
    private static final double AC_COP = 3.0;                    // 制冷能效比（耗电=制冷/COP）
    private static final double AC_SUPPLY_DELTA_C = 8;           // 送风温差
    private static final double RETURN_AIR_OFFSET_C = 1.5;       // 回风温升
    private static final double RH_VENT_RATE_PER_SEC = 5e-4;     // 室内外湿渗透（约 33min 时间常数）
    private static final double AC_DEHUMID_PER_SEC = 0.02;       // 制冷冷凝除湿 %/s

    private static final double TEMP_CLAMP_MIN = -5;
    private static final double TEMP_CLAMP_MAX = 55;             // 常规仿真护栏
    private static final double FIRE_TEMP_MAX = 120;             // 火灾演练越限上限
    private static final double FIRE_KW = 300;
    private static final double RH_CLAMP_MIN = 10;
    private static final double RH_CLAMP_MAX = 100;

    private static final double STEP_SEC = 5.0;
    private static final long MAX_CATCHUP_SEC = 6 * 3600L;

    private static final double IT_SLEW_KW_PER_SEC = 5.0 / 60;   // IT 负载斜率：5kW/分钟

    private final RoomEnvironmentProfileRepository profileRepository;
    private final RoomEnvironmentStateRepository stateRepository;
    private final EnvironmentChannelRepository channelRepository;
    private final WeatherService weatherService;

    // 每机房最新快照（供设备采样与管理页读取）
    private final Map<Long, RoomSnapshot> snapshotByProfile = new ConcurrentHashMap<>();
    // 火灾演练截止时刻（内存态：重启即灭火）
    private final Map<Long, LocalDateTime> fireUntilByProfile = new ConcurrentHashMap<>();
    // 每机房积分锁（按需补算防并发）
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();
    // IT 负载斜坡追踪（变化值→真实值：任何来源的负载变化以斜率生效）
    private final Map<Long, Double> effectiveItByProfile = new ConcurrentHashMap<>();
    // 设备事件流：负载激增（梯形热流）
    private final Map<Long, ItSpike> spikeByProfile = new ConcurrentHashMap<>();
    // 人工自然事件注入（演练用，内存态限时）
    private final Map<Long, ManualNatural> manualNaturalByProfile = new ConcurrentHashMap<>();

    public RoomPhysicsEngine(RoomEnvironmentProfileRepository profileRepository,
                             RoomEnvironmentStateRepository stateRepository,
                             EnvironmentChannelRepository channelRepository,
                             WeatherService weatherService) {
        this.profileRepository = profileRepository;
        this.stateRepository = stateRepository;
        this.channelRepository = channelRepository;
        this.weatherService = weatherService;
    }

    /** 机房环境实时快照（真实值 + 变化值两侧） */
    public record RoomSnapshot(long cloudRoomId, WeatherService.Regime regime,
                               double tempOutC, double rhOut,
                               double tempInC, double rhIn,
                               boolean acEnabled, boolean acRunning,
                               double itLoadKw, double acSetpointC, double acSetpointRh, double acRatedKw,
                               double supplyAirTempC, double returnAirTempC, double powerKw,
                               // 变化值（驱动侧）
                               double effectiveItKw, double spikeKw, double fireKw,
                               double equilibriumTempC,
                               String naturalEventLabel, String manualEventLabel) {
    }

    /** 设备事件流：IT 负载激增（爬坡 10min → 保持 → 卸坡 10min 的梯形热流） */
    public record ItSpike(double deltaKw, LocalDateTime fullAt, LocalDateTime holdUntil, LocalDateTime endAt) {
    }

    /** 人工自然事件注入（演练） */
    public record ManualNatural(String label, double tempOffset, double rhBias, boolean forceRain,
                                LocalDateTime startAt, LocalDateTime until) {
    }

    /** 环境积分节拍：每秒推进所有机房（断档自动补算） */
    @Scheduled(fixedDelay = 1000)
    public void tick() {
        for (RoomEnvironmentProfile profile : profileRepository.findAll()) {
            try {
                integrateToNow(profile);
            } catch (Exception ex) {
                log.error("机房环境积分失败 profileId={}", profile.getId(), ex);
            }
        }
    }

    /** 启动迁移：清理被物理引擎接管的遗留环境通道（温湿度/送回风/功率） */
    @EventListener(ApplicationReadyEvent.class)
    public void migratePhysicsOwnedChannels() {
        for (RoomEnvironmentProfile profile : profileRepository.findAll()) {
            List<EnvironmentChannel> stale = channelRepository.findByProfileId(profile.getId()).stream()
                    .filter(c -> PHYSICS_POINTS.contains(c.getPointCode()))
                    .toList();
            if (!stale.isEmpty()) {
                channelRepository.deleteAll(stale);
                log.info("环境物理引擎接管 {} 个指标，清理遗留通道 profileId={} points={}",
                        stale.size(), profile.getId(),
                        stale.stream().map(EnvironmentChannel::getPointCode).distinct().toList());
            }
        }
    }

    /** 读某机房的物理派生指标值（设备采样用）；机房尚无快照时返回空 */
    public OptionalDouble roomPoint(long profileId, String pointCode) {
        RoomSnapshot snapshot = snapshotByProfile.get(profileId);
        if (snapshot == null) {
            return OptionalDouble.empty();
        }
        return switch (pointCode) {
            case "temperature" -> OptionalDouble.of(snapshot.tempInC());
            case "humidity" -> OptionalDouble.of(snapshot.rhIn());
            case "supply_air_temp" -> OptionalDouble.of(snapshot.supplyAirTempC());
            case "return_air_temp" -> OptionalDouble.of(snapshot.returnAirTempC());
            case "power_kw" -> OptionalDouble.of(snapshot.powerKw());
            default -> OptionalDouble.empty();
        };
    }

    public Optional<RoomSnapshot> snapshotOf(long cloudRoomId) {
        return profileRepository.findByCloudRoomId(cloudRoomId)
                .map(profile -> {
                    integrateToNow(profile);
                    return snapshotByProfile.get(profile.getId());
                });
    }

    /** 演练：空调停机/恢复（持久化到档案参数） */
    public void setAcEnabled(long cloudRoomId, boolean enabled) {
        profileRepository.findByCloudRoomId(cloudRoomId).ifPresent(profile -> {
            profile.setAcEnabled(enabled);
            profileRepository.save(profile);
            log.info("空调{}演练 cloudRoomId={}", enabled ? "恢复" : "停机", cloudRoomId);
        });
    }

    /** 演练：火灾热注入（内存态，durationSec 秒后自然熄灭） */
    public void fireDrill(long cloudRoomId, int durationSec) {
        profileRepository.findByCloudRoomId(cloudRoomId).ifPresent(profile ->
                fireUntilByProfile.put(profile.getId(), LocalDateTime.now().plusSeconds(durationSec)));
    }

    /** 演练：IT 负载激增（梯形波：10min 爬坡 → 保持 holdSec → 10min 卸坡） */
    public void itSpikeDrill(long cloudRoomId, double deltaKw, int holdSec) {
        profileRepository.findByCloudRoomId(cloudRoomId).ifPresent(profile -> {
            LocalDateTime now = LocalDateTime.now();
            spikeByProfile.put(profile.getId(), new ItSpike(deltaKw,
                    now.plusMinutes(10), now.plusMinutes(10).plusSeconds(holdSec),
                    now.plusMinutes(10).plusSeconds(holdSec).plusMinutes(10)));
            log.info("负载激增演练 cloudRoomId={} +{}kW hold={}s", cloudRoomId, deltaKw, holdSec);
        });
    }

    /** 演练：人工自然事件注入（寒潮/热浪/连阴雨，限时缓动，内存态） */
    public void naturalEventDrill(long cloudRoomId, String kind, int durationSec) {
        profileRepository.findByCloudRoomId(cloudRoomId).ifPresent(profile -> {
            LocalDateTime now = LocalDateTime.now();
            ManualNatural manual = switch (kind) {
                case "COLD_SNAP" -> new ManualNatural("寒潮(人工)", -10, -5, false, now, now.plusSeconds(durationSec));
                case "HEAT_WAVE" -> new ManualNatural("热浪(人工)", 7, -10, false, now, now.plusSeconds(durationSec));
                case "PROLONGED_RAIN" -> new ManualNatural("连阴雨(人工)", -2, 10, true, now, now.plusSeconds(durationSec));
                default -> null;
            };
            if (manual != null) {
                manualNaturalByProfile.put(profile.getId(), manual);
                log.info("人工自然事件注入 cloudRoomId={} {} durationSec={}", cloudRoomId, kind, durationSec);
            }
        });
    }

    public boolean isFireActive(long profileId) {
        LocalDateTime until = fireUntilByProfile.get(profileId);
        return until != null && until.isAfter(LocalDateTime.now());
    }

    // ==================== 积分核心 ====================

    private void integrateToNow(RoomEnvironmentProfile profile) {
        synchronized (locks.computeIfAbsent(profile.getId(), k -> new Object())) {
            RoomEnvironmentState state = stateRepository.findByProfileId(profile.getId())
                    .orElseGet(() -> initState(profile));
            LocalDateTime now = LocalDateTime.now();
            long gapSec = Duration.between(state.getLastTickAt(), now).getSeconds();
            if (gapSec < 0) {
                // 时钟回拨：只推进时间戳，防止负积分
                state.setLastTickAt(now);
                stateRepository.save(state);
                return;
            }
            long simSec = Math.min(gapSec, MAX_CATCHUP_SEC);
            int steps = (int) Math.max(1, Math.ceil(simSec / STEP_SEC));
            double dt = (double) simSec / steps;
            LocalDateTime t = state.getLastTickAt();
            double effectiveIt = effectiveItByProfile.computeIfAbsent(profile.getId(),
                    k -> profile.itLoadKwOf());
            for (int i = 0; i < steps; i++) {
                t = t.plusSeconds((long) dt);
                WeatherService.Weather weather = applyManualNatural(
                        weatherService.snapshot(profile.getSeed(), t), manualNaturalByProfile.get(profile.getId()), now);
                // IT 负载斜坡：任何来源的参数变化以 5kW/分钟平滑生效（变化值→真实值）
                double target = profile.itLoadKwOf();
                double slew = IT_SLEW_KW_PER_SEC * dt;
                effectiveIt += clamp(target - effectiveIt, -slew, slew);
                double spikeKw = spikeKwAt(profile.getId(), t);
                double fireKw = isFireActive(profile.getId()) ? FIRE_KW : 0;
                step(state, profile, weather, dt, fireKw, spikeKw, effectiveIt);
            }
            effectiveItByProfile.put(profile.getId(), effectiveIt);
            state.setLastTickAt(now);
            stateRepository.save(state);
            snapshotByProfile.put(profile.getId(),
                    buildSnapshot(profile, state, now, effectiveIt, spikeKwAt(profile.getId(), now)));
        }
    }

    // 单步积分（纯函数便于单测）：事件一律以热流 kW 传入，绝不直接赋值温度
    static void step(RoomEnvironmentState state, RoomEnvironmentProfile profile,
                     WeatherService.Weather weather, double dtSec,
                     double fireKw, double spikeKw, double effectiveItKw) {
        // 空调回差控制（总开关关闭时强制停机）
        boolean acOn = profile.acEnabledOf();
        if (acOn) {
            if (!state.isAcRunning() && state.getIndoorTemp() > profile.acSetpointCOf() + 0.5) {
                state.setAcRunning(true);
            } else if (state.isAcRunning() && state.getIndoorTemp() < profile.acSetpointCOf() - 0.5) {
                state.setAcRunning(false);
            }
        } else {
            state.setAcRunning(false);
        }
        double coolKw = state.isAcRunning() ? profile.acRatedKwOf() : 0;

        // 热平衡：围护导热 + IT 负载（含斜坡/激增）+ 事件热流 − 空调制冷
        double gainW = ENVELOPE_UA_W_PER_K * (weather.tempOutC() - state.getIndoorTemp())
                + effectiveItKw * 1000 + (fireKw + spikeKw) * 1000;
        state.setIndoorTemp(state.getIndoorTemp() + (gainW - coolKw * 1000) * dtSec / THERMAL_CAPACITY_J_PER_K);

        // 湿平衡：向室外渗透 + 制冷冷凝除湿
        state.setIndoorRh(state.getIndoorRh()
                + (weather.rhOut() - state.getIndoorRh()) * RH_VENT_RATE_PER_SEC * dtSec
                - (state.isAcRunning() ? AC_DEHUMID_PER_SEC * dtSec : 0));

        // 钳位：常规护栏 55℃；火灾演练越限至上限 120℃
        state.setIndoorTemp(clamp(state.getIndoorTemp(), TEMP_CLAMP_MIN,
                fireKw > 0 ? FIRE_TEMP_MAX : TEMP_CLAMP_MAX));
        state.setIndoorRh(clamp(state.getIndoorRh(), RH_CLAMP_MIN, RH_CLAMP_MAX));
    }

    // 负载激增梯形波：爬坡 10min → 保持 → 卸坡 10min，窗口外为 0
    private double spikeKwAt(Long profileId, LocalDateTime t) {
        ItSpike spike = spikeByProfile.get(profileId);
        if (spike == null) {
            return 0;
        }
        if (!t.isBefore(spike.fullAt()) && !t.isAfter(spike.holdUntil())) {
            return spike.deltaKw();
        }
        if (t.isBefore(spike.fullAt())) {
            long ramp = Duration.between(spike.fullAt().minusMinutes(10), t).getSeconds();
            return ramp <= 0 ? 0 : spike.deltaKw() * ramp / 600.0;
        }
        long down = Duration.between(t, spike.endAt()).getSeconds();
        return down <= 0 ? 0 : spike.deltaKw() * down / 600.0;
    }

    // 人工自然事件叠加：起止各 5 分钟缓动（变化值以函数作用于气象，不直接改状态）
    private WeatherService.Weather applyManualNatural(WeatherService.Weather weather,
                                                      ManualNatural manual, LocalDateTime now) {
        if (manual == null || manual.until().isBefore(now)) {
            return weather;
        }
        long elapsed = Duration.between(manual.startAt(), now).getSeconds();
        long remain = Duration.between(now, manual.until()).getSeconds();
        double ease = clamp(Math.min(elapsed / 300.0, remain / 300.0), 0, 1);
        double temp = clamp(weather.tempOutC() + manual.tempOffset() * ease, -8, 42);
        double rh = clamp(weather.rhOut() + manual.rhBias() * ease, 20, 100);
        WeatherService.Regime regime = manual.forceRain() && ease > 0.5
                ? WeatherService.Regime.RAIN : weather.regime();
        return new WeatherService.Weather(regime, temp, rh, weather.event());
    }

    private RoomEnvironmentState initState(RoomEnvironmentProfile profile) {
        WeatherService.Weather weather = weatherService.snapshot(profile.getSeed(), LocalDateTime.now());
        RoomEnvironmentState state = new RoomEnvironmentState();
        state.setProfileId(profile.getId());
        // 初值仅作基准：机房启用时接近室外温度，此后永不重置
        state.setIndoorTemp(clamp(weather.tempOutC(), 15, 30));
        state.setIndoorRh(60);
        state.setAcRunning(false);
        state.setLastTickAt(LocalDateTime.now());
        log.info("机房环境状态初始化 profileId={} T={}℃ RH=60%", profile.getId(), state.getIndoorTemp());
        return state;
    }

    private RoomSnapshot buildSnapshot(RoomEnvironmentProfile profile, RoomEnvironmentState state,
                                       LocalDateTime now, double effectiveItKw, double spikeKw) {
        WeatherService.Weather weather = applyManualNatural(
                weatherService.snapshot(profile.getSeed(), now), manualNaturalByProfile.get(profile.getId()), now);
        double fireKw = isFireActive(profile.getId()) ? FIRE_KW : 0;
        double acElectricKw = state.isAcRunning() ? profile.acRatedKwOf() / AC_COP : 0;
        double supply = state.isAcRunning()
                ? state.getIndoorTemp() - AC_SUPPLY_DELTA_C
                : state.getIndoorTemp() + 0.5;
        double totalItKw = effectiveItKw + spikeKw;
        double powerKw = Math.max(0, totalItKw + acElectricKw);
        // 无空调平衡温度（变化值）：空调失效时机房的去向
        double equilibrium = weather.tempOutC()
                + (totalItKw * 1000 + fireKw * 1000) / ENVELOPE_UA_W_PER_K;
        ManualNatural manual = manualNaturalByProfile.get(profile.getId());
        String manualLabel = manual != null && manual.until().isAfter(now) ? manual.label() : "";
        return new RoomSnapshot(profile.getCloudRoomId(), weather.regime(),
                weather.tempOutC(), weather.rhOut(),
                state.getIndoorTemp(), state.getIndoorRh(),
                profile.acEnabledOf(), state.isAcRunning(),
                profile.itLoadKwOf(), profile.acSetpointCOf(), profile.acSetpointRhOf(), profile.acRatedKwOf(),
                supply, state.getIndoorTemp() + RETURN_AIR_OFFSET_C, powerKw,
                effectiveItKw, spikeKw, fireKw, equilibrium,
                weather.event() == WeatherService.NaturalEvent.NONE ? "" : weather.event().getLabel(),
                manualLabel);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
