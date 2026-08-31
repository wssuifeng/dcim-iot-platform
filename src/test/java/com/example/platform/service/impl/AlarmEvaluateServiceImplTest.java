package com.example.platform.service.impl;

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
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.AlarmTemplateService;
import com.example.platform.service.DeviceService;
import com.example.platform.service.MetricSampleService;
import com.example.platform.service.PointDefinitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:AlarmEvaluateServiceImpl 单元测试（S8-1 三区模型 + 三机制 + 静默窗口）
 * 覆盖：无规则跳过、最严重命中、自动恢复、LT 语义、评估异常吞掉（D15）、
 *   M1 连续越限（命中/不足/中断/死区打断）、M2 变化量（命中/样本不足回退）、
 *   M3 再告警（超时重发/间隔内抑制/未启用）、恢复侧静默窗口（阻止/放行）、
 *   迟滞三区（死区不动作/方向随算符）、停报兜底超窗确认恢复
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlarmEvaluateService 单元测试")
class AlarmEvaluateServiceImplTest {

    @Mock
    private AlarmTemplateService alarmTemplateService;
    @Mock
    private AlarmEventService alarmEventService;
    @Mock
    private DeviceService deviceService;
    @Mock
    private PointDefinitionService pointDefinitionService;
    @Mock
    private MetricSampleService metricSampleService;
    @Mock
    private AlarmNotificationLogMapper notificationLogMapper;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AlarmEvaluateServiceImpl alarmEvaluateService;

    private static final Integer DEVICE_ID = 2;
    private static final Long POINT_ID = 1L;

    @BeforeEach
    void setUp() {
        // 测试默认关闭静默窗口（等价存量行为），需要窗口语义的用例单独设置
        ReflectionTestUtils.setField(alarmEvaluateService, "quietWindowSec", 0);
    }

    private MetricSampleSavedEvent event(String value) {
        return new MetricSampleSavedEvent(DEVICE_ID, POINT_ID,
                new BigDecimal(value), LocalDateTime.now());
    }

    private MetricSampleSavedEvent eventAt(String value, LocalDateTime sampleTime) {
        return new MetricSampleSavedEvent(DEVICE_ID, POINT_ID,
                new BigDecimal(value), sampleTime);
    }

    private PointAlarmRuleVO rule(String operator, String threshold, int level) {
        PointAlarmRuleVO vo = new PointAlarmRuleVO();
        vo.setRuleType("METRIC");
        vo.setRuleName("高温警告");
        vo.setOperator(operator);
        vo.setThreshold(new BigDecimal(threshold));
        vo.setAlarmLevel(level);
        return vo;
    }

    private MetricSample sample(String value, LocalDateTime time) {
        MetricSample sample = new MetricSample();
        sample.setDeviceId(DEVICE_ID);
        sample.setPointId(POINT_ID);
        sample.setSampleValue(new BigDecimal(value));
        sample.setSampleTime(time);
        return sample;
    }

    private Device device() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setCloudRoomId(10L);
        return device;
    }

    // 类型化匹配器：IRepository.list(Wrapper)/list(IPage) 重载在裸 any() 下二义
    private com.baomidou.mybatisplus.core.conditions.Wrapper<MetricSample> anyWrapper() {
        return org.mockito.ArgumentMatchers.any();
    }

    private AlarmEvent activeEvent(long id, LocalDateTime triggeredAt) {
        AlarmEvent event = new AlarmEvent();
        event.setId(id);
        event.setDeviceId(DEVICE_ID);
        event.setPointId(POINT_ID);
        event.setAlarmType("METRIC");
        event.setState("TRIGGERED");
        event.setTriggeredTime(triggeredAt);
        return event;
    }

    private void stubTriggerCreated() {
        when(deviceService.getById(DEVICE_ID)).thenReturn(device());
        when(pointDefinitionService.getById(POINT_ID)).thenReturn(new PointDefinition());
        when(alarmEventService.trigger(any()))
                .thenReturn(AlarmTriggerResult.of(new AlarmEvent(),
                        AlarmTriggerResult.KIND_CREATED));
    }

    @Nested
    @DisplayName("evaluate - 存量行为（默认全关）")
    class Baseline {

        @Test
        @DisplayName("无生效规则时不做任何触发")
        void should_skip_whenNoRules() {
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of());

            alarmEvaluateService.evaluate(event("41"));

            verify(alarmEventService, never()).trigger(any());
            verify(alarmEventService, never()).autoRecover(any(), any(), any());
        }

        @Test
        @DisplayName("多条规则命中取最严重级别（数字最小）")
        void should_triggerMostSevereHit() {
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(
                            rule("GT", "35", 2),
                            rule("GT", "40", 1)));
            stubTriggerCreated();

            alarmEvaluateService.evaluate(event("41"));

            ArgumentCaptor<AlarmTriggerContext> captor = ArgumentCaptor.forClass(AlarmTriggerContext.class);
            verify(alarmEventService).trigger(captor.capture());
            assertThat(captor.getValue().getAlarmLevel()).isEqualTo(1);
            assertThat(captor.getValue().getCloudRoomId()).isEqualTo(10L);
            assertThat(captor.getValue().getAlarmType()).isEqualTo("METRIC");
        }

        @Test
        @DisplayName("未命中且存在活动告警时自动恢复（静默窗口关闭）")
        void should_autoRecover_whenNoHit() {
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(rule("GT", "35", 2)));
            when(alarmEventService.autoRecover(DEVICE_ID, POINT_ID, "METRIC")).thenReturn(true);

            alarmEvaluateService.evaluate(event("25"));

            verify(alarmEventService).autoRecover(DEVICE_ID, POINT_ID, "METRIC");
            verify(alarmEventService, never()).trigger(any());
        }

        @Test
        @DisplayName("比较符语义：LT 命中低温规则")
        void should_matchLtOperator() {
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(rule("LT", "10", 4)));
            stubTriggerCreated();

            alarmEvaluateService.evaluate(event("5"));

            verify(alarmEventService).trigger(any());
        }

        @Test
        @DisplayName("评估异常不外抛不触发（D15：采样数据不受影响）")
        void should_swallowException_withoutTrigger() {
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenThrow(new IllegalStateException("engine down"));

            assertThatCode(() -> alarmEvaluateService.evaluate(event("41")))
                    .doesNotThrowAnyException();

            verify(alarmEventService, never()).trigger(any());
            verify(alarmEventService, never()).autoRecover(any(), any(), any());
        }

        @Test
        @DisplayName("trigger 抛异常同样被吞掉（评估事务失败不影响采样）")
        void should_swallowTriggerException() {
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(rule("GT", "35", 2)));
            when(deviceService.getById(DEVICE_ID)).thenReturn(device());
            when(pointDefinitionService.getById(POINT_ID)).thenReturn(new PointDefinition());
            when(alarmEventService.trigger(any()))
                    .thenThrow(new RuntimeException("T2 rollback"));

            assertThatCode(() -> alarmEvaluateService.evaluate(event("41")))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("M1 连续越限（尾部推导）")
    class BreachStreak {

        @Test
        @DisplayName("连续越限点数达标即触发（含当前点）")
        void should_trigger_whenStreakReachesRequired() {
            PointAlarmRuleVO m1 = rule("GT", "35", 2);
            m1.setBreachCount(3);
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(m1));
            LocalDateTime now = LocalDateTime.now();
            when(metricSampleService.list(anyWrapper())).thenReturn(List.of(
                    sample("36", now.minusSeconds(60)),
                    sample("36", now.minusSeconds(120))));
            stubTriggerCreated();

            alarmEvaluateService.evaluate(eventAt("36", now));

            verify(alarmEventService).trigger(any());
        }

        @Test
        @DisplayName("连续点数不足：不触发也不误恢复（值仍越限）")
        void should_notTriggerAndNotRecover_whenStreakInsufficient() {
            PointAlarmRuleVO m1 = rule("GT", "35", 2);
            m1.setBreachCount(3);
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(m1));
            LocalDateTime now = LocalDateTime.now();
            // 尾部往回：安全点打断连续链
            when(metricSampleService.list(anyWrapper())).thenReturn(List.of(
                    sample("30", now.minusSeconds(60)),
                    sample("36", now.minusSeconds(120))));

            alarmEvaluateService.evaluate(eventAt("36", now));

            verify(alarmEventService, never()).trigger(any());
            verify(alarmEventService, never()).autoRecover(any(), any(), any());
        }

        @Test
        @DisplayName("中途回落打断连续链后重新计数")
        void should_restartCounting_whenOlderSafeSampleBreaksStreak() {
            PointAlarmRuleVO m1 = rule("GT", "35", 2);
            m1.setBreachCount(3);
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(m1));
            LocalDateTime now = LocalDateTime.now();
            when(metricSampleService.list(anyWrapper())).thenReturn(List.of(
                    sample("36", now.minusSeconds(60)),
                    sample("20", now.minusSeconds(120)),
                    sample("36", now.minusSeconds(180))));

            alarmEvaluateService.evaluate(eventAt("36", now));

            verify(alarmEventService, never()).trigger(any());
        }

        @Test
        @DisplayName("迟滞死区打断连续链（死区点不算越限）")
        void should_breakStreak_onDeadZoneSample() {
            PointAlarmRuleVO m1 = rule("GT", "30", 2);
            m1.setBreachCount(3);
            m1.setHysteresis(new BigDecimal("5"));
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(m1));
            LocalDateTime now = LocalDateTime.now();
            // 28 位于死区（≤30 且 >25）→ 打断连续链
            when(metricSampleService.list(anyWrapper())).thenReturn(List.of(
                    sample("28", now.minusSeconds(60)),
                    sample("36", now.minusSeconds(120))));

            alarmEvaluateService.evaluate(eventAt("36", now));

            verify(alarmEventService, never()).trigger(any());
        }
    }

    @Nested
    @DisplayName("M2 变化量（窗口推导）")
    class RateLimit {

        @Test
        @DisplayName("窗口内变化量超限：不等阈值直接触发")
        void should_trigger_whenRateExceeded() {
            PointAlarmRuleVO m2 = rule("GT", "35", 2);
            m2.setRateLimit(new BigDecimal("5"));
            m2.setRateWindowSec(60);
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(m2));
            LocalDateTime now = LocalDateTime.now();
            when(metricSampleService.getOne(any(), anyBoolean()))
                    .thenReturn(sample("20", now.minusSeconds(55)));
            stubTriggerCreated();

            alarmEvaluateService.evaluate(eventAt("34", now));

            ArgumentCaptor<AlarmTriggerContext> captor = ArgumentCaptor.forClass(AlarmTriggerContext.class);
            verify(alarmEventService).trigger(captor.capture());
            assertThat(captor.getValue().getAlarmMessage()).contains("疑似急变");
        }

        @Test
        @DisplayName("窗口内不足两条采样：跳过 M2 回退直接阈值")
        void should_fallBackToThreshold_whenWindowSparse() {
            PointAlarmRuleVO m2 = rule("GT", "35", 2);
            m2.setRateLimit(new BigDecimal("5"));
            m2.setRateWindowSec(60);
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(m2));
            LocalDateTime now = LocalDateTime.now();
            when(metricSampleService.getOne(any(), anyBoolean())).thenReturn(null);
            when(alarmEventService.autoRecover(DEVICE_ID, POINT_ID, "METRIC")).thenReturn(true);

            // 34 未越限（GT 35），M2 跳过 → 全规则安全区 → 恢复
            alarmEvaluateService.evaluate(eventAt("34", now));

            verify(alarmEventService, never()).trigger(any());
            verify(alarmEventService).autoRecover(DEVICE_ID, POINT_ID, "METRIC");
        }
    }

    @Nested
    @DisplayName("M3 再告警")
    class ReAlarm {

        @Test
        @DisplayName("持续越限超过再告警间隔：发布 RESEND 重发通知（携带命中规则行 ID）")
        void should_publishResend_whenElapsedBeyondInterval() {
            PointAlarmRuleVO m3 = rule("GT", "35", 2);
            m3.setId(11L);
            m3.setReAlarmSec(1800);
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(m3));
            stubTriggerCreated();
            AlarmEvent active = activeEvent(9L, LocalDateTime.now().minusHours(1));
            when(alarmEventService.trigger(any()))
                    .thenReturn(AlarmTriggerResult.of(active, AlarmTriggerResult.KIND_SKIPPED));
            AlarmNotificationLog log = new AlarmNotificationLog();
            log.setSentTime(LocalDateTime.now().minusHours(1));
            when(notificationLogMapper.selectOne(any(), anyBoolean())).thenReturn(log);

            alarmEvaluateService.evaluate(event("41"));

            ArgumentCaptor<AlarmEventTriggeredEvent> captor =
                    ArgumentCaptor.forClass(AlarmEventTriggeredEvent.class);
            verify(alarmEventService, never()).autoRecover(any(), any(), any());
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().kind()).isEqualTo(AlarmEventTriggeredEvent.KIND_RESEND);
            assertThat(captor.getValue().alarmEventId()).isEqualTo(9L);
            assertThat(captor.getValue().templateItemId()).isEqualTo(11L);
        }

        @Test
        @DisplayName("间隔内不重复提醒")
        void should_suppressResend_withinInterval() {
            PointAlarmRuleVO m3 = rule("GT", "35", 2);
            m3.setReAlarmSec(1800);
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(m3));
            stubTriggerCreated();
            AlarmEvent active = activeEvent(9L, LocalDateTime.now().minusMinutes(5));
            when(alarmEventService.trigger(any()))
                    .thenReturn(AlarmTriggerResult.of(active, AlarmTriggerResult.KIND_SKIPPED));
            AlarmNotificationLog log = new AlarmNotificationLog();
            log.setSentTime(LocalDateTime.now().minusSeconds(60));
            when(notificationLogMapper.selectOne(any(), anyBoolean())).thenReturn(log);

            alarmEvaluateService.evaluate(event("41"));

            verify(eventPublisher, never()).publishEvent(any(AlarmEventTriggeredEvent.class));
        }

        @Test
        @DisplayName("M3 未启用（re_alarm_sec=0）不重发")
        void should_notResend_whenDisabled() {
            PointAlarmRuleVO m3 = rule("GT", "35", 2);
            m3.setReAlarmSec(0);
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(m3));
            stubTriggerCreated();
            AlarmEvent active = activeEvent(9L, LocalDateTime.now().minusHours(2));
            when(alarmEventService.trigger(any()))
                    .thenReturn(AlarmTriggerResult.of(active, AlarmTriggerResult.KIND_SKIPPED));

            alarmEvaluateService.evaluate(event("41"));

            verify(eventPublisher, never()).publishEvent(any(AlarmEventTriggeredEvent.class));
            verify(notificationLogMapper, never()).selectOne(any(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("三区模型与恢复侧静默窗口")
    class ZonesAndQuietWindow {

        @Test
        @DisplayName("迟滞死区：不触发也不恢复")
        void should_holdInDeadZone() {
            PointAlarmRuleVO hysteresis = rule("GT", "30", 3);
            hysteresis.setHysteresis(new BigDecimal("5"));
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(hysteresis));

            // 28：不越限（GT 30）但也未回落进安全区（<25）→ 死区 → 不动作
            alarmEvaluateService.evaluate(event("28"));

            verify(alarmEventService, never()).trigger(any());
            verify(alarmEventService, never()).autoRecover(any(), any(), any());
        }

        @Test
        @DisplayName("迟滞方向随算符：LT 回升进安全区后恢复")
        void should_recover_whenLtEntersSafeZone() {
            PointAlarmRuleVO hysteresis = rule("LT", "10", 3);
            hysteresis.setHysteresis(new BigDecimal("3"));
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(hysteresis));
            when(alarmEventService.autoRecover(DEVICE_ID, POINT_ID, "METRIC")).thenReturn(true);

            // 安全区：> 10+3=13
            alarmEvaluateService.evaluate(event("14"));

            verify(alarmEventService).autoRecover(DEVICE_ID, POINT_ID, "METRIC");
        }

        @Test
        @DisplayName("静默窗口内存在越限采样：暂缓恢复")
        void should_holdRecover_whenBreachInQuietWindow() {
            ReflectionTestUtils.setField(alarmEvaluateService, "quietWindowSec", 300);
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(rule("GT", "35", 2)));
            LocalDateTime now = LocalDateTime.now();
            when(metricSampleService.list(anyWrapper())).thenReturn(List.of(
                    sample("36", now.minusSeconds(120))));

            alarmEvaluateService.evaluate(eventAt("25", now));

            verify(alarmEventService, never()).autoRecover(any(), any(), any());
            verify(alarmEventService, never()).trigger(any());
        }

        @Test
        @DisplayName("静默窗口内全部安全：放行恢复")
        void should_recover_whenQuietWindowClean() {
            ReflectionTestUtils.setField(alarmEvaluateService, "quietWindowSec", 300);
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(rule("GT", "35", 2)));
            when(metricSampleService.list(anyWrapper())).thenReturn(List.of(
                    sample("30", LocalDateTime.now().minusSeconds(120))));
            when(alarmEventService.autoRecover(DEVICE_ID, POINT_ID, "METRIC")).thenReturn(true);

            alarmEvaluateService.evaluate(event("25"));

            verify(alarmEventService).autoRecover(DEVICE_ID, POINT_ID, "METRIC");
        }
    }

    @Nested
    @DisplayName("停报兜底（超窗确认恢复）")
    class StaleRecovery {

        @Test
        @DisplayName("最新采样已进安全区且超窗：确认恢复")
        void should_recoverStale_whenLatestSafeAndAged() {
            ReflectionTestUtils.setField(alarmEvaluateService, "quietWindowSec", 300);
            AlarmEvent active = activeEvent(7L, LocalDateTime.now().minusHours(2));
            when(alarmEventService.listActiveByAlarmType("METRIC")).thenReturn(List.of(active));
            when(metricSampleService.getOne(any(), anyBoolean())).thenReturn(
                    sample("25", LocalDateTime.now().minusSeconds(400)));
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(rule("GT", "35", 2)));
            when(alarmEventService.autoRecover(DEVICE_ID, POINT_ID, "METRIC")).thenReturn(true);

            int recovered = alarmEvaluateService.recoverStaleConfirmedAlarms();

            assertThat(recovered).isEqualTo(1);
        }

        @Test
        @DisplayName("最新采样仍越限：不恢复（交由 OFFLINE 链路表达）")
        void should_holdStale_whenLatestStillBreaching() {
            ReflectionTestUtils.setField(alarmEvaluateService, "quietWindowSec", 300);
            AlarmEvent active = activeEvent(7L, LocalDateTime.now().minusHours(2));
            when(alarmEventService.listActiveByAlarmType("METRIC")).thenReturn(List.of(active));
            when(metricSampleService.getOne(any(), anyBoolean())).thenReturn(
                    sample("36", LocalDateTime.now().minusSeconds(400)));
            when(alarmTemplateService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(rule("GT", "35", 2)));

            int recovered = alarmEvaluateService.recoverStaleConfirmedAlarms();

            assertThat(recovered).isZero();
            verify(alarmEventService, never()).autoRecover(any(), any(), any());
        }

        @Test
        @DisplayName("最新采样未超静默窗口：不恢复")
        void should_holdStale_whenLatestSampleStillFresh() {
            ReflectionTestUtils.setField(alarmEvaluateService, "quietWindowSec", 300);
            AlarmEvent active = activeEvent(7L, LocalDateTime.now().minusHours(2));
            when(alarmEventService.listActiveByAlarmType("METRIC")).thenReturn(List.of(active));
            when(metricSampleService.getOne(any(), anyBoolean())).thenReturn(
                    sample("25", LocalDateTime.now().minusSeconds(60)));

            int recovered = alarmEvaluateService.recoverStaleConfirmedAlarms();

            assertThat(recovered).isZero();
            verify(alarmTemplateService, never()).findEffectiveRules(any(), any());
        }
    }
}
