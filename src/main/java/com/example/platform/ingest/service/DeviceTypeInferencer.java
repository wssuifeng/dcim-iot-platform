package com.example.platform.ingest.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.entity.DeviceType;
import com.example.platform.service.DeviceTypeService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/*
 * 类功能:首包自动注册的设备类型解析器——按网关上报线索智能匹配平台设备类型
 *
 * 【匹配优先级】清单显式类型码 > 指标编码推断 > 凭证默认类型（兜底）。
 * 指标推断规则与平台指标目录对齐：temperature/humidity→温湿度，
 * voltage/current→UPS，power→电表，leak→漏水检测。
 * */
@Component
public class DeviceTypeInferencer {

    private final DeviceTypeService deviceTypeService;

    // 指标编码片段（前缀/包含匹配）→ 类型码（与 device_types.type_code 对齐）
    private static final Map<String, String> POINT_HINTS = Map.ofEntries(
            Map.entry("temp", "TEMP_HUMIDITY"),
            Map.entry("hum", "TEMP_HUMIDITY"),
            Map.entry("voltage", "UPS"),
            Map.entry("current", "UPS"),
            Map.entry("power", "PDU_METER"),
            Map.entry("leak", "LEAK_DETECTOR"));

    public DeviceTypeInferencer(DeviceTypeService deviceTypeService) {
        this.deviceTypeService = deviceTypeService;
    }

    /*
     * 方法功能:按清单显式类型码解析平台类型ID
     * 参数:deviceTypeCode-类型码（如 TEMP_HUMIDITY），可空
     * 返回数据:Integer-类型ID，未匹配返回 null
     * */
    public Integer resolveByTypeCode(String deviceTypeCode) {
        if (deviceTypeCode == null || deviceTypeCode.isBlank()) {
            return null;
        }
        DeviceType type = deviceTypeService.getOne(new LambdaQueryWrapper<DeviceType>()
                .eq(DeviceType::getTypeCode, deviceTypeCode));
        return type == null ? null : type.getId();
    }

    /*
     * 方法功能:按上报指标编码集合推断平台类型ID
     * 参数:pointCodes-网关上报的指标编码（任一命中即投票）
     * 返回数据:Integer-类型ID，无法推断返回 null
     * */
    public Integer inferByPointCodes(List<String> pointCodes) {
        if (pointCodes == null) {
            return null;
        }
        for (String pointCode : pointCodes) {
            if (pointCode == null) {
                continue;
            }
            String lower = pointCode.toLowerCase();
            for (Map.Entry<String, String> hint : POINT_HINTS.entrySet()) {
                if (lower.contains(hint.getKey())) {
                    Integer typeId = resolveByTypeCode(hint.getValue());
                    if (typeId != null) {
                        return typeId;
                    }
                }
            }
        }
        return null;
    }
}
