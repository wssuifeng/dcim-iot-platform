package com.example.platform.ingest.mqtt;

import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.ResultCode;
import com.example.platform.ingest.dto.IngestResultVO;
import com.example.platform.ingest.dto.IngestSampleBatchDTO;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.service.IngestCoreService;
import com.example.platform.ingest.service.IngestCredentialService;
import com.example.platform.ingest.util.GatewaySignUtils;
import com.example.platform.service.SysAuditLogService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestMqttListenerTest {

    @Mock
    private IngestCoreService ingestCoreService;
    @Mock
    private IngestCredentialService ingestCredentialService;
    @Mock
    private SysAuditLogService sysAuditLogService;
    @Mock
    private com.example.platform.service.AlarmEventService alarmEventService;
    @Mock
    private com.example.platform.service.CloudRoomService cloudRoomService;
    @Mock
    private com.example.platform.ingest.service.IngestPayloadMappingService payloadMappingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private IngestMqttListener listener;

    @BeforeEach
    void setUp() {
        listener = new IngestMqttListener(ingestCoreService,
                new com.example.platform.ingest.metrics.IngestMetricsCollector(), ingestCredentialService,
                sysAuditLogService, alarmEventService, cloudRoomService, payloadMappingService,
                Validation.buildDefaultValidatorFactory().getValidator(),
                objectMapper, "tcp://localhost:1883", "test-ingest", "", "", 30, false);
    }

    @Test
    void validEnvelopeDelegatesToCoreService() {
        IngestCredential credential = credential();
        when(ingestCredentialService.authenticate("gw-a")).thenReturn(credential);
        when(ingestCoreService.processSamples(eq(credential), any(IngestSampleBatchDTO.class)))
                .thenReturn(new IngestResultVO());

        listener.handleSamples("dcim/gw-a/samples", envelopeBytes("gw-a-secret", null));

        verify(ingestCoreService).processSamples(eq(credential), any(IngestSampleBatchDTO.class));
        verify(sysAuditLogService, never()).logStructured(any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void invalidSignatureRejectedAndAudited() {
        when(ingestCredentialService.authenticate("gw-a")).thenReturn(credential());

        listener.handleSamples("dcim/gw-a/samples",
                envelopeBytes("wrong-secret", null));

        verify(ingestCoreService, never()).processSamples(any(), any());
        verify(sysAuditLogService).logStructured(any(), eq("gw-a"), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), contains("验签失败"));
    }

    @Test
    void missingFieldsRejectedAndAudited() {
        when(ingestCredentialService.authenticate("gw-a")).thenReturn(credential());
        String payload = "{\"signature\":\"" + "0".repeat(64) + "\"}";

        listener.handleSamples("dcim/gw-a/samples", payload.getBytes(StandardCharsets.UTF_8));

        verify(ingestCoreService, never()).processSamples(any(), any());
        verify(sysAuditLogService).logStructured(any(), eq("gw-a"), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), contains("batch"));
    }

    @Test
    void invalidCredentialRejectedAndAudited() {
        when(ingestCredentialService.authenticate("gw-a"))
                .thenThrow(new BusinessException(ResultCode.INGEST_UNAUTHORIZED, "网关凭证无效"));

        listener.handleSamples("dcim/gw-a/samples", envelopeBytes("any", null));

        verify(ingestCoreService, never()).processSamples(any(), any());
        verify(sysAuditLogService).logStructured(any(), eq("gw-a"), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), contains("鉴权失败"));
    }

    @Test
    void emptyItemsFailsValidationAndAudited() {
        when(ingestCredentialService.authenticate("gw-a")).thenReturn(credential());

        listener.handleSamples("dcim/gw-a/samples",
                envelopeBytes("gw-a-secret", "[]"));

        verify(ingestCoreService, never()).processSamples(any(), any());
        verify(sysAuditLogService).logStructured(any(), eq("gw-a"), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), contains("校验失败"));
    }

    @Test
    void heartbeatDelegatesToCore() {
        when(ingestCredentialService.authenticate("gw-a")).thenReturn(credential());

        listener.handleHeartbeat("dcim/gw-a/heartbeat", "{\"ts\":\"x\"}".getBytes(StandardCharsets.UTF_8));

        verify(ingestCoreService).heartbeat(any(IngestCredential.class));
    }

    @Test
    void lwtRecordedAsStructuredAudit() {
        // 凭证不存在：仅审计，不产出告警
        when(ingestCredentialService.authenticate("gw-a"))
                .thenThrow(new BusinessException(ResultCode.INGEST_UNAUTHORIZED, "网关凭证无效"));
        listener.handleLwt("dcim/gw-a/lwt", "offline".getBytes(StandardCharsets.UTF_8));

        verify(sysAuditLogService).logStructured(any(), eq("gw-a"), eq("GATEWAY"), any(),
                eq("INGEST_GATEWAY_LWT"), any(), eq("gw-a"), eq("FAILURE"), any(), any(), any(), any());
        verify(alarmEventService, never()).trigger(any());
    }

    @Test
    void lwtWithValidCredentialTriggersGatewayOfflineAlarm() {
        when(ingestCredentialService.authenticate("gw-a")).thenReturn(credential());

        listener.handleLwt("dcim/gw-a/lwt", "offline".getBytes(StandardCharsets.UTF_8));

        verify(alarmEventService).trigger(any(com.example.platform.dto.AlarmTriggerContext.class));
    }

    @Test
    void topicParsesAccessKeyCorrectly() throws Exception {
        // 通过 handleHeartbeat 验证 topic 解析：uk accessKey 不会匹配任何凭证
        when(ingestCredentialService.authenticate("uk"))
                .thenThrow(new BusinessException(ResultCode.INGEST_UNAUTHORIZED, "网关凭证无效"));

        listener.handleHeartbeat("dcim/uk/heartbeat", new byte[0]);

        verify(ingestCredentialService).authenticate("uk");
    }

    private IngestCredential credential() {
        IngestCredential credential = new IngestCredential();
        credential.setId(1L);
        credential.setAccessKey("gw-a");
        credential.setSecret("gw-a-secret");
        credential.setCloudRoomId(5L);
        return credential;
    }

    private IngestCredential mqttCredential(String username) {
        IngestCredential credential = credential();
        credential.setMqttUsername(username);
        credential.setMqttPassword("p".repeat(40));
        return credential;
    }

    private byte[] openBatch(String deviceCode) {
        String payload = "{\"items\":[{\"deviceCode\":\"" + deviceCode
                + "\",\"pointCode\":\"temperature\",\"value\":22.5,"
                + "\"sampleTime\":\"2026-08-31T10:00:00\"}]}";
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void openSamplesDelegateToCoreService() {
        IngestCredential credential = mqttCredential("mqtt-a");
        when(ingestCredentialService.authenticateByMqttUsername("mqtt-a")).thenReturn(credential);
        when(ingestCoreService.processSamples(eq(credential), any(IngestSampleBatchDTO.class)))
                .thenReturn(new IngestResultVO());

        listener.handleOpenSamples("dcim-open/mqtt-a/samples", openBatch("dev-1"));

        verify(ingestCoreService).processSamples(eq(credential), any(IngestSampleBatchDTO.class));
    }

    @Test
    void openSamplesRejectWhenUsernameTopicMismatch() {
        // 双重校验：topic 用户名与凭证用户名不一致（模拟跨用户混投/脏数据）即拒收
        when(ingestCredentialService.authenticateByMqttUsername("mqtt-b"))
                .thenReturn(mqttCredential("mqtt-a"));

        listener.handleOpenSamples("dcim-open/mqtt-b/samples", openBatch("dev-1"));

        verify(ingestCoreService, never()).processSamples(any(), any());
        verify(sysAuditLogService).logStructured(any(), eq("mqtt-b"), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), contains("不一致"));
    }

    @Test
    void openSamplesRejectUnknownUsername() {
        when(ingestCredentialService.authenticateByMqttUsername("mqtt-ghost"))
                .thenThrow(new BusinessException(com.example.platform.common.ResultCode.INGEST_UNAUTHORIZED,
                        "MQTT 子凭证无效"));

        listener.handleOpenSamples("dcim-open/mqtt-ghost/samples", openBatch("dev-1"));

        verify(ingestCoreService, never()).processSamples(any(), any());
        verify(sysAuditLogService).logStructured(any(), eq("mqtt-ghost"), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), contains("鉴权失败"));
    }

    @Test
    void openSamplesWithoutSeqStillAccepted() {
        // 无 seq 报文：幂等降级 sampleTime（ADR-008 D4），契约校验不因 seq 缺失拒绝
        IngestCredential credential = mqttCredential("mqtt-a");
        when(ingestCredentialService.authenticateByMqttUsername("mqtt-a")).thenReturn(credential);
        when(ingestCoreService.processSamples(eq(credential), any(IngestSampleBatchDTO.class)))
                .thenReturn(new IngestResultVO());

        listener.handleOpenSamples("dcim-open/mqtt-a/samples", openBatch("dev-1"));

        ArgumentCaptor<IngestSampleBatchDTO> captor = ArgumentCaptor.forClass(IngestSampleBatchDTO.class);
        verify(ingestCoreService).processSamples(eq(credential), captor.capture());
        assertThat(captor.getValue().getItems().get(0).getSeq()).isNull();
    }

    @Test
    void openSamplesFallbackToPayloadMappingOnNonBatch() {
        // 自定义报文（非标准 batch）：回退映射模板，命中即转译进核心（ADR-009）
        IngestCredential credential = mqttCredential("mqtt-a");
        when(ingestCredentialService.authenticateByMqttUsername("mqtt-a")).thenReturn(credential);
        IngestSampleBatchDTO translated = new IngestSampleBatchDTO();
        translated.setItems(List.of(item("dev-9", "temperature", "45.2")));
        when(payloadMappingService.tryTranslate(eq(credential), any(), anyString()))
                .thenReturn(java.util.Optional.of(translated));
        when(ingestCoreService.processSamples(eq(credential), any(IngestSampleBatchDTO.class)))
                .thenReturn(new IngestResultVO());

        listener.handleOpenSamples("dcim-open/mqtt-a/samples",
                "{\"model\":\"TC-1\",\"sn\":\"dev-9\",\"data\":{\"temp\":\"452\"}}".getBytes(StandardCharsets.UTF_8));

        verify(ingestCoreService).processSamples(eq(credential), any(IngestSampleBatchDTO.class));
        verify(sysAuditLogService, never()).logStructured(any(), eq("mqtt-a"), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), anyString());
    }

    @Test
    void openSamplesSavesRawWhenNoTemplateMatches() {
        // 自定义报文且无模板命中：留存原始报文（服务内 UPSERT）并审计拒收
        IngestCredential credential = mqttCredential("mqtt-a");
        when(ingestCredentialService.authenticateByMqttUsername("mqtt-a")).thenReturn(credential);
        when(payloadMappingService.tryTranslate(eq(credential), any(), anyString()))
                .thenReturn(java.util.Optional.empty());

        listener.handleOpenSamples("dcim-open/mqtt-a/samples",
                "{\"model\":\"OTHER\"}".getBytes(StandardCharsets.UTF_8));

        verify(ingestCoreService, never()).processSamples(any(), any());
        verify(sysAuditLogService).logStructured(any(), eq("mqtt-a"), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), contains("未匹配映射模板"));
    }

    private com.example.platform.ingest.dto.IngestSampleItemDTO item(String deviceCode,
                                                                     String pointCode, String value) {
        com.example.platform.ingest.dto.IngestSampleItemDTO dto =
                new com.example.platform.ingest.dto.IngestSampleItemDTO();
        dto.setDeviceCode(deviceCode);
        dto.setPointCode(pointCode);
        dto.setValue(new BigDecimal(value));
        dto.setSampleTime(LocalDateTime.now());
        return dto;
    }

    private byte[] envelopeBytes(String secret, String itemsOverride) {
        String items = itemsOverride != null ? itemsOverride : """
                [{"deviceCode":"DEV-1","pointCode":"temperature","value":22.5,
                  "sampleTime":"2026-09-01T12:00:05","seq":10023}]""";
        // envelope.batch 独立序列化后签名——与监听器验签逻辑对称
        JsonNode batchNode = objectMapper.readTree("{\"items\":" + items + "}");
        byte[] batchJson = objectMapper.writeValueAsBytes(batchNode);
        String signature = GatewaySignUtils.signature(secret, new String(batchJson, StandardCharsets.UTF_8));
        String payload = "{\"signature\":\"" + signature + "\",\"batch\":{\"items\":" + items + "}}";
        return payload.getBytes(StandardCharsets.UTF_8);
    }
}
