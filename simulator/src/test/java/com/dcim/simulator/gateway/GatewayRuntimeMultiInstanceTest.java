package com.dcim.simulator.gateway;

import com.dcim.simulator.device.RegisterMapEntry;
import com.dcim.simulator.device.RegisterMapEntryRepository;
import com.dcim.simulator.device.VirtualDevice;
import com.dcim.simulator.device.VirtualDeviceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;


import static org.mockito.Mockito.when;

/**
 * 网关多实例注册表测试：一机房一网关实例——设备分流、独立启停、热重载、实例上限
 */
@ExtendWith(MockitoExtension.class)
class GatewayRuntimeMultiInstanceTest {

    @Mock
    private VirtualDeviceRepository deviceRepository;
    @Mock
    private RegisterMapEntryRepository registerMapRepository;
    @Mock
    private ModbusMaster modbusMaster;

    private GatewayRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = new GatewayRuntime(deviceRepository, registerMapRepository, modbusMaster);
    }

    @AfterEach
    void tearDown() {
        runtime.shutdown();
    }

    private GatewayConfig config(long id, long cloudRoomId) {
        GatewayConfig config = new GatewayConfig();
        config.setId(id);
        config.setName("网关-" + id);
        config.setCloudRoomId(cloudRoomId);
        // 不可达地址：上报失败静默返回（测试只关注轮询分流，不关注北向送达）
        config.setPlatformBaseUrl("http://127.0.0.1:1");
        config.setNorthboundTransport("HTTP");
        config.setPollIntervalSec(0);
        config.setReportIntervalSec(3600);
        config.setChangeDeadband(new BigDecimal("0.5"));
        config.setStatus(0);
        return config;
    }

    private VirtualDevice device(long id, String code, long cloudRoomId, int port) {
        VirtualDevice device = new VirtualDevice();
        device.setId(id);
        device.setDeviceCode(code);
        device.setCloudRoomId(cloudRoomId);
        device.setModbusPort(port);
        device.setStatus(0);
        return device;
    }

    private RegisterMapEntry entry(long deviceId, String pointCode) {
        RegisterMapEntry entry = new RegisterMapEntry();
        entry.setDeviceId(deviceId);
        entry.setPointCode(pointCode);
        entry.setRegisterAddr(0);
        entry.setScaleFactor(BigDecimal.TEN);
        return entry;
    }

    @Test
    @DisplayName("设备分流：实例只轮询本机房设备，快照互不串机房")
    void should_pollOnlyOwnRoomDevices_whenTwoInstancesRun() throws Exception {
        VirtualDevice th01 = device(11, "TH-01", 1L, 15021);
        VirtualDevice ups01 = device(12, "UPS-01", 2L, 15022);
        when(deviceRepository.findByStatusAndCloudRoomIdOrderById(0, 1L)).thenReturn(List.of(th01));
        when(deviceRepository.findByStatusAndCloudRoomIdOrderById(0, 2L)).thenReturn(List.of(ups01));
        when(registerMapRepository.findByDeviceIdOrderByRegisterAddrAsc(11L))
                .thenReturn(List.of(entry(11L, "temperature")));
        when(registerMapRepository.findByDeviceIdOrderByRegisterAddrAsc(12L))
                .thenReturn(List.of(entry(12L, "voltage_a")));
        when(modbusMaster.readHoldingRegisters(any(), eq(15021), anyInt(), anyInt(), anyInt()))
                .thenReturn(new int[]{235});
        when(modbusMaster.readHoldingRegisters(any(), eq(15022), anyInt(), anyInt(), anyInt()))
                .thenReturn(new int[]{2200});

        runtime.start(config(1, 1L));
        runtime.start(config(2, 2L));

        runtime.instanceOf(1L).tick();
        runtime.instanceOf(2L).tick();

        assertThat(runtime.instanceOf(1L).snapshot()).containsOnlyKeys("TH-01");
        assertThat(runtime.instanceOf(1L).snapshot().get("TH-01").get("temperature"))
                .isEqualByComparingTo("23.5");
        assertThat(runtime.instanceOf(2L).snapshot()).containsOnlyKeys("UPS-01");
        assertThat(runtime.instanceOf(2L).snapshot().get("UPS-01").get("voltage_a"))
                .isEqualByComparingTo("220.0");
        // 聚合视图兼容旧单网关消费方
        assertThat(runtime.snapshot()).containsKeys("TH-01", "UPS-01");
    }

    @Test
    @DisplayName("独立启停：A 网关停止后 B 网关照常轮询上报")
    void should_keepOtherInstanceRunning_whenOneStopped() throws Exception {
        VirtualDevice th01 = device(11, "TH-01", 1L, 15021);
        VirtualDevice ups01 = device(12, "UPS-01", 2L, 15022);
        when(deviceRepository.findByStatusAndCloudRoomIdOrderById(0, 1L)).thenReturn(List.of(th01));
        when(deviceRepository.findByStatusAndCloudRoomIdOrderById(0, 2L)).thenReturn(List.of(ups01));
        when(registerMapRepository.findByDeviceIdOrderByRegisterAddrAsc(any()))
                .thenReturn(List.of(entry(11L, "temperature")));
        when(modbusMaster.readHoldingRegisters(any(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new int[]{235});

        runtime.start(config(1, 1L));
        runtime.start(config(2, 2L));
        runtime.instanceOf(1L).tick();

        runtime.stop(1L);

        assertThat(runtime.isRunning(1L)).isFalse();
        assertThat(runtime.instanceOf(1L)).isNull();
        runtime.instanceOf(2L).tick();
        assertThat(runtime.instanceOf(2L).snapshot()).containsOnlyKeys("UPS-01");
        assertThat(runtime.runningIds()).containsExactly(2L);
    }

    @Test
    @DisplayName("热重载：同 id 重复 start 替换实例且不产生实例泄漏")
    void should_replaceInstance_withoutLeak_whenRestarted() {
        runtime.start(config(1, 1L));
        runtime.start(config(1, 1L));

        assertThat(runtime.runningIds()).containsExactly(1L);
        assertThat(runtime.isRunning(1L)).isTrue();
    }

    @Test
    @DisplayName("旧数据退化：cloud_room_id 为空的配置轮询全部设备（回填兜底）")
    void should_pollAllDevices_whenRoomBindingMissing() throws Exception {
        GatewayConfig legacy = config(9, 0L);
        legacy.setCloudRoomId(null);
        VirtualDevice th01 = device(11, "TH-01", 1L, 15021);
        VirtualDevice ups01 = device(12, "UPS-01", 2L, 15022);
        when(deviceRepository.findByStatusOrderById(0)).thenReturn(List.of(th01, ups01));
        when(registerMapRepository.findByDeviceIdOrderByRegisterAddrAsc(any()))
                .thenReturn(List.of(entry(11L, "temperature")));
        when(modbusMaster.readHoldingRegisters(any(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new int[]{235});

        runtime.start(legacy);
        runtime.instanceOf(9L).tick();

        assertThat(runtime.instanceOf(9L).snapshot()).containsKeys("TH-01", "UPS-01");
    }

    @Test
    @DisplayName("实例上限：超过 16 个网关拒绝启动")
    void should_rejectStart_whenGatewayLimitReached() {
        for (long id = 1; id <= GatewayRuntime.MAX_GATEWAYS; id++) {
            runtime.start(config(id, id));
        }
        assertThatThrownBy(() -> runtime.start(config(99L, 99L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("上限");
    }

    @Test
    @DisplayName("未保存配置拒绝启动：id 为空时抛出参数异常")
    void should_rejectStart_whenConfigNotSaved() {
        GatewayConfig unsaved = config(1, 1L);
        unsaved.setId(null);
        assertThatThrownBy(() -> runtime.start(unsaved))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("阈值缓存按网关分片：目标实例更新、未运行实例告警不覆盖他人")
    void should_updateThresholdsPerInstance() throws Exception {
        VirtualDevice th01 = device(11, "TH-01", 1L, 15021);
        when(deviceRepository.findByStatusAndCloudRoomIdOrderById(0, 1L)).thenReturn(List.of(th01));
        when(registerMapRepository.findByDeviceIdOrderByRegisterAddrAsc(11L))
                .thenReturn(List.of(entry(11L, "temperature")));
        when(modbusMaster.readHoldingRegisters(any(), eq(15021), anyInt(), anyInt(), anyInt()))
                .thenReturn(new int[]{235});
        runtime.start(config(1, 1L));

        runtime.updateThresholds(1L, Map.of("temperature",
                List.of(new ReportStrategy.Threshold("GT", new BigDecimal("35")))));
        runtime.updateThresholds(2L, Map.of()); // 未运行网关：仅告警，不影响实例 1

        runtime.instanceOf(1L).tick();
        // 实例 1 仍在运行且轮询正常（阈值更新未破坏实例）
        assertThat(runtime.isRunning(1L)).isTrue();
        assertThat(runtime.instanceOf(1L).snapshot()).containsOnlyKeys("TH-01");
    }
}
