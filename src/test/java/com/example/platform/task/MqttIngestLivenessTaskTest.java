package com.example.platform.task;

import com.example.platform.dto.AlarmTriggerContext;
import com.example.platform.ingest.mqtt.IngestMqttListener;
import com.example.platform.service.AlarmEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:MQTT 假死巡检任务判据回归——"已连接 + 超过静默阈值零消息"才产出自指告警，
 *   未连接（重连机制负责）与消息新鲜（水位新）均不告警
 * */
@ExtendWith(MockitoExtension.class)
class MqttIngestLivenessTaskTest {

    @Mock
    private IngestMqttListener ingestMqttListener;
    @Mock
    private AlarmEventService alarmEventService;

    private MqttIngestLivenessTask task;

    @BeforeEach
    void setUp() {
        task = new MqttIngestLivenessTask(ingestMqttListener, alarmEventService);
        ReflectionTestUtils.setField(task, "silentThresholdSeconds", 600L);
    }

    @Test
    void connectedAndSilentBeyondThresholdTriggersStallAlarm() {
        when(ingestMqttListener.isMqttConnected()).thenReturn(true);
        when(ingestMqttListener.millisSinceLastIngestMessage()).thenReturn(601_000L);

        task.check();

        ArgumentCaptor<AlarmTriggerContext> captor = ArgumentCaptor.forClass(AlarmTriggerContext.class);
        verify(alarmEventService).trigger(captor.capture());
        assertThat(captor.getValue().getAlarmType()).isEqualTo(MqttIngestLivenessTask.ALARM_TYPE_MQTT_STALL);
    }

    @Test
    void connectedWithFreshTrafficDoesNotTrigger() {
        when(ingestMqttListener.isMqttConnected()).thenReturn(true);
        when(ingestMqttListener.millisSinceLastIngestMessage()).thenReturn(300_000L);

        task.check();

        verify(alarmEventService, never()).trigger(anyContext());
    }

    @Test
    void disconnectedDoesNotTrigger() {
        // 断连不属于假死（自动重连负责），不产自指告警
        when(ingestMqttListener.isMqttConnected()).thenReturn(false);

        task.check();

        verify(alarmEventService, never()).trigger(anyContext());
    }

    private AlarmTriggerContext anyContext() {
        return org.mockito.ArgumentMatchers.any(AlarmTriggerContext.class);
    }
}
