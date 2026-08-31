package com.dcim.simulator.device;

import java.util.List;

/*
 * 类功能:虚拟设备预设目录——平台 5 类设备的标准指标映射与环境通道默认值
 *
 * 说明:pointCode 与平台指标字典对齐（平台 V16 补齐精密空调/漏水检测/UPS 增强）；
 *   室内环境类指标（温湿度/送回风/功率）由环境物理引擎接管——设备从机房室内状态
 *   读值并叠加自身偏差，不在此配置通道；通道仅覆盖设备电气类指标。
 *   通道为机房级默认值——同机房同指标共享，创建预设设备时只补缺失通道，
 *   不覆盖已调参通道；温湿度预设不带通道（机房档案默认通道已含累计电量）。
 * */
public final class DevicePresetCatalog {

    private DevicePresetCatalog() {
    }

    /** 单个指标映射：平台指标编码 + Modbus 保持寄存器地址 + 工程量缩放系数 */
    public record RegisterSpec(String pointCode, int registerAddr, double scaleFactor) {
    }

    /** 环境通道默认值：函数类型 + 基值/振幅/周期/斜率（与环境引擎求值参数一致） */
    public record ChannelSpec(String pointCode, String funcType, double base,
                              Double amplitude, Integer periodSec, Double slopePerSec) {
    }

    /** 设备预设：一键创建所需的全部参数 */
    public record DevicePreset(String presetId, Integer typeId, String typeCode, String typeName,
                               String description, List<RegisterSpec> registers, List<ChannelSpec> channels) {
    }

    // 电表电量寄存器为 int16：scale 10 下 RAMP 累计约 17 天回绕，真实 int16 寄存器语义
    public static final List<DevicePreset> PRESETS = List.of(
            new DevicePreset("temp-humidity", 4, "TEMP_HUMIDITY", "温湿度传感器",
                    "温度/湿度双指标，感知机房室内热湿状态（物理引擎）",
                    List.of(
                            new RegisterSpec("temperature", 0, 10),
                            new RegisterSpec("humidity", 1, 10)),
                    List.of()),
            new DevicePreset("ups", 1, "UPS", "UPS 不间断电源",
                    "A相电压/电流 + 电池电量 + 输出负载率",
                    List.of(
                            new RegisterSpec("voltage_a", 0, 10),
                            new RegisterSpec("current_a", 1, 10),
                            new RegisterSpec("battery_level", 2, 10),
                            new RegisterSpec("output_load", 3, 10)),
                    List.of(
                            new ChannelSpec("voltage_a", "CONSTANT", 220.0, null, null, null),
                            new ChannelSpec("current_a", "SINUSOID", 60.0, 20.0, 86400, null),
                            new ChannelSpec("battery_level", "SINUSOID", 92.0, 6.0, 86400, null),
                            new ChannelSpec("output_load", "SINUSOID", 65.0, 20.0, 86400, null))),
            new DevicePreset("pdu-meter", 2, "PDU_METER", "智能电表/PDU",
                    "电压/电流 + 有功功率（随 IT 负载与空调电耗）+ 累计电量",
                    List.of(
                            new RegisterSpec("voltage_a", 0, 10),
                            new RegisterSpec("current_a", 1, 10),
                            new RegisterSpec("power_kw", 2, 100),
                            new RegisterSpec("power_kwh", 3, 10)),
                    List.of(
                            new ChannelSpec("voltage_a", "CONSTANT", 220.0, null, null, null),
                            new ChannelSpec("current_a", "SINUSOID", 60.0, 20.0, 86400, null),
                            new ChannelSpec("power_kwh", "RAMP", 0.0, null, null, 0.0222))),
            new DevicePreset("precision-ac", 3, "PRECISION_AC", "精密空调",
                    "送风/回风温度取自机房室内状态，制冷除湿真实反作用机房环境",
                    List.of(
                            new RegisterSpec("supply_air_temp", 0, 10),
                            new RegisterSpec("return_air_temp", 1, 10)),
                    List.of()),
            new DevicePreset("leak-detector", 5, "LEAK_DETECTOR", "漏水检测器",
                    "漏水状态 0/1，配合快捷操作做漏水告警演练",
                    List.of(
                            new RegisterSpec("leak_status", 0, 1)),
                    List.of(
                            new ChannelSpec("leak_status", "CONSTANT", 0.0, null, null, null))));

    /*
     * 方法功能:按预设ID查找设备预设
     * 参数:presetId-预设ID（如 ups / precision-ac）
     * 返回数据:DevicePreset-匹配的预设，未匹配返回 null
     * */
    public static DevicePreset byId(String presetId) {
        if (presetId == null || presetId.isBlank()) {
            return null;
        }
        return PRESETS.stream()
                .filter(p -> p.presetId().equals(presetId))
                .findFirst()
                .orElse(null);
    }
}
