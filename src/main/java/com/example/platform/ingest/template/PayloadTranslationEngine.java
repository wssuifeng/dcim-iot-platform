package com.example.platform.ingest.template;

import com.example.platform.ingest.dto.PayloadFeatureRule;
import com.example.platform.ingest.dto.PayloadFieldMapping;
import com.example.platform.ingest.dto.IngestSampleItemDTO;
import com.example.platform.ingest.entity.IngestPayloadMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/*
 * 类功能:自定义报文转译引擎（ADR-009）——特征匹配路由 + 字段路径提取转译
 *
 * 【路径语法】报文 JSON 内部点路径 + 数组下标：data.temp、items[0].humidity、a.b[1][0].c；
 *   刻意不支持脚本/正则/函数（安全边界，ADR-009 D4）。
 * 【值口径】数值直取；布尔 0/1；可解析数字的字符串自动转（"45.2"→45.2）；
 *   每映射可配缩放系数 scale（raw 452 × 0.1 → 45.2），不可解析字段跳过不报错。
 * 【时间口径】sampleTimePath 解析毫秒/秒级时间戳（<1e11 按秒），缺省=服务器接收时刻。
 * */
@Component
@RequiredArgsConstructor
public class PayloadTranslationEngine {

    private final ObjectMapper objectMapper;

    /*
     * 方法功能:特征匹配——模板全部特征规则命中返回 true（空规则列表视为不匹配，保存时已禁止）
     * 参数:mapping-映射模板, root-报文 JSON 根节点
     * 返回数据:boolean-是否命中
     * */
    public boolean matches(IngestPayloadMapping mapping, JsonNode root) {
        List<PayloadFeatureRule> rules = parseFeatures(mapping);
        // 空规则视为不匹配（保存时已强制至少一条，此处兜底防 catch-all 误路由）
        if (rules.isEmpty()) {
            return false;
        }
        for (PayloadFeatureRule rule : rules) {
            JsonNode node = resolve(root, rule.getPath());
            if (!ruleMatches(rule, node)) {
                return false;
            }
        }
        return true;
    }

    /*
     * 方法功能:按模板转译报文——设备编码 + 采样时间 + 各字段映射展开为采样项
     * 参数:mapping-映射模板, root-报文 JSON 根节点, receivedAt-服务器接收时刻（时间缺省口径）
     * 返回数据:List<IngestSampleItemDTO>-采样项（不可解析字段跳过；可能为空列表）
     * */
    public List<IngestSampleItemDTO> translate(IngestPayloadMapping mapping, JsonNode root,
                                               LocalDateTime receivedAt) {
        String deviceCode = mapping.getDeviceCodePath() != null && !mapping.getDeviceCodePath().isBlank()
                ? textOf(resolve(root, mapping.getDeviceCodePath()))
                : mapping.getDeviceCodeFixed();
        LocalDateTime sampleTime = mapping.getSampleTimePath() != null && !mapping.getSampleTimePath().isBlank()
                ? timeOf(resolve(root, mapping.getSampleTimePath()), receivedAt)
                : receivedAt;

        List<IngestSampleItemDTO> items = new ArrayList<>();
        if (deviceCode == null || deviceCode.isBlank()) {
            return items;
        }
        for (PayloadFieldMapping field : parseMappings(mapping)) {
            BigDecimal value = numericOf(resolve(root, field.getPath()));
            if (value == null) {
                continue;
            }
            if (field.getScale() != null) {
                value = value.multiply(field.getScale()).setScale(6, RoundingMode.HALF_UP)
                        .stripTrailingZeros();
            }
            IngestSampleItemDTO item = new IngestSampleItemDTO();
            item.setDeviceCode(deviceCode);
            item.setPointCode(field.getPointCode());
            item.setValue(value);
            item.setSampleTime(sampleTime);
            items.add(item);
        }
        return items;
    }

    /*
     * 方法功能:解析模板特征规则 JSON（非法 JSON 返回空列表，保存时已校验）
     * 参数:mapping-映射模板
     * 返回数据:List<PayloadFeatureRule>-特征规则
     * */
    public List<PayloadFeatureRule> parseFeatures(IngestPayloadMapping mapping) {
        try {
            return objectMapper.readValue(mapping.getFeatureJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, PayloadFeatureRule.class));
        } catch (Exception ex) {
            return List.of();
        }
    }

    /*
     * 方法功能:解析模板字段映射 JSON（非法 JSON 返回空列表，保存时已校验）
     * 参数:mapping-映射模板
     * 返回数据:List<PayloadFieldMapping>-字段映射
     * */
    public List<PayloadFieldMapping> parseMappings(IngestPayloadMapping mapping) {
        try {
            return objectMapper.readValue(mapping.getMappingsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, PayloadFieldMapping.class));
        } catch (Exception ex) {
            return List.of();
        }
    }

    // 单条特征规则判定：eq 文本等值 / contains 文本包含 / exists 节点存在且非 null
    private boolean ruleMatches(PayloadFeatureRule rule, JsonNode node) {
        String op = rule.getOp() == null ? "eq" : rule.getOp();
        return switch (op) {
            case "exists" -> node != null && !node.isMissingNode() && !node.isNull();
            case "contains" -> node != null && !node.isMissingNode() && node.asText().contains(
                    rule.getValue() == null ? "" : rule.getValue());
            default -> node != null && !node.isMissingNode()
                    && node.asText().equals(rule.getValue() == null ? "" : rule.getValue());
        };
    }

    /*
     * 方法功能:报文内部路径解析——点号分段，段内支持 [n] 数组下标（可连续多级）
     * 参数:root-JSON 根节点, path-点路径（如 items[0].temp）
     * 返回数据:JsonNode-命中节点；任何一层无法命中返回 null
     * */
    public static JsonNode resolve(JsonNode root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return null;
        }
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            int bracket = segment.indexOf('[');
            String name = bracket >= 0 ? segment.substring(0, bracket) : segment;
            if (!name.isEmpty()) {
                if (current == null || !current.isObject()) {
                    return null;
                }
                current = current.get(name);
            }
            while (bracket >= 0) {
                int close = segment.indexOf(']', bracket);
                if (close < 0 || current == null || !current.isArray()) {
                    return null;
                }
                int index;
                try {
                    index = Integer.parseInt(segment.substring(bracket + 1, close));
                } catch (NumberFormatException ex) {
                    return null;
                }
                current = index >= 0 && index < current.size() ? current.get(index) : null;
                bracket = segment.indexOf('[', close);
            }
        }
        return current;
    }

    // 节点转数值：数值直取 / 布尔 0-1 / 可解析数字字符串转换；其余 null
    private BigDecimal numericOf(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean() ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    // 节点转文本（设备编码提取），null 安全
    private String textOf(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    // 节点转时间：毫秒/秒级时间戳（<1e11 按秒解释）；不可解析回退接收时刻
    private LocalDateTime timeOf(JsonNode node, LocalDateTime fallback) {
        if (node == null || !node.isNumber()) {
            return fallback;
        }
        long value = node.asLong();
        long millis = value < 100_000_000_000L ? value * 1000L : value;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
