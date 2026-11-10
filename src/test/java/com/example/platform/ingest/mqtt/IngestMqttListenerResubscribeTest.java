package com.example.platform.ingest.mqtt;

import jakarta.validation.Validation;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/*
 * 类功能:W-001 修复回归——connectComplete 补订语义：首次连接与自动重连成功后
 *   都必须对全部四个北向 topic 执行订阅（cleanSession=true 下 Paho 不会自动重订阅）
 * */
@ExtendWith(MockitoExtension.class)
class IngestMqttListenerResubscribeTest {

    @Mock
    private com.example.platform.ingest.service.IngestCoreService ingestCoreService;
    @Mock
    private com.example.platform.ingest.service.IngestCredentialService ingestCredentialService;
    @Mock
    private com.example.platform.service.SysAuditLogService sysAuditLogService;
    @Mock
    private com.example.platform.service.AlarmEventService alarmEventService;
    @Mock
    private com.example.platform.service.CloudRoomService cloudRoomService;
    @Mock
    private com.example.platform.ingest.service.IngestPayloadMappingService payloadMappingService;

    private IngestMqttListener listener;

    @BeforeEach
    void setUp() {
        listener = new IngestMqttListener(ingestCoreService,
                new com.example.platform.ingest.metrics.IngestMetricsCollector(), ingestCredentialService,
                sysAuditLogService, alarmEventService, cloudRoomService, payloadMappingService,
                Validation.buildDefaultValidatorFactory().getValidator(),
                new ObjectMapper(), "tcp://localhost:1883", "test-ingest", "", "", 30, false);
    }

    @Test
    void connectCompleteResubscribesAllFourTopicsWithContractQos() throws Exception {
        IMqttClient target = mock(IMqttClient.class);

        listener.handleConnectComplete(target, true, "tcp://localhost:1883");

        verify(target).subscribe(eq("dcim/+/samples"), eq(1), any(IMqttMessageListener.class));
        verify(target).subscribe(eq("dcim/+/heartbeat"), eq(0), any(IMqttMessageListener.class));
        verify(target).subscribe(eq("dcim/+/lwt"), eq(0), any(IMqttMessageListener.class));
        verify(target).subscribe(eq("dcim-open/+/samples"), eq(1), any(IMqttMessageListener.class));
    }

    @Test
    void connectCompleteOnFirstConnectAlsoSubscribes() throws Exception {
        IMqttClient target = mock(IMqttClient.class);

        listener.handleConnectComplete(target, false, "tcp://localhost:1883");

        verify(target, times(4)).subscribe(any(String.class), any(Integer.class),
                any(IMqttMessageListener.class));
    }

    @Test
    void livenessWatermarkStartsNearConstructionTime() {
        // 启动后尚无消息：水位 = 进程内经历时长，应为小的非负值
        long sinceLastMessage = listener.millisSinceLastIngestMessage();

        assertThat(sinceLastMessage).isBetween(0L, 5_000L);
    }
}
