package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.dto.AlarmTriggerContext;
import com.example.platform.dto.AlarmTriggerResult;
import com.example.platform.dto.PointAlarmRuleVO;
import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.AlarmNotificationLog;
import com.example.platform.entity.Device;
import com.example.platform.entity.MetricSample;
import com.example.platform.entity.PointDefinition;
import com.example.platform.event.AlarmEventTriggeredEvent;
import com.example.platform.event.MetricSampleSavedEvent;
import com.example.platform.mapper.AlarmNotificationLogMapper;
import com.example.platform.service.AlarmEvaluateService;
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.AlarmTemplateService;
import com.example.platform.service.DeviceService;
import com.example.platform.service.MetricSampleService;
import com.example.platform.service.PointDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/*
 * 类功能:告警评估引擎服务实现类（T2 评估事务所有者）
 *
 * 【三区判定模型（S8-1）】对每条启用 METRIC 规则按当前值分三区：
 *   越限区（operator×threshold 命中）→ 触发逻辑；
 *   迟滞死区（不越限但未回落进安全区，仅 hysteresis>0 时存在）→ 不动作（不触发不恢复）；
 *   安全区 → 恢复逻辑（含静默窗口）。安全区方向随算符：GT/GE = value < threshold-hysteresis，
 *   LT/LE = value > threshold+hysteresis；EQ/NE 不适用迟滞。hysteresis=0 时死区为空、
 *   安全区=不越限，行为与存量一致。
 *
 * 【机制命中（同台竞争取最严重，级别数字最小）】
 *   直接阈值：breach_count ≤1 时越限即命中（存量行为）；
 *   M1 连续越限：breach_count ≥2 时从尾部数连续越限点数 ≥N 才命中，迟滞死区/安全区打断连续链；
 *   M2 变化量：rate_limit 非空时取「当前采样为终点、向前 rate_window_sec 窗口内最早一条」为基准，
 *     |当前-基准| > rate_limit 直接命中（不等阈值、与三区无关）；窗口内不足两条跳过。
 *
 * 【尾部推导，无持久状态】评估为 @Async AFTER_COMMIT，异步乱序/并发下任何
 *   read-modify-write 计数都会丢数——M1/M2 判定基准一律评估时查 metric_sample
 *   尾部（idx_metric_sample_device_point_time），重算幂等收敛、重启续算免实现。
 *   注意：metric_latest 在 T1 内评估前已 UPSERT 为当前值，禁止作为 M2 基准。
 *
 * 【恢复侧静默窗口】恢复条件=全部规则进安全区 且 最近 quiet-window-sec（全局配置）
 *   内无越限采样（防抖在恢复侧，不产生 flap 事件）。
 *
 * 【M3 再告警】trigger 返回 SKIPPED（活动告警去重跳过）且规则 re_alarm_sec>0 时，
 *   距该事件最近一次成功通知（alarm_notification_log 任一通道 status=1 最新 sent_time，
 *   无记录则取事件触发时间）超过间隔 → 发布 RESEND 事件重发通知（不升级不改级别）。
 *
 * 【异常原则（D15）】评估报错≠采样非法：全方法 try-catch，异常仅记 ERROR 日志，
 *   不生成设备告警、不回滚采样；引擎自身故障不进 alarm_event。
 *
 * 【注解三件套】@Async(复用 AsyncConfig，MDC traceId 继承) +
 *   @TransactionalEventListener(AFTER_COMMIT，仅 T1 提交后执行) +
 *   @Transactional(REQUIRES_NEW，T2 独立事务)。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmEvaluateServiceImpl implements AlarmEvaluateService {

    public static final String ALARM_TYPE_METRIC = "METRIC";

    // 胜出机制标记（决定告警文案口径）
    private static final String MECH_THRESHOLD = "THRESHOLD";
    private static final String MECH_STREAK = "STREAK";
    private static final String MECH_RATE = "RATE";

    // 三区代码：数值越大越异常；worstZone==SAFE 才允许恢复
    private static final int ZONE_SAFE = 0;
    private static final int ZONE_DEAD = 1;
    private static final int ZONE_BREACH = 2;

    // M1 尾部回看的下界：24h 内的采样才参与连续计数（覆盖 999 次×60s 心跳上限；
    // 同时为 S8-4 月分区提供裁剪下界，避免跨全部分区扫描）
    private static final java.time.Duration STREAK_LOOKBACK = java.time.Duration.ofHours(24);

    // 恢复侧静默窗口单次最多回看的采样条数上限（密集流下兜底，防止无界查询）
    private static final int QUIET_WINDOW_SAMPLE_LIMIT = 500;

    // 通知日志无成功记录时，M3 基准回退到事件触发时间
    private static final int LOG_STATUS_DELIVERED = 1;

    private final AlarmTemplateService alarmTemplateService;
    private final AlarmEventService alarmEventService;
    private final DeviceService deviceService;
    private final PointDefinitionService pointDefinitionService;
    private final MetricSampleService metricSampleService;
    private final AlarmNotificationLogMapper notificationLogMapper;
    private final ApplicationEventPublisher eventPublisher;

    // 恢复侧静默窗口（秒），全局配置：恢复要求窗口内无越限采样
    @Value("${alarm.quiet-window-sec:300}")
    private int quietWindowSec;

    @Override
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void evaluate(MetricSampleSavedEvent event) {
        try {
            doEvaluate(event);
        } catch (Exception ex) {
            // D15：评估异常不回滚采样、不生成自指告警，仅记 ERROR（traceId 由 MDC 贯穿）
            log.error("告警评估异常（采样数据不受影响） deviceId={} pointId={} value={}",
                    event.deviceId(), event.pointId(), event.sampleValue(), ex);
        }
    }

    private void doEvaluate(MetricSampleSavedEvent event) {
        List<PointAlarmRuleVO> rules = alarmTemplateService
                .findEffectiveRules(event.deviceId(), event.pointId()).stream()
                .filter(rule -> ALARM_TYPE_METRIC.equals(rule.getRuleType()))
                .toList();
        if (rules.isEmpty()) {
            return;
        }

        // 三区分类 + 机制命中竞争：M2 与三区无关（直接告警）；M1/直接阈值只在越限区判定
        HitCandidate hit = null;
        int worstZone = ZONE_SAFE;
        for (PointAlarmRuleVO rule : rules) {
            int zone = zoneOf(rule, event.sampleValue());
            worstZone = Math.max(worstZone, zone);
            if (isRateHit(rule, event)) {
                hit = worseOf(hit, new HitCandidate(rule, MECH_RATE));
            } else if (zone == ZONE_BREACH) {
                if (isImmediateBreach(rule)) {
                    hit = worseOf(hit, new HitCandidate(rule, MECH_THRESHOLD));
                } else if (isBreachStreakHit(rule, event)) {
                    hit = worseOf(hit, new HitCandidate(rule, MECH_STREAK));
                }
            }
        }

        if (hit != null) {
            triggerAlarm(event, hit);
        } else if (worstZone == ZONE_SAFE) {
            maybeAutoRecover(event, rules);
        }
        // worstZone == ZONE_DEAD：不动作（不触发不恢复）
    }

    // ===================== 三区判定 =====================

    // 当前值相对单条规则的三区归属
    private int zoneOf(PointAlarmRuleVO rule, BigDecimal value) {
        if (matches(rule, value)) {
            return ZONE_BREACH;
        }
        BigDecimal hysteresis = rule.getHysteresis();
        if (hysteresis == null || hysteresis.signum() <= 0 || rule.getThreshold() == null) {
            return ZONE_SAFE;
        }
        return switch (rule.getOperator()) {
            // GT/GE：回落到 threshold-hysteresis 以下才算安全；LT/LE：回升到 threshold+hysteresis 以上
            case "GT", "GE" -> value.compareTo(rule.getThreshold().subtract(hysteresis)) < 0
                    ? ZONE_SAFE : ZONE_DEAD;
            case "LT", "LE" -> value.compareTo(rule.getThreshold().add(hysteresis)) > 0
                    ? ZONE_SAFE : ZONE_DEAD;
            // EQ/NE 不适用迟滞
            default -> ZONE_SAFE;
        };
    }

    private boolean matches(PointAlarmRuleVO rule, BigDecimal value) {
        BigDecimal threshold = rule.getThreshold();
        if (threshold == null || rule.getOperator() == null) {
            return false;
        }
        int cmp = value.compareTo(threshold);
        return switch (rule.getOperator()) {
            case "GT" -> cmp > 0;
            case "LT" -> cmp < 0;
            case "GE" -> cmp >= 0;
            case "LE" -> cmp <= 0;
            case "EQ" -> cmp == 0;
            case "NE" -> cmp != 0;
            default -> false;
        };
    }

    // ===================== 机制命中 =====================

    // 直接阈值：M1 未启用时越限即命中（存量行为）
    private boolean isImmediateBreach(PointAlarmRuleVO rule) {
        return rule.getBreachCount() == null || rule.getBreachCount() <= 1;
    }

    // M1 连续越限：尾部推导——含当前采样在内数连续越限点数，死区/安全区打断连续链
    private boolean isBreachStreakHit(PointAlarmRuleVO rule, MetricSampleSavedEvent event) {
        int required = rule.getBreachCount();
        if (required < 2) {
            return false;
        }
        int streak = 1;
        if (required > 1) {
            List<MetricSample> older = metricSampleService.list(new LambdaQueryWrapper<MetricSample>()
                    .eq(MetricSample::getDeviceId, event.deviceId())
                    .eq(MetricSample::getPointId, event.pointId())
                    .lt(MetricSample::getSampleTime, event.sampleTime())
                    .ge(MetricSample::getSampleTime, event.sampleTime().minus(STREAK_LOOKBACK))
                    .orderByDesc(MetricSample::getSampleTime)
                    .orderByDesc(MetricSample::getId)
                    .last("LIMIT " + (required - 1)));
            for (MetricSample sample : older) {
                if (matches(rule, sample.getSampleValue())) {
                    streak++;
                } else {
                    break;
                }
            }
        }
        return streak >= required;
    }

    // M2 变化量：与三区无关的直接告警；基准=窗口内最早一条（禁止用 metric_latest，见类注释）
    private boolean isRateHit(PointAlarmRuleVO rule, MetricSampleSavedEvent event) {
        BigDecimal rateLimit = rule.getRateLimit();
        Integer windowSec = rule.getRateWindowSec();
        if (rateLimit == null || rateLimit.signum() < 0 || windowSec == null || windowSec < 1) {
            return false;
        }
        MetricSample baseline = metricSampleService.getOne(new LambdaQueryWrapper<MetricSample>()
                .eq(MetricSample::getDeviceId, event.deviceId())
                .eq(MetricSample::getPointId, event.pointId())
                .ge(MetricSample::getSampleTime, event.sampleTime().minusSeconds(windowSec))
                .lt(MetricSample::getSampleTime, event.sampleTime())
                .orderByAsc(MetricSample::getSampleTime)
                .orderByAsc(MetricSample::getId)
                .last("LIMIT 1"), false);
        if (baseline == null || baseline.getSampleValue() == null) {
            // 窗口内不足两条（含稀疏采样整窗只有当前一条）→ 跳过该机制，回退直接阈值
            return false;
        }
        return event.sampleValue().subtract(baseline.getSampleValue()).abs()
                .compareTo(rateLimit) > 0;
    }

    // 命中竞争：级别数字最小者胜出，同级别保留先到者
    private HitCandidate worseOf(HitCandidate current, HitCandidate candidate) {
        if (current == null || candidate.rule().getAlarmLevel() < current.rule().getAlarmLevel()) {
            return candidate;
        }
        return current;
    }

    // 命中候选：规则 + 胜出机制（决定告警文案口径）
    private record HitCandidate(PointAlarmRuleVO rule, String mechanism) {
    }

    // ===================== 触发 / 恢复 / 再告警 =====================

    private void triggerAlarm(MetricSampleSavedEvent event, HitCandidate hit) {
        PointAlarmRuleVO rule = hit.rule();
        Device device = deviceService.getById(event.deviceId());
        if (device == null) {
            log.warn("告警评估目标设备不存在 deviceId={}", event.deviceId());
            return;
        }
        PointDefinition point = pointDefinitionService.getById(event.pointId());
        String pointName = point != null ? point.getPointName() : String.valueOf(event.pointId());
        String unit = point != null && point.getUnit() != null ? point.getUnit() : "";

        AlarmTriggerContext context = AlarmTriggerContext.builder()
                .deviceId(event.deviceId())
                .pointId(event.pointId())
                .alarmType(ALARM_TYPE_METRIC)
                .alarmLevel(rule.getAlarmLevel())
                .alarmMessage(buildHitMessage(event, hit, pointName, unit))
                .cloudRoomId(device.getCloudRoomId())
                .build();
        AlarmTriggerResult result = alarmEventService.trigger(context);
        if (result.isNotifiable()) {
            log.info("告警评估触发 alarmEventId={} kind={} device={} point={} rule={}",
                    result.getAlarmEvent().getId(), result.getKind(),
                    event.deviceId(), event.pointId(), rule.getRuleName());
        } else if (rule.getReAlarmSec() != null && rule.getReAlarmSec() > 0) {
            // M3 再告警：活动告警未恢复且持续越限，距最近一次成功通知超过间隔 → 重发通知
            maybeReSend(result.getAlarmEvent(), rule);
        }
    }

    private String buildHitMessage(MetricSampleSavedEvent event, HitCandidate hit,
                                   String pointName, String unit) {
        PointAlarmRuleVO rule = hit.rule();
        String valueText = event.sampleValue().stripTrailingZeros().toPlainString();
        return switch (hit.mechanism()) {
            case MECH_RATE -> String.format("%s：%s=%s%s 疑似急变（%ds 窗口变化量超限值 %s，级别%d）",
                    rule.getRuleName(), pointName, valueText, unit,
                    rule.getRateWindowSec() != null ? rule.getRateWindowSec() : 60,
                    rule.getRateLimit().stripTrailingZeros().toPlainString(),
                    rule.getAlarmLevel());
            case MECH_STREAK -> String.format("%s：%s=%s%s 连续%d个采样点越限（规则 %s %s，级别%d）",
                    rule.getRuleName(), pointName, valueText, unit, rule.getBreachCount(),
                    rule.getOperator(),
                    rule.getThreshold() != null
                            ? rule.getThreshold().stripTrailingZeros().toPlainString() : "-",
                    rule.getAlarmLevel());
            default -> String.format("%s：%s=%s%s（规则 %s %s，级别%d）",
                    rule.getRuleName(), pointName, valueText, unit,
                    rule.getOperator(),
                    rule.getThreshold() != null
                            ? rule.getThreshold().stripTrailingZeros().toPlainString() : "-",
                    rule.getAlarmLevel());
        };
    }

    // 恢复侧静默窗口：全部规则进安全区后，还要求窗口内无越限采样才真正恢复
    private void maybeAutoRecover(MetricSampleSavedEvent event, List<PointAlarmRuleVO> rules) {
        if (quietWindowSec > 0 && hasBreachInQuietWindow(event, rules)) {
            log.debug("静默窗口内存在越限采样，暂缓恢复 deviceId={} pointId={}",
                    event.deviceId(), event.pointId());
            return;
        }
        alarmEventService.autoRecover(event.deviceId(), event.pointId(), ALARM_TYPE_METRIC);
    }

    // 静默窗口内是否存在任一启用规则的越限采样（尾部回看，LIMIT 兜底防无界查询）
    private boolean hasBreachInQuietWindow(MetricSampleSavedEvent event, List<PointAlarmRuleVO> rules) {
        List<MetricSample> window = metricSampleService.list(new LambdaQueryWrapper<MetricSample>()
                .eq(MetricSample::getDeviceId, event.deviceId())
                .eq(MetricSample::getPointId, event.pointId())
                .ge(MetricSample::getSampleTime, event.sampleTime().minusSeconds(quietWindowSec))
                .lt(MetricSample::getSampleTime, event.sampleTime())
                .orderByDesc(MetricSample::getSampleTime)
                .orderByDesc(MetricSample::getId)
                .last("LIMIT " + QUIET_WINDOW_SAMPLE_LIMIT));
        for (MetricSample sample : window) {
            for (PointAlarmRuleVO rule : rules) {
                if (matches(rule, sample.getSampleValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    // M3：成功通知口径=任一通道 status=1 最新 sent_time；无记录回退事件触发时间；重发成功后由新日志刷新基准
    private void maybeReSend(AlarmEvent event, PointAlarmRuleVO rule) {
        AlarmNotificationLog lastSuccess = notificationLogMapper.selectOne(
                new LambdaQueryWrapper<AlarmNotificationLog>()
                        .eq(AlarmNotificationLog::getAlarmEventId, event.getId())
                        .eq(AlarmNotificationLog::getStatus, LOG_STATUS_DELIVERED)
                        .orderByDesc(AlarmNotificationLog::getSentTime)
                        .last("LIMIT 1"), false);
        LocalDateTime baseline = lastSuccess != null && lastSuccess.getSentTime() != null
                ? lastSuccess.getSentTime() : event.getTriggeredTime();
        if (baseline == null) {
            return;
        }
        long elapsedSec = Duration.between(baseline, LocalDateTime.now()).getSeconds();
        if (elapsedSec < rule.getReAlarmSec()) {
            return;
        }
        eventPublisher.publishEvent(new AlarmEventTriggeredEvent(
                event.getId(), AlarmEventTriggeredEvent.KIND_RESEND));
        log.info("告警再提醒已发布 alarmEventId={} rule={} 持续越限={}s 间隔={}s",
                event.getId(), rule.getRuleName(), elapsedSec, rule.getReAlarmSec());
    }

    // ===================== 停报兜底（超窗确认恢复） =====================

    @Override
    public int recoverStaleConfirmedAlarms() {
        int recovered = 0;
        List<AlarmEvent> activeEvents = alarmEventService.listActiveByAlarmType(ALARM_TYPE_METRIC);
        for (AlarmEvent event : activeEvents) {
            if (event.getDeviceId() == null || event.getPointId() == null) {
                continue;
            }
            try {
                if (recoverStaleOne(event)) {
                    recovered++;
                }
            } catch (Exception ex) {
                log.warn("停报兜底恢复失败 alarmEventId={} error={}", event.getId(), ex.getMessage());
            }
        }
        return recovered;
    }

    // 恢复条件：最新采样已位于安全区、距今超过静默窗口。最新采样即无更新数据，
    // "期间无新越限采样"自动成立；越限/死区则交由 OFFLINE/网关离线链路表达，不由恢复链路兜
    private boolean recoverStaleOne(AlarmEvent event) {
        MetricSample latest = metricSampleService.getOne(new LambdaQueryWrapper<MetricSample>()
                .eq(MetricSample::getDeviceId, event.getDeviceId())
                .eq(MetricSample::getPointId, event.getPointId())
                .orderByDesc(MetricSample::getSampleTime)
                .orderByDesc(MetricSample::getId)
                .last("LIMIT 1"), false);
        if (latest == null || latest.getSampleTime() == null
                || latest.getSampleTime().isAfter(LocalDateTime.now().minusSeconds(quietWindowSec))) {
            return false;
        }
        List<PointAlarmRuleVO> rules = alarmTemplateService
                .findEffectiveRules(event.getDeviceId(), event.getPointId()).stream()
                .filter(rule -> ALARM_TYPE_METRIC.equals(rule.getRuleType()))
                .toList();
        if (rules.isEmpty()) {
            return false;
        }
        boolean allSafe = rules.stream()
                .allMatch(rule -> zoneOf(rule, latest.getSampleValue()) == ZONE_SAFE);
        if (!allSafe) {
            return false;
        }
        boolean recovered = alarmEventService.autoRecover(
                event.getDeviceId(), event.getPointId(), ALARM_TYPE_METRIC);
        if (recovered) {
            log.info("停报超窗确认恢复 alarmEventId={} 最新采样时间={}",
                    event.getId(), latest.getSampleTime());
        }
        return recovered;
    }
}
