package com.dcim.simulator.web;

import com.dcim.simulator.device.DeviceRuntimeManager;
import com.dcim.simulator.device.EnvironmentChannelRepository;
import com.dcim.simulator.device.RegisterMapEntry;
import com.dcim.simulator.device.RegisterMapEntryRepository;
import com.dcim.simulator.device.RoomEnvironmentProfile;
import com.dcim.simulator.device.RoomEnvironmentProfileRepository;
import com.dcim.simulator.device.VirtualDevice;
import com.dcim.simulator.device.VirtualDeviceRepository;
import com.dcim.simulator.gateway.GatewayConfigRepository;
import com.dcim.simulator.gateway.GatewayRuntime;
import com.dcim.simulator.lifecycle.SimulatorLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:批量设备套件工厂单元测试
 * 覆盖：按数量逐台创建并应用编码规则、编码冲突跳过不中断、环境档案缺失时先建、前缀超长拒绝
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimulatorController 批量设备套件单元测试")
class SimulatorControllerDeviceSuiteTest {

    @Mock
    private VirtualDeviceRepository deviceRepository;
    @Mock
    private RegisterMapEntryRepository registerMapRepository;
    @Mock
    private RoomEnvironmentProfileRepository profileRepository;
    @Mock
    private EnvironmentChannelRepository channelRepository;
    @Mock
    private DeviceRuntimeManager deviceRuntimeManager;
    @Mock
    private SimulatorLifecycleService lifecycle;
    @Mock
    private GatewayRuntime gatewayRuntime;
    @Mock
    private GatewayConfigRepository gatewayConfigRepository;

    @InjectMocks
    private SimulatorController controller;

    private static final Long ROOM_ID = 7L;

    @BeforeEach
    void setUp() {
        lenient().when(lifecycle.nextAvailablePort()).thenReturn(15020);
        lenient().when(deviceRepository.save(any())).thenAnswer(invocation -> {
            VirtualDevice device = invocation.getArgument(0);
            device.setId(9L);
            return device;
        });
        // 档案仓储状态化：首次查询为空，save 后可查到（模拟真实 DB 行为）
        java.util.concurrent.atomic.AtomicReference<RoomEnvironmentProfile> profileHolder =
                new java.util.concurrent.atomic.AtomicReference<>();
        lenient().when(profileRepository.findByCloudRoomId(ROOM_ID))
                .thenAnswer(inv -> java.util.Optional.ofNullable(profileHolder.get()));
        lenient().when(profileRepository.save(any(RoomEnvironmentProfile.class)))
                .thenAnswer(inv -> { profileHolder.set(inv.getArgument(0)); return inv.getArgument(0); });
        lenient().when(channelRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private SimulatorController.DeviceSuiteRequest suite(int th, int ups, int pdu, int ac, int leak) {
        SimulatorController.DeviceSuiteRequest request = new SimulatorController.DeviceSuiteRequest();
        request.setTempHumidity(th);
        request.setUps(ups);
        request.setPduMeter(pdu);
        request.setPrecisionAc(ac);
        request.setLeakDetector(leak);
        request.setCodePrefix("DC7");
        return request;
    }

    @Test
    @DisplayName("按数量创建：编码按 前缀-缩写+序号 生成，档案缺失先建，预设映射写入")
    @SuppressWarnings("unchecked")
    void should_createSuiteDevices_withCodeScheme() {
        when(deviceRepository.findByDeviceCode(any())).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.createDeviceSuite(ROOM_ID, suite(2, 1, 0, 0, 0));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("created")).isEqualTo(3);
        verify(deviceRepository, times(3)).save(any());
        org.mockito.ArgumentCaptor<VirtualDevice> captor =
                org.mockito.ArgumentCaptor.forClass(VirtualDevice.class);
        verify(deviceRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(VirtualDevice::getDeviceCode)
                .containsExactly("DC7-TH01", "DC7-TH02", "DC7-UPS01");
        // 预设指标映射按 typeId 解析写入（温湿度 2 指标 ×2 台 + UPS 4 指标 = 8 行）
        org.mockito.ArgumentCaptor<RegisterMapEntry> regCaptor =
                org.mockito.ArgumentCaptor.forClass(RegisterMapEntry.class);
        verify(registerMapRepository, times(8)).save(regCaptor.capture());
        // 档案已创建
        verify(profileRepository).save(any(RoomEnvironmentProfile.class));
    }

    @Test
    @DisplayName("编码冲突跳过不中断：冲突台计入 skipped，其余照常创建")
    @SuppressWarnings("unchecked")
    void should_skipConflict_andContinue() {
        // 兜底桩在前，精确冲突桩在后（Mockito 命中最后一个匹配桩）
        when(deviceRepository.findByDeviceCode(org.mockito.ArgumentMatchers.startsWith("DC7-TH0")))
                .thenAnswer(inv -> Optional.empty());
        when(deviceRepository.findByDeviceCode("DC7-TH01"))
                .thenReturn(Optional.of(new VirtualDevice()));

        ResponseEntity<?> response = controller.createDeviceSuite(ROOM_ID, suite(2, 0, 0, 0, 0));

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body.get("created")).isEqualTo(1);
        List<Map<String, String>> skipped = (List<Map<String, String>>) body.get("skipped");
        assertThat(skipped).hasSize(1);
        assertThat(skipped.get(0).get("deviceCode")).isEqualTo("DC7-TH01");
        verify(deviceRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("档案已存在时不重复创建")
    void should_reuseExistingProfile() {
        when(profileRepository.findByCloudRoomId(ROOM_ID))
                .thenReturn(Optional.of(new RoomEnvironmentProfile()));
        when(deviceRepository.findByDeviceCode(any())).thenReturn(Optional.empty());

        controller.createDeviceSuite(ROOM_ID, suite(1, 0, 0, 0, 0));

        verify(profileRepository, times(1)).findByCloudRoomId(ROOM_ID);
        verify(profileRepository, times(0)).save(any(RoomEnvironmentProfile.class));
    }

    @Test
    @DisplayName("前缀超长拒绝：>20 字符返回 400")
    void should_reject_whenPrefixTooLong() {
        SimulatorController.DeviceSuiteRequest request = suite(1, 0, 0, 0, 0);
        request.setCodePrefix("X".repeat(21));

        ResponseEntity<?> response = controller.createDeviceSuite(ROOM_ID, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}
