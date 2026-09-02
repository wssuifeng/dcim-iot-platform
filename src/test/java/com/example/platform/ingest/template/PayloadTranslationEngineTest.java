package com.example.platform.ingest.template;

import com.example.platform.ingest.dto.PayloadFeatureRule;
import com.example.platform.ingest.dto.PayloadFieldMapping;
import com.example.platform.ingest.entity.IngestPayloadMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 类功能:PayloadTranslationEngine 单元测试（ADR-009）
 * 覆盖：点路径/数组下标解析、特征匹配三操作、字段映射转译（缩放/字符串数值/跳过）、
 *   设备编码路径与固定值、采样时间毫秒/秒/缺省口径
 * */
@DisplayName("自定义报文转译引擎测试")
class PayloadTranslationEngineTest {

    private final PayloadTranslationEngine engine = new PayloadTranslationEngine(new ObjectMapper());
    private final ObjectMapper objectMapper = new ObjectMapper();

    private IngestPayloadMapping mapping(String featureJson, String mappingsJson,
                                         String deviceCodePath, String deviceCodeFixed,
                                         String sampleTimePath) {
        IngestPayloadMapping mapping = new IngestPayloadMapping();
        mapping.setName("t");
        mapping.setCloudRoomId(1L);
        mapping.setEnabled(1);
        mapping.setFeatureJson(featureJson);
        mapping.setDeviceCodePath(deviceCodePath);
        mapping.setDeviceCodeFixed(deviceCodeFixed);
        mapping.setSampleTimePath(sampleTimePath);
        mapping.setMappingsJson(mappingsJson);
        return mapping;
    }

    @Test
    @DisplayName("路径解析：点路径、数组下标、多层嵌套与越界")
    void should_resolvePaths() {
        var root = objectMapper.readTree("{\"a\":{\"b\":[10,20]},\"list\":[{\"t\":1}]}");
        assertThat(PayloadTranslationEngine.resolve(root, "a.b[1]").asInt()).isEqualTo(20);
        assertThat(PayloadTranslationEngine.resolve(root, "list[0].t").asInt()).isEqualTo(1);
        assertThat(PayloadTranslationEngine.resolve(root, "a.b[9]")).isNull();
        assertThat(PayloadTranslationEngine.resolve(root, "a.x.y")).isNull();
        assertThat(PayloadTranslationEngine.resolve(root, "")).isNull();
    }

    @Test
    @DisplayName("特征匹配：eq 全等、contains 包含、exists 存在；空规则不匹配")
    void should_matchFeatures() {
        var root = objectMapper.readTree("{\"model\":\"TH-100\",\"fw\":\"1.2\",\"sn\":7}");
        IngestPayloadMapping hit = mapping(
                "[{\"path\":\"model\",\"op\":\"eq\",\"value\":\"TH-100\"},"
                        + "{\"path\":\"fw\",\"op\":\"contains\",\"value\":\".\"},"
                        + "{\"path\":\"sn\",\"op\":\"exists\"}]",
                "[{\"path\":\"a\",\"pointCode\":\"temperature\"}]", "sn", null, null);
        assertThat(engine.matches(hit, root)).isTrue();

        IngestPayloadMapping miss = mapping(
                "[{\"path\":\"model\",\"op\":\"eq\",\"value\":\"OTHER\"}]",
                "[{\"path\":\"a\",\"pointCode\":\"temperature\"}]", "sn", null, null);
        assertThat(engine.matches(miss, root)).isFalse();

        IngestPayloadMapping empty = mapping("[]", "[{\"path\":\"a\",\"pointCode\":\"temperature\"}]",
                "sn", null, null);
        assertThat(engine.matches(empty, root)).isFalse();
    }

    @Test
    @DisplayName("字段转译：数值直取、字符串数值转换、缩放系数、不可解析跳过")
    void should_translateFields() {
        var root = objectMapper.readTree(
                "{\"temp\":\"452\",\"hum\":61.5,\"raw\":123,\"label\":\"abc\",\"flag\":true}");
        IngestPayloadMapping mapping = mapping(
                "[{\"path\":\"temp\",\"op\":\"exists\"}]",
                "[{\"path\":\"temp\",\"pointCode\":\"temperature\",\"scale\":0.1},"
                        + "{\"path\":\"hum\",\"pointCode\":\"humidity\"},"
                        + "{\"path\":\"raw\",\"pointCode\":\"voltage\"},"
                        + "{\"path\":\"label\",\"pointCode\":\"ignored\"},"
                        + "{\"path\":\"flag\",\"pointCode\":\"switch\"},"
                        + "{\"path\":\"missing\",\"pointCode\":\"noop\"}]",
                null, "dev-fixed", null);

        var items = engine.translate(mapping, root, LocalDateTime.now());

        assertThat(items).extracting("pointCode")
                .containsExactly("temperature", "humidity", "voltage", "switch");
        assertThat(items.get(0).getValue()).isEqualByComparingTo("45.2");
        assertThat(items.get(0).getDeviceCode()).isEqualTo("dev-fixed");
        assertThat(items.get(1).getValue()).isEqualByComparingTo("61.5");
        assertThat(items.get(3).getValue()).isEqualTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("设备编码：路径提取优先；路径未取到且无固定值则不产采样")
    void should_resolveDeviceCode() {
        var root = objectMapper.readTree("{\"device\":\"dev-9\",\"temp\":25}");
        IngestPayloadMapping byPath = mapping("[{\"path\":\"temp\",\"op\":\"exists\"}]",
                "[{\"path\":\"temp\",\"pointCode\":\"temperature\"}]", "device", null, null);
        assertThat(engine.translate(byPath, root, LocalDateTime.now())).hasSize(1)
                .first().satisfies(item -> assertThat(item.getDeviceCode()).isEqualTo("dev-9"));

        IngestPayloadMapping neither = mapping("[{\"path\":\"temp\",\"op\":\"exists\"}]",
                "[{\"path\":\"temp\",\"pointCode\":\"temperature\"}]", null, null, null);
        assertThat(engine.translate(neither, root, LocalDateTime.now())).isEmpty();
    }

    @Test
    @DisplayName("采样时间：毫秒时间戳、秒级时间戳（<1e11 按秒）、缺省=接收时刻")
    void should_resolveSampleTime() {
        var root = objectMapper.readTree("{\"ts\":1693468800,\"temp\":25}");
        IngestPayloadMapping seconds = mapping("[{\"path\":\"temp\",\"op\":\"exists\"}]",
                "[{\"path\":\"temp\",\"pointCode\":\"temperature\"}]", null, "dev-1", "ts");
        LocalDateTime parsed = engine.translate(seconds, root, LocalDateTime.now()).get(0).getSampleTime();
        assertThat(parsed.getYear()).isEqualTo(2023);

        IngestPayloadMapping absent = mapping("[{\"path\":\"temp\",\"op\":\"exists\"}]",
                "[{\"path\":\"temp\",\"pointCode\":\"temperature\"}]", null, "dev-1", null);
        LocalDateTime fallback = LocalDateTime.of(2026, 1, 1, 0, 0);
        assertThat(engine.translate(absent, root, fallback).get(0).getSampleTime()).isEqualTo(fallback);
    }

    @Test
    @DisplayName("非法特征/映射 JSON：解析兜底为空列表，不抛异常")
    void should_tolerateBrokenJson() {
        IngestPayloadMapping broken = mapping("not-json", "also-not-json", null, "dev-1", null);
        assertThat(engine.parseFeatures(broken)).isEmpty();
        assertThat(engine.parseMappings(broken)).isEmpty();
        assertThat(engine.matches(broken, objectMapper.readTree("{}"))).isFalse();
    }

    @Test
    @DisplayName("特征值类型：数值节点 asText 等值匹配")
    void should_matchNumericFeatureAsText() {
        var root = objectMapper.readTree("{\"kind\":3}");
        IngestPayloadMapping mapping = mapping("[{\"path\":\"kind\",\"op\":\"eq\",\"value\":\"3\"}]",
                "[{\"path\":\"kind\",\"pointCode\":\"v\"}]", null, "dev-1", null);
        assertThat(engine.matches(mapping, root)).isTrue();
    }
}
