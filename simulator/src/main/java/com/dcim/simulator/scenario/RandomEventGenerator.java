package com.dcim.simulator.scenario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.SplittableRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 随机事件生成器：权重 + 冷却 + 种子化，产出 SET_CHANNEL 事件写入环境通道
 *
 * 同种子两次运行事件序列一致（确定性回归）。事件库内置（温度骤升/骤降/湿度漂移），
 * 冷却窗口防止同一事件连续刷屏。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RandomEventGenerator {

    private final ScenarioPlayer scenarioPlayer;
    private final com.dcim.simulator.device.RoomEnvironmentProfileRepository profileRepository;
    private final com.dcim.simulator.device.EnvironmentChannelRepository channelRepository;

    @Value("${simulator.seed:42}")
    private long seed;

    @Value("${simulator.random-event.enabled:true}")
    private boolean enabled;

    @Value("${simulator.random-event.min-interval-sec:300}")
    private int minIntervalSec;

    private SplittableRandom random;
    private final AtomicLong lastEventAtEpoch = new AtomicLong(0);

    /** 内置事件库：名称 / 目标指标 / 目标基值 / 权重 */
    private static final List<EventDef> EVENTS = List.of(
            new EventDef("温度骤升", "temperature", 32.0, 3),
            new EventDef("温度骤降", "temperature", 16.0, 2),
            new EventDef("湿度漂移", "humidity", 70.0, 2));

    private record EventDef(String name, String pointCode, double baseValue, int weight) {
    }

    /**
     * 事件节拍：每 30s 掷一次骰子（冷却窗口 + 加权抽取）
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 60_000)
    public void tick() {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        long last = lastEventAtEpoch.get();
        if (now - last < minIntervalSec) {
            return;
        }
        if (random == null) {
            random = new SplittableRandom(seed);
        }
        // 每个节拍 10% 触发概率：小时级事件密度（30s 节拍 → 期望约 5 分钟一次，受冷却约束）
        if (random.nextInt(10) != 0) {
            return;
        }
        if (!lastEventAtEpoch.compareAndSet(last, now)) {
            return;
        }
        fireRandomEvent();
    }

    /**
     * 触发一次加权随机事件（写环境通道 STEP，60 秒后由新基值覆盖回归）
     */
    void fireRandomEvent() {
        int totalWeight = EVENTS.stream().mapToInt(EventDef::weight).sum();
        int pick = random.nextInt(totalWeight);
        EventDef chosen = null;
        for (EventDef event : EVENTS) {
            pick -= event.weight();
            if (pick < 0) {
                chosen = event;
                break;
            }
        }
        if (chosen == null) {
            return;
        }
        final EventDef selected = chosen;
        profileRepository.findAll().stream().findFirst().ifPresent(profile ->
                channelRepository.findByProfileIdAndPointCode(profile.getId(), selected.pointCode())
                        .stream().findFirst().ifPresent(channel -> {
                            channel.setFuncType("STEP");
                            channel.setStepTo(java.math.BigDecimal.valueOf(selected.baseValue()));
                            channel.setStepAt(LocalDateTime.now());
                            channelRepository.save(channel);
                            log.info("随机事件触发: {} → {}={}", selected.name(),
                                    selected.pointCode(), selected.baseValue());
                        }));
    }
}
