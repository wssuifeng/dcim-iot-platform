package com.dcim.simulator.lifecycle;

import com.dcim.simulator.device.DeviceRuntimeManager;
import com.dcim.simulator.device.RegisterMapEntryRepository;
import com.dcim.simulator.device.VirtualDevice;
import com.dcim.simulator.device.VirtualDeviceRepository;
import com.dcim.simulator.gateway.GatewayConfig;
import com.dcim.simulator.gateway.GatewayConfigRepository;
import com.dcim.simulator.gateway.GatewayRuntime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 旧网关数据机房绑定回填测试：cloud_room_id 为空的配置按设备归属推断
 */
@ExtendWith(MockitoExtension.class)
class SimulatorLifecycleServiceBackfillTest {

    @Mock
    private VirtualDeviceRepository deviceRepository;
    @Mock
    private RegisterMapEntryRepository registerMapRepository;
    @Mock
    private DeviceRuntimeManager deviceRuntimeManager;
    @Mock
    private GatewayConfigRepository gatewayConfigRepository;
    @Mock
    private GatewayRuntime gatewayRuntime;

    private SimulatorLifecycleService service;

    private VirtualDevice device(long id, String code, long cloudRoomId) {
        VirtualDevice device = new VirtualDevice();
        device.setId(id);
        device.setDeviceCode(code);
        device.setCloudRoomId(cloudRoomId);
        return device;
    }

    private SimulatorLifecycleService service() {
        return new SimulatorLifecycleService(deviceRepository, registerMapRepository,
                deviceRuntimeManager, gatewayConfigRepository, gatewayRuntime);
    }

    @Test
    @DisplayName("全部设备同机房：空绑定回填为该机房")
    void should_backfillSingleRoom_whenAllDevicesInOneRoom() {
        when(deviceRepository.findAll()).thenReturn(List.of(
                device(1, "TH-01", 5L), device(2, "UPS-01", 5L)));
        GatewayConfig legacy = new GatewayConfig();
        legacy.setId(1L);
        legacy.setCloudRoomId(null);
        when(gatewayConfigRepository.findAll()).thenReturn(List.of(legacy));

        service().backfillGatewayRoomBinding();

        ArgumentCaptor<GatewayConfig> captor = ArgumentCaptor.forClass(GatewayConfig.class);
        verify(gatewayConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getCloudRoomId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("设备跨机房无法推断：空绑定回填 -1 占位待人工绑定")
    void should_backfillMinusOne_whenDevicesSpanRooms() {
        when(deviceRepository.findAll()).thenReturn(List.of(
                device(1, "TH-01", 1L), device(2, "UPS-01", 2L)));
        GatewayConfig legacy = new GatewayConfig();
        legacy.setId(1L);
        when(gatewayConfigRepository.findAll()).thenReturn(List.of(legacy));

        service().backfillGatewayRoomBinding();

        ArgumentCaptor<GatewayConfig> captor = ArgumentCaptor.forClass(GatewayConfig.class);
        verify(gatewayConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getCloudRoomId()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("已绑定配置不回填；无设备时回填 -1")
    void should_skipBoundConfig_andBackfillMinusOne_whenNoDevices() {
        when(deviceRepository.findAll()).thenReturn(List.of());
        GatewayConfig bound = new GatewayConfig();
        bound.setId(1L);
        bound.setCloudRoomId(3L);
        GatewayConfig legacy = new GatewayConfig();
        legacy.setId(2L);
        when(gatewayConfigRepository.findAll()).thenReturn(List.of(bound, legacy));

        service().backfillGatewayRoomBinding();

        ArgumentCaptor<GatewayConfig> captor = ArgumentCaptor.forClass(GatewayConfig.class);
        verify(gatewayConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getCloudRoomId()).isEqualTo(-1L);
        verify(gatewayConfigRepository, never()).save(bound);
    }
}
