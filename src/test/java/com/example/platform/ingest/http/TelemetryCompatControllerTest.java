package com.example.platform.ingest.http;

import com.example.platform.common.exception.BusinessException;
import com.example.platform.ingest.dto.IngestSampleBatchDTO;
import com.example.platform.ingest.dto.IngestSampleItemDTO;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.service.IngestCoreService;
import com.example.platform.ingest.service.IngestCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:TelemetryCompatController 单元测试（S8-10，ADR-008 D1/D2/D4）
 * 覆盖：TB 报文三形态 + device 扩展解析、默认设备绑定、无设备 422、
 *   token 无效拒绝、机房归属透传核心、ts 缺省取服务器时间
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("TelemetryCompatController TB 兼容遥测测试")
class TelemetryCompatControllerTest {

    @Mock
    private IngestCredentialService ingestCredentialService;
    @Mock
    private IngestCoreService ingestCoreService;

    private TelemetryCompatController controller;

    @BeforeEach
    void setUp() {
        controller = new TelemetryCompatController(ingestCredentialService, ingestCoreService,
                new ObjectMapper());
    }

    private IngestCredential credential(String defaultDevice) {
        IngestCredential credential = new IngestCredential();
        credential.setId(1L);
        credential.setAccessKey("gw-test");
        credential.setCloudRoomId(10L);
        credential.setStatus(0);
        credential.setTelemetryToken("tok-123");
        credential.setTelemetryDefaultDevice(defaultDevice);
        return credential;
    }

    @SuppressWarnings("unchecked")
    private List<IngestSampleItemDTO> capturedItems() {
        ArgumentCaptor<IngestSampleBatchDTO> captor = ArgumentCaptor.forClass(IngestSampleBatchDTO.class);
        verify(ingestCoreService, times(1)).processSamples(any(), captor.capture());
        return captor.getValue().getItems();
    }

    @Test
    @DisplayName("T1 简单对象：默认设备 + 服务器接收时间 + 数值键全量转换")
    void should_parseSimpleObject_withDefaultDevice() {
        when(ingestCredentialService.authenticateByTelemetryToken("tok-123"))
                .thenReturn(credential("tb-sensor-01"));

        controller.telemetry("tok-123", "{\"temperature\":25.1,\"humidity\":\"60\"}");

        List<IngestSampleItemDTO> items = capturedItems();
        assertThat(items).hasSize(2);
        IngestSampleItemDTO first = items.get(0);
        assertThat(first.getDeviceCode()).isEqualTo("tb-sensor-01");
        assertThat(first.getPointCode()).isEqualTo("temperature");
        assertThat(first.getValue()).isEqualByComparingTo("25.1");
        // 字符串数值转换
        assertThat(items.get(1).getValue()).isEqualByComparingTo("60");
        // seq 缺省（幂等降级 sampleTime 由核心处理）
        assertThat(first.getSeq()).isNull();
        // 时间为服务器接收时刻（整秒级断言宽松）
        assertThat(first.getSampleTime()).isNotNull();
    }

    @Test
    @DisplayName("T2 时间戳对象 + E1 device 扩展：显式设备优先，ts 毫秒解析")
    void should_parseTsObject_withExplicitDevice() {
        when(ingestCredentialService.authenticateByTelemetryToken("tok-123"))
                .thenReturn(credential("tb-default"));

        long ts = System.currentTimeMillis();
        controller.telemetry("tok-123",
                "{\"device\":\"tb-gw-meter\",\"ts\":" + ts + ",\"values\":{\"power_kw\":80.5}}");

        List<IngestSampleItemDTO> items = capturedItems();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getDeviceCode()).isEqualTo("tb-gw-meter");
        assertThat(items.get(0).getValue()).isEqualByComparingTo("80.5");
        LocalDateTime expected = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(ts), java.time.ZoneId.systemDefault());
        assertThat(items.get(0).getSampleTime().truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(expected.truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    @DisplayName("T3 数组批量：多设备多时刻展开为多条采样")
    void should_parseArrayBatch() {
        when(ingestCredentialService.authenticateByTelemetryToken("tok-123"))
                .thenReturn(credential("tb-default"));

        controller.telemetry("tok-123", "["
                + "{\"device\":\"dev-a\",\"values\":{\"temperature\":20}},"
                + "{\"device\":\"dev-b\",\"ts\":1693468800000,\"values\":{\"humidity\":55}}]");

        List<IngestSampleItemDTO> items = capturedItems();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getDeviceCode()).isEqualTo("dev-a");
        assertThat(items.get(1).getDeviceCode()).isEqualTo("dev-b");
        assertThat(items.get(1).getSampleTime().getYear()).isGreaterThanOrEqualTo(2023);
    }

    @Test
    @DisplayName("无 device 且凭证未绑定默认设备：422 拒绝且不进核心")
    void should_reject_whenNoDeviceResolvable() {
        when(ingestCredentialService.authenticateByTelemetryToken("tok-123"))
                .thenReturn(credential(null));

        assertThatThrownBy(() -> controller.telemetry("tok-123", "{\"temperature\":25.1}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("默认设备");

        verify(ingestCoreService, never()).processSamples(any(), any());
    }

    @Test
    @DisplayName("token 无效：拒绝且不进核心（authenticate 抛业务异常）")
    void should_reject_whenTokenInvalid() {
        when(ingestCredentialService.authenticateByTelemetryToken(anyString()))
                .thenThrow(new BusinessException(
                        com.example.platform.common.ResultCode.INGEST_UNAUTHORIZED, "遥测 token 无效"));

        assertThatThrownBy(() -> controller.telemetry("bad-token", "{\"a\":1}"))
                .isInstanceOf(BusinessException.class);

        verify(ingestCoreService, never()).processSamples(any(), any());
    }

    @Test
    @DisplayName("鉴权后刷新 last_seen 并透传凭证给核心（机房归属校验在核心同口径）")
    void should_refreshLastSeen_andPassCredential() {
        IngestCredential credential = credential("tb-sensor-01");
        when(ingestCredentialService.authenticateByTelemetryToken("tok-123")).thenReturn(credential);

        controller.telemetry("tok-123", "{\"temperature\":25.1}");

        verify(ingestCredentialService).refreshLastSeen(1L);
        verify(ingestCoreService).processSamples(eq(credential), any(IngestSampleBatchDTO.class));
    }

    @Test
    @DisplayName("非数值键（纯文本）丢弃不报错；非法 JSON 422")
    void should_skipNonNumeric_andRejectBadJson() {
        IngestCredential credential = credential("tb-sensor-01");
        when(ingestCredentialService.authenticateByTelemetryToken("tok-123")).thenReturn(credential);

        controller.telemetry("tok-123", "{\"temperature\":25.1,\"label\":\"机柜A\"}");
        assertThat(capturedItems()).hasSize(1);

        assertThatThrownBy(() -> controller.telemetry("tok-123", "not-json"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JSON");
    }
}
