package com.dcim.simulator.web;

import com.dcim.simulator.gateway.GatewayConfig;
import com.dcim.simulator.gateway.GatewayConfigRepository;
import com.dcim.simulator.gateway.GatewayRuntime;
import com.dcim.simulator.scenario.ScenarioPlayer;
import com.dcim.simulator.scenario.ScenarioRepository;
import com.dcim.simulator.scenario.ScenarioStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 网关机房绑定校验测试：一机房一网关——缺失必填拒绝、重复绑定拒绝、-1 占位放行
 */
@ExtendWith(MockitoExtension.class)
class GatewayConfigRoomBindingTest {

    @Mock
    private GatewayConfigRepository configRepository;
    @Mock
    private ScenarioRepository scenarioRepository;
    @Mock
    private ScenarioStepRepository stepRepository;
    @Mock
    private ScenarioPlayer scenarioPlayer;
    @Mock
    private com.dcim.simulator.device.VirtualDeviceRepository deviceRepository;
    @Mock
    private com.dcim.simulator.device.RegisterMapEntryRepository registerMapRepository;
    @Mock
    private com.dcim.simulator.gateway.GatewayRuntime gatewayRuntime;

    private GatewayScenarioController controller;

    @BeforeEach
    void setUp() {
        controller = new GatewayScenarioController(configRepository,
                scenarioRepository, stepRepository, scenarioPlayer,
                deviceRepository, registerMapRepository, gatewayRuntime,
                new PlatformDirectoryCache());
    }

    private GatewayConfig draft(Long id, Long cloudRoomId) {
        GatewayConfig config = new GatewayConfig();
        config.setId(id);
        config.setName("网关-" + cloudRoomId);
        config.setCloudRoomId(cloudRoomId);
        config.setPlatformBaseUrl("http://localhost:8080");
        config.setIngestAccessKey("ak");
        config.setIngestSecret("sk");
        config.setStatus(1); // 不触发启动，专注校验逻辑
        return config;
    }

    @Test
    @DisplayName("cloud_room_id 缺失：创建被拒绝")
    void should_rejectCreate_whenRoomMissing() {
        GatewayConfig draft = draft(null, null);

        ResponseEntity<?> response = controller.createGateway(draft);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("机房已被其他网关绑定：创建被拒绝（一机房一网关）")
    void should_rejectCreate_whenRoomAlreadyBound() {
        GatewayConfig existing = draft(1L, 3L);
        when(configRepository.findAll()).thenReturn(List.of(existing));

        ResponseEntity<?> response = controller.createGateway(draft(null, 3L));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(configRepository, never()).save(any());
    }

    @Test
    @DisplayName("机房未被占用：创建成功并保存")
    void should_create_whenRoomFree() {
        when(configRepository.findAll()).thenReturn(List.of());
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.createGateway(draft(null, 4L));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(configRepository).save(any());
    }

    @Test
    @DisplayName("-1 占位绑定（旧数据待人工绑定）：多个并存不判重复")
    void should_allowMultiplePlaceholders_whenRoomIsMinusOne() {
        // -1 占位在绑定校验中短路放行，不触发占用查询
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.createGateway(draft(null, -1L));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("部分更新：body 不带凭证（列表响应不含密钥）时保留原凭证")
    void should_keepCredentials_whenUpdateOmitsThem() {
        GatewayConfig existing = draft(1L, 3L);
        when(configRepository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(configRepository.findAll()).thenReturn(List.of(existing));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        GatewayConfig body = draft(1L, 3L);
        body.setIngestAccessKey(null);
        body.setIngestSecret(null);

        ResponseEntity<?> response = controller.updateGateway(1L, body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(existing.getIngestAccessKey()).isEqualTo("ak");
        assertThat(existing.getIngestSecret()).isEqualTo("sk");
    }

    @Test
    @DisplayName("地址归一化：填完整地址（含 /api 与尾斜杠）统一存根地址")
    void should_normalizeBaseUrl_withContextPath() throws Exception {
        java.lang.reflect.Field field = GatewayScenarioController.class.getDeclaredField("platformBaseUrl");
        field.setAccessible(true);
        field.set(controller, "http://localhost:8080");

        GatewayConfig existing = draft(1L, 3L);
        when(configRepository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(configRepository.findAll()).thenReturn(List.of(existing));
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        GatewayConfig body = draft(1L, 3L);
        body.setPlatformBaseUrl("http://192.168.1.5:8081/api/");

        controller.updateGateway(1L, body);

        assertThat(existing.getPlatformBaseUrl()).isEqualTo("http://192.168.1.5:8081");
    }

    @Test
    @DisplayName("地址留空：创建时回退服务端默认（8080 口径）")
    void should_fallbackToServerDefault_whenUrlBlank() throws Exception {
        java.lang.reflect.Field field = GatewayScenarioController.class.getDeclaredField("platformBaseUrl");
        field.setAccessible(true);
        field.set(controller, "http://localhost:8080");
        when(configRepository.findAll()).thenReturn(List.of());
        when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        GatewayConfig body = draft(null, 4L);
        body.setPlatformBaseUrl(" ");

        controller.createGateway(body);

        assertThat(body.getPlatformBaseUrl()).isEqualTo("http://localhost:8080");
    }

    @Test
    @DisplayName("编辑时绑定被其他网关占用的机房：拒绝且不重载")
    void should_rejectUpdate_whenRoomBoundByOther() {
        GatewayConfig other = draft(2L, 5L);
        when(configRepository.findById(1L)).thenReturn(java.util.Optional.of(draft(1L, 3L)));
        when(configRepository.findAll()).thenReturn(List.of(other));

        ResponseEntity<?> response = controller.updateGateway(1L, draft(1L, 5L));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(configRepository, never()).save(any());
        verify(gatewayRuntime, never()).start(any());
    }
}
