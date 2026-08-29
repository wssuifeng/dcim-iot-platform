package com.example.platform.ingest.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.entity.DeviceType;
import com.example.platform.mapper.DeviceTypeMapper;
import com.example.platform.service.DeviceTypeService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/*
 * 类功能:DeviceTypeInferencer 单元测试
 * 覆盖：指标编码推断全类型、最长关键词优先（return_air_temp 判空调非温湿度）、
 *   显式类型码解析、无命中返回 null
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceTypeInferencer 单元测试")
class DeviceTypeInferencerTest {

    @Mock
    private DeviceTypeService deviceTypeService;

    @InjectMocks
    private DeviceTypeInferencer inferencer;

    private static final Integer UPS_ID = 1;
    private static final Integer PDU_METER_ID = 2;
    private static final Integer PRECISION_AC_ID = 3;
    private static final Integer TEMP_HUMIDITY_ID = 4;
    private static final Integer LEAK_DETECTOR_ID = 5;

    @BeforeEach
    void setUp() {
        // resolveByTypeCode 走 LambdaQueryWrapper，需要 DeviceType 表信息入缓存
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(DeviceTypeMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, DeviceType.class);

        // Wrapper 的 eq 值 = 类型码：从 paramNameValuePairs 取出后返回对应类型
        lenient().when(deviceTypeService.getOne(any())).thenAnswer(invocation -> {
            AbstractWrapper<DeviceType, ?, ?> wrapper = invocation.getArgument(0);
            // eq 参数为惰性绑定：渲染 SQL 段后才写入 paramNameValuePairs
            wrapper.getSqlSegment();
            Object typeCode = wrapper.getParamNameValuePairs().values().iterator().next();
            DeviceType type = new DeviceType();
            type.setId(typeIdOf(String.valueOf(typeCode)));
            type.setTypeCode(String.valueOf(typeCode));
            return type;
        });
    }

    private Integer typeIdOf(String typeCode) {
        return switch (typeCode) {
            case "UPS" -> UPS_ID;
            case "PDU_METER" -> PDU_METER_ID;
            case "PRECISION_AC" -> PRECISION_AC_ID;
            case "TEMP_HUMIDITY" -> TEMP_HUMIDITY_ID;
            case "LEAK_DETECTOR" -> LEAK_DETECTOR_ID;
            default -> null;
        };
    }

    @Test
    @DisplayName("temperature/humidity 推断为温湿度")
    void should_inferTempHumidity() {
        assertThat(inferencer.inferByPointCodes(List.of("temperature")))
                .isEqualTo(TEMP_HUMIDITY_ID);
        assertThat(inferencer.inferByPointCodes(List.of("humidity")))
                .isEqualTo(TEMP_HUMIDITY_ID);
    }

    @Test
    @DisplayName("voltage/current 推断为 UPS，power 推断为电表，leak 推断为漏水检测")
    void should_inferByBasicHints() {
        assertThat(inferencer.inferByPointCodes(List.of("voltage_a", "current_a")))
                .isEqualTo(UPS_ID);
        assertThat(inferencer.inferByPointCodes(List.of("power_kw", "power_kwh")))
                .isEqualTo(PDU_METER_ID);
        assertThat(inferencer.inferByPointCodes(List.of("leak_status")))
                .isEqualTo(LEAK_DETECTOR_ID);
    }

    @Test
    @DisplayName("return_air_temp 最长关键词优先：判精密空调而非温湿度")
    void should_preferLongestKeywordForAirTemp() {
        assertThat(inferencer.inferByPointCodes(List.of("return_air_temp")))
                .isEqualTo(PRECISION_AC_ID);
        assertThat(inferencer.inferByPointCodes(List.of("supply_air_temp")))
                .isEqualTo(PRECISION_AC_ID);
    }

    @Test
    @DisplayName("多编码混合时按首个命中编码投票")
    void should_voteByFirstMatchingCode() {
        // battery_level 无提示，跳过；voltage_a 命中 UPS
        assertThat(inferencer.inferByPointCodes(List.of("battery_level", "voltage_a")))
                .isEqualTo(UPS_ID);
    }

    @Test
    @DisplayName("无命中或空列表返回 null")
    void should_returnNull_whenNoHintHit() {
        assertThat(inferencer.inferByPointCodes(List.of("unknown_code"))).isNull();
        assertThat(inferencer.inferByPointCodes(null)).isNull();
        assertThat(inferencer.inferByPointCodes(List.of())).isNull();
    }

    @Test
    @DisplayName("显式类型码直接解析")
    void should_resolveByExplicitTypeCode() {
        assertThat(inferencer.resolveByTypeCode("PRECISION_AC")).isEqualTo(PRECISION_AC_ID);
        assertThat(inferencer.resolveByTypeCode(null)).isNull();
        assertThat(inferencer.resolveByTypeCode("  ")).isNull();
    }
}
