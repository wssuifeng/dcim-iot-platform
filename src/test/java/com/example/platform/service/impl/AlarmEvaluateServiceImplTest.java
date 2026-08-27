package com.example.platform.service.impl;

import com.example.platform.dto.AlarmTriggerContext;
import com.example.platform.dto.AlarmTriggerResult;
import com.example.platform.dto.PointAlarmRuleVO;
import com.example.platform.entity.Device;
import com.example.platform.entity.PointDefinition;
import com.example.platform.event.MetricSampleSavedEvent;
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.DevicePointAlarmRuleService;
import com.example.platform.service.DeviceService;
import com.example.platform.service.PointDefinitionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:AlarmEvaluateServiceImpl 单元测试
 * 覆盖：无规则跳过、最严重命中、未命中自动恢复、trigger 上下文组装、
 *   评估异常不外抛不触发（D15：评估报错不回滚采样、不产自指告警）
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlarmEvaluateService 单元测试")
class AlarmEvaluateServiceImplTest {

    @Mock
    private DevicePointAlarmRuleService devicePointAlarmRuleService;
    @Mock
    private AlarmEventService alarmEventService;
    @Mock
    private DeviceService deviceService;
    @Mock
    private PointDefinitionService pointDefinitionService;

    @InjectMocks
    private AlarmEvaluateServiceImpl alarmEvaluateService;

    private static final Integer DEVICE_ID = 2;
    private static final Long POINT_ID = 1L;

    private MetricSampleSavedEvent event(String value) {
        return new MetricSampleSavedEvent(DEVICE_ID, POINT_ID,
                new BigDecimal(value), LocalDateTime.now());
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

    private Device device() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setCloudRoomId(10L);
        return device;
    }

    @Nested
    @DisplayName("evaluate - 评估")
    class Evaluate {

        @Test
        @DisplayName("无生效规则时不做任何触发")
        void should_skip_whenNoRules() {
            when(devicePointAlarmRuleService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of());

            alarmEvaluateService.evaluate(event("41"));

            verify(alarmEventService, never()).trigger(any());
            verify(alarmEventService, never()).autoRecover(any(), any(), any());
        }

        @Test
        @DisplayName("多条规则命中取最严重级别（数字最小）")
        void should_triggerMostSevereHit() {
            when(devicePointAlarmRuleService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(
                            rule("GT", "35", 2),
                            rule("GT", "40", 1)));
            when(deviceService.getById(DEVICE_ID)).thenReturn(device());
            when(pointDefinitionService.getById(POINT_ID)).thenReturn(new PointDefinition());
            when(alarmEventService.trigger(any()))
                    .thenReturn(AlarmTriggerResult.of(new com.example.platform.entity.AlarmEvent(),
                            AlarmTriggerResult.KIND_CREATED));

            alarmEvaluateService.evaluate(event("41"));

            ArgumentCaptor<AlarmTriggerContext> captor = ArgumentCaptor.forClass(AlarmTriggerContext.class);
            verify(alarmEventService).trigger(captor.capture());
            assertThat(captor.getValue().getAlarmLevel()).isEqualTo(1);
            assertThat(captor.getValue().getCloudRoomId()).isEqualTo(10L);
            assertThat(captor.getValue().getAlarmType()).isEqualTo("METRIC");
        }

        @Test
        @DisplayName("未命中且存在活动告警时自动恢复")
        void should_autoRecover_whenNoHit() {
            when(devicePointAlarmRuleService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(rule("GT", "35", 2)));
            when(alarmEventService.autoRecover(DEVICE_ID, POINT_ID, "METRIC")).thenReturn(true);

            alarmEvaluateService.evaluate(event("25"));

            verify(alarmEventService).autoRecover(DEVICE_ID, POINT_ID, "METRIC");
            verify(alarmEventService, never()).trigger(any());
        }

        @Test
        @DisplayName("比较符语义：LT 命中低温规则")
        void should_matchLtOperator() {
            when(devicePointAlarmRuleService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(rule("LT", "10", 4)));
            when(deviceService.getById(DEVICE_ID)).thenReturn(device());
            when(pointDefinitionService.getById(POINT_ID)).thenReturn(new PointDefinition());
            when(alarmEventService.trigger(any()))
                    .thenReturn(AlarmTriggerResult.of(new com.example.platform.entity.AlarmEvent(),
                            AlarmTriggerResult.KIND_CREATED));

            alarmEvaluateService.evaluate(event("5"));

            verify(alarmEventService).trigger(any());
        }

        @Test
        @DisplayName("评估异常不外抛不触发（D15：采样数据不受影响）")
        void should_swallowException_withoutTrigger() {
            when(devicePointAlarmRuleService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenThrow(new IllegalStateException("engine down"));

            assertThatCode(() -> alarmEvaluateService.evaluate(event("41")))
                    .doesNotThrowAnyException();

            verify(alarmEventService, never()).trigger(any());
            verify(alarmEventService, never()).autoRecover(any(), any(), any());
        }

        @Test
        @DisplayName("trigger 抛异常同样被吞掉（评估事务失败不影响采样）")
        void should_swallowTriggerException() {
            when(devicePointAlarmRuleService.findEffectiveRules(DEVICE_ID, POINT_ID))
                    .thenReturn(List.of(rule("GT", "35", 2)));
            when(deviceService.getById(DEVICE_ID)).thenReturn(device());
            when(pointDefinitionService.getById(POINT_ID)).thenReturn(new PointDefinition());
            when(alarmEventService.trigger(any()))
                    .thenThrow(new RuntimeException("T2 rollback"));

            assertThatCode(() -> alarmEvaluateService.evaluate(event("41")))
                    .doesNotThrowAnyException();
        }
    }
}
