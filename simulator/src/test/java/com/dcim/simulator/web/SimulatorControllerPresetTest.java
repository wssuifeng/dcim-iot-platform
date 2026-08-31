package com.dcim.simulator.web;

import com.dcim.simulator.device.DevicePresetCatalog;
import com.dcim.simulator.device.DeviceRuntimeManager;
import com.dcim.simulator.device.EnvironmentChannel;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:SimulatorController 设备预设创建流程单元测试
 * 覆盖：预设指标映射兜底、机房环境通道只补缺失不覆盖、档案缺失时先建默认档案、
 *   手工映射优先于预设、重复编码拒绝、未知预设 ID 正常创建
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimulatorController 设备预设创建单元测试")
class SimulatorControllerPresetTest {

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

    private static final Long ROOM_ID = 4L;

    @BeforeEach
    void setUp() {
        lenient().when(lifecycle.nextAvailablePort()).thenReturn(15020);
        lenient().when(deviceRepository.save(any())).thenAnswer(invocation -> {
            VirtualDevice device = invocation.getArgument(0);
            device.setId(9L);
            return device;
        });
        lenient().when(channelRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private SimulatorController.CreateDeviceRequest request(String presetId, List<SimulatorController.RegisterMapRequest> registers) {
        SimulatorController.CreateDeviceRequest request = new SimulatorController.CreateDeviceRequest();
        request.setDeviceCode("UPS-TEST-01");
        request.setCloudRoomId(ROOM_ID);
        request.setTypeId(1);
        request.setDeviceName("UPS 测试设备");
        request.setPresetId(presetId);
        request.setRegisters(registers);
        return request;
    }

    private SimulatorController.RegisterMapRequest register(String pointCode, int addr, double scale) {
        SimulatorController.RegisterMapRequest item = new SimulatorController.RegisterMapRequest();
        item.setPointCode(pointCode);
        item.setRegisterAddr(addr);
        item.setScaleFactor(scale);
        return item;
    }

    @Test
    @DisplayName("UPS 预设创建：应用预设指标映射并补齐缺失环境通道")
    void should_applyPresetRegisters_and_ensureChannels() {
        when(profileRepository.findByCloudRoomId(ROOM_ID))
                .thenReturn(Optional.of(profileWithId(33L)));
        when(channelRepository.findByProfileIdAndPointCode(anyLong(), anyString()))
                .thenReturn(List.of());

        ResponseEntity<?> response = controller.createDevice(request("ups", null));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<RegisterMapEntry> captor = ArgumentCaptor.forClass(RegisterMapEntry.class);
        verify(registerMapRepository, times(4)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(RegisterMapEntry::getPointCode)
                .containsExactly("voltage_a", "current_a", "battery_level", "output_load");
        assertThat(captor.getAllValues()).allSatisfy(entry -> {
            assertThat(entry.getDeviceId()).isEqualTo(9L);
            assertThat(entry.getScaleFactor()).isEqualByComparingTo("10");
        });
        // 4 个预设通道全部缺失 → 全部补齐
        verify(channelRepository, times(4)).save(any(EnvironmentChannel.class));
        verify(lifecycle).restartDevice(any());
    }

    @Test
    @DisplayName("已存在的同指标通道不覆盖（保留已调参值）")
    void should_notOverwriteExistingChannels() {
        when(profileRepository.findByCloudRoomId(ROOM_ID))
                .thenReturn(Optional.of(profileWithId(33L)));
        when(channelRepository.findByProfileIdAndPointCode(33L, "voltage_a"))
                .thenReturn(List.of(new EnvironmentChannel()));
        when(channelRepository.findByProfileIdAndPointCode(33L, "current_a"))
                .thenReturn(List.of(new EnvironmentChannel()));
        when(channelRepository.findByProfileIdAndPointCode(33L, "battery_level"))
                .thenReturn(List.of());
        when(channelRepository.findByProfileIdAndPointCode(33L, "output_load"))
                .thenReturn(List.of());

        controller.createDevice(request("ups", null));

        // 只有 battery_level / output_load 两个缺失通道被补齐
        verify(channelRepository, times(2)).save(any(EnvironmentChannel.class));
    }

    @Test
    @DisplayName("机房无环境档案时先建默认档案再补预设通道")
    void should_createProfileWithDefaults_thenPresetChannels() {
        when(profileRepository.findByCloudRoomId(ROOM_ID)).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(invocation -> profileWithId(77L));
        when(channelRepository.findByProfileIdAndPointCode(anyLong(), anyString()))
                .thenReturn(List.of());

        controller.createDevice(request("ups", null));

        // 默认通道 1（累计电量）+ UPS 预设通道 4（温湿度/功率由物理引擎接管）
        verify(channelRepository, times(5)).save(any(EnvironmentChannel.class));
    }

    @Test
    @DisplayName("手工填了指标映射时不应用预设映射")
    void should_preferManualRegisters_overPreset() {
        when(profileRepository.findByCloudRoomId(ROOM_ID))
                .thenReturn(Optional.of(profileWithId(33L)));
        when(channelRepository.findByProfileIdAndPointCode(anyLong(), anyString()))
                .thenReturn(List.of());

        controller.createDevice(request("ups",
                List.of(register("temperature", 5, 10))));

        ArgumentCaptor<RegisterMapEntry> captor = ArgumentCaptor.forClass(RegisterMapEntry.class);
        verify(registerMapRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getPointCode()).isEqualTo("temperature");
        assertThat(captor.getValue().getRegisterAddr()).isEqualTo(5);
    }

    @Test
    @DisplayName("设备编码重复返回 400 且不保存")
    void should_rejectDuplicateDeviceCode() {
        when(deviceRepository.findByDeviceCode("UPS-TEST-01"))
                .thenReturn(Optional.of(new VirtualDevice()));

        ResponseEntity<?> response = controller.createDevice(request("ups", null));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(registerMapRepository, never()).save(any());
        verify(lifecycle, never()).restartDevice(any());
    }

    @Test
    @DisplayName("编码为空返回可行动的错误信息而非裸 Bad Request")
    void should_rejectBlankCode_withFriendlyMessage() {
        SimulatorController.CreateDeviceRequest blank = request("ups", null);
        blank.setDeviceCode(" ");

        ResponseEntity<?> response = controller.createDevice(blank);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).asString().contains("设备编码不能为空");
        verify(deviceRepository, never()).save(any());
    }

    @Test
    @DisplayName("机房缺失返回明确错误信息")
    void should_rejectMissingRoom_withFriendlyMessage() {
        SimulatorController.CreateDeviceRequest noRoom = request("ups", null);
        noRoom.setCloudRoomId(null);

        ResponseEntity<?> response = controller.createDevice(noRoom);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).asString().contains("必须选择机房");
    }

    @Test
    @DisplayName("未知预设 ID 按自定义设备正常创建（不补通道）")
    void should_createNormally_withUnknownPreset() {
        controller.createDevice(request("no-such-preset", null));

        verify(registerMapRepository, never()).save(any());
        verify(channelRepository, never()).save(any());
        verify(lifecycle).restartDevice(any());
    }

    @Test
    @DisplayName("预设目录包含全部 5 类且控制器端点可读取")
    void should_exposePresetEndpoint() {
        List<DevicePresetCatalog.DevicePreset> presets = controller.listDevicePresets();
        assertThat(presets).hasSize(5);
    }

    private RoomEnvironmentProfile profileWithId(Long id) {
        RoomEnvironmentProfile profile = new RoomEnvironmentProfile();
        profile.setId(id);
        profile.setCloudRoomId(ROOM_ID);
        profile.setSeed(42L);
        return profile;
    }
}
