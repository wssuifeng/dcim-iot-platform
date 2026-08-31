package com.dcim.simulator.device;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 类功能:DevicePresetCatalog 单元测试
 * 覆盖：5 类平台设备预设齐全、指标映射地址连续无冲突、通道参数与函数类型匹配、
 *   按 ID 查找与未匹配兜底
 * */
@DisplayName("DevicePresetCatalog 单元测试")
class DevicePresetCatalogTest {

    @Test
    @DisplayName("预设覆盖平台全部 5 类设备且 ID 唯一")
    void should_coverAllFivePlatformTypes() {
        assertThat(DevicePresetCatalog.PRESETS).hasSize(5);
        Set<String> ids = new HashSet<>();
        Set<Integer> typeIds = new HashSet<>();
        DevicePresetCatalog.PRESETS.forEach(p -> {
            ids.add(p.presetId());
            typeIds.add(p.typeId());
        });
        assertThat(ids).hasSize(5);
        assertThat(typeIds).hasSize(5);
        assertThat(ids).containsExactlyInAnyOrder(
                "temp-humidity", "ups", "pdu-meter", "precision-ac", "leak-detector");
    }

    @Test
    @DisplayName("每类预设的寄存器地址从 0 连续且指标编码不重复")
    void should_haveSequentialUniqueRegisters() {
        for (DevicePresetCatalog.DevicePreset preset : DevicePresetCatalog.PRESETS) {
            List<DevicePresetCatalog.RegisterSpec> registers = preset.registers();
            Set<String> pointCodes = new HashSet<>();
            for (int i = 0; i < registers.size(); i++) {
                DevicePresetCatalog.RegisterSpec register = registers.get(i);
                assertThat(register.registerAddr())
                        .as("%s 第 %d 个映射应使用连续地址", preset.presetId(), i)
                        .isEqualTo(i);
                pointCodes.add(register.pointCode());
                assertThat(register.scaleFactor()).isPositive();
            }
            assertThat(pointCodes).hasSize(registers.size());
        }
    }

    @Test
    @DisplayName("通道参数与函数类型匹配（SINUSOID 带振幅周期，RAMP 带斜率）")
    void should_haveWellFormedChannels() {
        for (DevicePresetCatalog.DevicePreset preset : DevicePresetCatalog.PRESETS) {
            for (DevicePresetCatalog.ChannelSpec channel : preset.channels()) {
                assertThat(channel.base()).isNotNull();
                switch (channel.funcType()) {
                    case "SINUSOID" -> {
                        assertThat(channel.amplitude()).as("%s/%s 振幅", preset.presetId(), channel.pointCode()).isNotNull();
                        assertThat(channel.periodSec()).isPositive();
                    }
                    case "RAMP" -> assertThat(channel.slopePerSec())
                            .as("%s/%s 斜率", preset.presetId(), channel.pointCode()).isNotNull();
                    case "CONSTANT" -> { /* 仅需基值 */ }
                    default -> throw new AssertionError("预设目录不应使用函数 " + channel.funcType());
                }
            }
        }
    }

    @Test
    @DisplayName("温湿度/精密空调预设不带通道（室内指标由物理引擎接管），其余带通道")
    void should_omitChannelsForPhysicsDrivenTypes() {
        assertThat(DevicePresetCatalog.byId("temp-humidity").channels()).isEmpty();
        assertThat(DevicePresetCatalog.byId("precision-ac").channels()).isEmpty();
        for (String id : List.of("ups", "pdu-meter", "leak-detector")) {
            assertThat(DevicePresetCatalog.byId(id).channels()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("按 ID 查找命中，未匹配与空值返回 null")
    void should_findByIdOrNull() {
        assertThat(DevicePresetCatalog.byId("ups").typeName()).isEqualTo("UPS 不间断电源");
        assertThat(DevicePresetCatalog.byId("unknown")).isNull();
        assertThat(DevicePresetCatalog.byId(null)).isNull();
        assertThat(DevicePresetCatalog.byId(" ")).isNull();
    }
}
