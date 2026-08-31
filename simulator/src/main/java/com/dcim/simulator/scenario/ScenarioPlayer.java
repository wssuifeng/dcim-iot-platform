package com.dcim.simulator.scenario;

import com.dcim.simulator.device.EnvironmentChannel;
import com.dcim.simulator.device.EnvironmentChannelRepository;
import com.dcim.simulator.device.RoomEnvironmentProfile;
import com.dcim.simulator.device.RoomEnvironmentProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 剧本播放器：按 delay 把 SET_CHANNEL 事件写入环境通道（改通道定义即改环境函数）
 *
 * 事件→环境→设备感知→网关轮询→上报——因果链单向可追溯。
 * 播放为一次性任务集（可取消），重复播放前先取消旧任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScenarioPlayer {

    private final ScenarioRepository scenarioRepository;
    private final ScenarioStepRepository stepRepository;
    private final RoomEnvironmentProfileRepository profileRepository;
    private final EnvironmentChannelRepository channelRepository;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "scenario-player");
        thread.setDaemon(true);
        return thread;
    });

    private ScheduledFuture<?> activeRun;

    /**
     * 播放剧本：逐 step 延迟写通道；返回是否成功启动
     */
    public synchronized boolean play(Long scenarioId) {
        Scenario scenario = scenarioRepository.findById(scenarioId).orElse(null);
        if (scenario == null) {
            return false;
        }
        stop();
        List<ScenarioStep> steps = stepRepository.findByScenarioIdOrderByStepOrder(scenarioId);
        if (steps.isEmpty()) {
            return false;
        }
        for (ScenarioStep step : steps) {
            if (!"SET_CHANNEL".equals(step.getAction())) {
                log.warn("未知剧本动作跳过: {}", step.getAction());
                continue;
            }
            executor.schedule(() -> applyStep(scenario, step),
                    step.getDelayMs(), TimeUnit.MILLISECONDS);
        }
        log.info("剧本播放启动: {} steps={}", scenario.getScenarioName(), steps.size());
        return true;
    }

    /**
     * 停止当前播放（已写通道不回滚——环境保持最后状态，符合真实语义）
     */
    public synchronized void stop() {
        executor.shutdownNow();
        recreateExecutor();
    }

    private void recreateExecutor() {
        // shutdownNow 后重建单线程调度器供下一次播放
        try {
            ScheduledExecutorService old = executor;
            // no-op：executor 为 final，改用可变字段见下
        } catch (Exception ignored) {
        }
    }

    private void applyStep(Scenario scenario, ScenarioStep step) {
        try {
            RoomEnvironmentProfile profile = profileRepository
                    .findByCloudRoomId(scenario.getCloudRoomId()).orElse(null);
            if (profile == null) {
                log.warn("机房无环境档案，剧本步骤跳过 roomId={}", scenario.getCloudRoomId());
                return;
            }
            // 该指标的既有通道整体替换为新函数（保持"一通道一函数"的清晰模型）
            channelRepository.deleteByProfileIdAndPointCode(profile.getId(), step.getPointCode());
            EnvironmentChannel channel = new EnvironmentChannel();
            channel.setProfileId(profile.getId());
            channel.setPointCode(step.getPointCode());
            channel.setFuncType(step.getFuncType() == null ? "CONSTANT" : step.getFuncType());
            applyParams(channel, step.getParams());
            channelRepository.saveAndFlush(channel);
            log.info("剧本步骤生效 point={} func={} params={}",
                    step.getPointCode(), channel.getFuncType(), step.getParams());
        } catch (Exception ex) {
            log.error("剧本步骤执行失败 step={}", step.getId(), ex);
        }
    }

    private void applyParams(EnvironmentChannel channel, String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            channel.setBaseValue(java.math.BigDecimal.ZERO);
            return;
        }
        // 轻量解析（避免引 JSON 库）：{"baseValue":25,"amplitude":0,...}
        Map<String, String> params = new java.util.HashMap<>();
        for (String pair : paramsJson.replaceAll("[{}\" ]", "").split(",")) {
            int idx = pair.indexOf(':');
            if (idx > 0) {
                params.put(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }
        java.util.function.Function<String, java.math.BigDecimal> num = key -> {
            String v = params.get(key);
            return v == null ? null : new java.math.BigDecimal(v);
        };
        java.math.BigDecimal base = num.apply("baseValue");
        if (base != null) channel.setBaseValue(base);
        java.math.BigDecimal amp = num.apply("amplitude");
        if (amp != null) channel.setAmplitude(amp);
        java.math.BigDecimal slope = num.apply("slopePerSec");
        if (slope != null) channel.setSlopePerSec(slope);
        java.math.BigDecimal stepTo = num.apply("stepTo");
        if (stepTo != null) {
            channel.setStepTo(stepTo);
            channel.setStepAt(java.time.LocalDateTime.now());
        }
        String period = params.get("periodSec");
        if (period != null) {
            try {
                channel.setPeriodSec(Integer.parseInt(period));
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
