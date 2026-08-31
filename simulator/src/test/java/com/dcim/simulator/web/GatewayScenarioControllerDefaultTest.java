package com.dcim.simulator.web;

import com.dcim.simulator.gateway.GatewayConfig;
import com.dcim.simulator.gateway.GatewayConfigRepository;
import com.dcim.simulator.scenario.ScenarioPlayer;
import com.dcim.simulator.scenario.ScenarioRepository;
import com.dcim.simulator.scenario.ScenarioStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 网关配置接口默认值测试：无已保存配置时 GET /api/sim/gateway
 * 必须返回服务端默认（平台地址 8081 口径），而不是空——
 * 防止管理页（含旧缓存页面）显示过期默认地址
 */
@ExtendWith(MockitoExtension.class)
class GatewayScenarioControllerDefaultTest {

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
    void setUp() throws Exception {
        controller = new GatewayScenarioController(configRepository,
                scenarioRepository, stepRepository, scenarioPlayer,
                deviceRepository, registerMapRepository, gatewayRuntime,
                new com.dcim.simulator.web.PlatformDirectoryCache());
        Field field = GatewayScenarioController.class.getDeclaredField("platformBaseUrl");
        field.setAccessible(true);
        field.set(controller, "http://localhost:8081");
    }

    @Test
    @DisplayName("无已保存配置时返回服务端默认：平台地址 8081 口径")
    void should_returnServerDefault_whenNoConfigSaved() {
        when(configRepository.findAll()).thenReturn(List.of());

        GatewayConfig config = controller.getGateway();

        assertThat(config).isNotNull();
        assertThat(config.getPlatformBaseUrl()).isEqualTo("http://localhost:8081");
        assertThat(config.getName()).isEqualTo("仿真网关01");
        assertThat(config.getNorthboundTransport()).isEqualTo("HTTP");
        assertThat(config.getStatus()).isZero();
    }

    @Test
    @DisplayName("已保存配置优先：返回库内配置而非默认")
    void should_returnSavedConfig_whenExists() {
        GatewayConfig saved = new GatewayConfig();
        saved.setId(1L);
        saved.setName("现场网关");
        saved.setPlatformBaseUrl("http://192.168.1.10:8080");
        when(configRepository.findAll()).thenReturn(List.of(saved));

        GatewayConfig config = controller.getGateway();

        assertThat(config.getPlatformBaseUrl()).isEqualTo("http://192.168.1.10:8080");
        assertThat(config.getName()).isEqualTo("现场网关");
    }
}
