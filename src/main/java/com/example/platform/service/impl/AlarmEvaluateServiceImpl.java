package com.example.platform.service.impl;

import com.example.platform.dto.AlarmTriggerContext;
import com.example.platform.dto.AlarmTriggerResult;
import com.example.platform.dto.PointAlarmRuleVO;
import com.example.platform.entity.Device;
import com.example.platform.entity.PointDefinition;
import com.example.platform.event.MetricSampleSavedEvent;
import com.example.platform.service.AlarmEvaluateService;
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.AlarmTemplateService;
import com.example.platform.service.DeviceService;
import com.example.platform.service.PointDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/*
 * 类功能:告警评估引擎服务实现类（T2 评估事务所有者）
 *
 * 【评估流程】
 *   1. findEffectiveRules(deviceId, pointId) 取 METRIC 类型生效规则（已按级别升序）
 *   2. 逐条 operator×threshold 比较，全部启用规则评估完取最严重命中（级别数字最小）
 *      （"从高到低/从低到高"校验顺序不影响结果：最严重命中者胜出）
 *   3. 命中 → AlarmEventService.trigger（活动告警去重/升级在状态机内完成）
 *   4. 未命中但存在同指标 METRIC 活动告警 → AlarmEventService.autoRecover（值恢复正常）
 *
 * 【异常原则（D15）】评估报错≠采样非法：全方法 try-catch，异常仅记 ERROR 日志
 *   （带 traceId），不生成设备告警、不回滚采样；引擎自身故障不进 alarm_event。
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

    private final AlarmTemplateService alarmTemplateService;
    private final AlarmEventService alarmEventService;
    private final DeviceService deviceService;
    private final PointDefinitionService pointDefinitionService;

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

        // 全部启用规则评估完，取最严重命中（级别数字最小）；同级别取首条
        Optional<PointAlarmRuleVO> hit = rules.stream()
                .filter(rule -> matches(rule, event.sampleValue()))
                .min(Comparator.comparingInt(PointAlarmRuleVO::getAlarmLevel));

        if (hit.isPresent()) {
            triggerAlarm(event, hit.get());
        } else {
            // 值恢复正常：同指标 METRIC 活动告警自动恢复（设备在线由上报刷新，离线告警由任务恢复）
            alarmEventService.autoRecover(event.deviceId(), event.pointId(), ALARM_TYPE_METRIC);
        }
    }

    private void triggerAlarm(MetricSampleSavedEvent event, PointAlarmRuleVO rule) {
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
                .alarmMessage(String.format("%s：%s=%s%s（规则 %s %s，级别%d）",
                        rule.getRuleName(), pointName,
                        event.sampleValue().stripTrailingZeros().toPlainString(), unit,
                        rule.getOperator(),
                        rule.getThreshold() != null
                                ? rule.getThreshold().stripTrailingZeros().toPlainString() : "-",
                        rule.getAlarmLevel()))
                .cloudRoomId(device.getCloudRoomId())
                .build();
        AlarmTriggerResult result = alarmEventService.trigger(context);
        if (result.isNotifiable()) {
            log.info("告警评估触发 alarmEventId={} kind={} device={} point={} rule={}",
                    result.getAlarmEvent().getId(), result.getKind(),
                    event.deviceId(), event.pointId(), rule.getRuleName());
        }
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
}
