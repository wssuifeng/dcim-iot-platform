package com.example.platform.ingest.http;

import com.example.platform.common.Result;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.TelemetryAuthException;
import com.example.platform.dto.MetricSampleDTO;
import com.example.platform.ingest.dto.IngestResultVO;
import com.example.platform.ingest.dto.IngestSampleBatchDTO;
import com.example.platform.ingest.dto.IngestSampleItemDTO;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.service.IngestCoreService;
import com.example.platform.ingest.service.IngestCredentialService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * 类功能:ThingsBoard 兼容遥测入口（S8-10，边界决策见 ADR-008）
 *
 * 【鉴权】POST /api/v1/{token}/telemetry——token 映射机房凭证（独立于 secret，可重置）；
 *   无 HMAC 签名为既定取舍；无效 token 一律 401（防枚举）。
 * 【报文】TB 三形态 + 显式扩展（ADR-008 D1）：
 *   T1 简单对象 {k:v}（时间=服务器接收时刻）/ T2 {ts:毫秒, values:{k:v}} /
 *   T3 数组批量 / E1 对象可带 device 字段指定设备编码；
 *   无 device 时用凭证 telemetry_default_device，两者皆无 → 422。
 * 【复用】解析后转 IngestSampleBatchDTO 直入传输无关核心（processSamples）：
 *   机房归属校验/首包自动注册/类型推断/幂等（无 seq 降级 sampleTime）全部同口径。
 * */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Tag(name = "TB兼容遥测API", description = "ThingsBoard 生态设备/网关直连入口（token 鉴权，ADR-008）")
public class TelemetryCompatController {

    private final IngestCredentialService ingestCredentialService;
    private final IngestCoreService ingestCoreService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "TB 兼容遥测上报",
            description = "POST /api/v1/{token}/telemetry；报文支持 TB 简单对象/时间戳对象/数组，"
                    + "对象可携带 device 字段（网关场景），无 device 用凭证默认设备")
    @PostMapping("/{token}/telemetry")
    public Result<IngestResultVO> telemetry(
            @PathVariable("token") String token,
            @RequestBody String body) {
        IngestCredential credential;
        try {
            credential = ingestCredentialService.authenticateByTelemetryToken(token);
        } catch (BusinessException ex) {
            // 防 token 枚举：统一 401（ADR-008 D2；TelemetryAuthException 映射 HTTP 401）
            throw new TelemetryAuthException();
        }
        ingestCredentialService.refreshLastSeen(credential.getId());

        IngestSampleBatchDTO batch = parseBatch(body, credential);
        // 空对象/空数组（TB SDK test_connection 连通性探测形态）：不视为错误，
        // 返回 200 空结果——TB 生态客户端以 HTTP 状态码判连接成败
        if (batch.getItems().isEmpty()) {
            return Result.success(new IngestResultVO());
        }
        IngestResultVO result = ingestCoreService.processSamples(credential, batch);
        return Result.success(result);
    }

    // ===================== 报文解析（ADR-008 D1） =====================

    private IngestSampleBatchDTO parseBatch(String body, IngestCredential credential) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "报文不是合法 JSON");
        }
        List<IngestSampleItemDTO> items = new ArrayList<>();
        LocalDateTime receivedAt = LocalDateTime.now();
        if (root.isArray()) {
            for (JsonNode node : root) {
                items.addAll(parseEntry(node, credential, receivedAt));
            }
        } else if (root.isObject()) {
            items.addAll(parseEntry(root, credential, receivedAt));
        } else {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "报文须为 JSON 对象或数组");
        }
        if (items.isEmpty()) {
            // 空对象/空数组：TB SDK test_connection 连通性探测形态，交由调用方决定是否放行
            IngestSampleBatchDTO empty = new IngestSampleBatchDTO();
            empty.setItems(new ArrayList<>());
            return empty;
        }
        IngestSampleBatchDTO batch = new IngestSampleBatchDTO();
        batch.setItems(items);
        return batch;
    }

    // 单个条目（对象）→ 若干采样项；E1 扩展：device 字段优先，缺省凭证默认设备
    private List<IngestSampleItemDTO> parseEntry(JsonNode node, IngestCredential credential,
                                                 LocalDateTime receivedAt) {
        if (!node.isObject()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "报文条目须为 JSON 对象");
        }
        String deviceCode;
        if (node.hasNonNull("device") && !node.get("device").asText().isBlank()) {
            deviceCode = node.get("device").asText().trim();
        } else if (credential.getTelemetryDefaultDevice() != null
                && !credential.getTelemetryDefaultDevice().isBlank()) {
            deviceCode = credential.getTelemetryDefaultDevice();
        } else {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "报文未携带 device 且凭证未绑定默认设备（ADR-008 D1）");
        }
        long tsMillis;
        JsonNode values;
        if (node.has("values") && node.get("values").isObject()) {
            tsMillis = node.hasNonNull("ts") ? parseTsMillis(node.get("ts")) : receivedAtMillis(receivedAt);
            values = node.get("values");
        } else {
            // T1 简单对象：全部键值即 values（排除 device/ts 解析键）
            tsMillis = node.hasNonNull("ts") ? parseTsMillis(node.get("ts")) : receivedAtMillis(receivedAt);
            values = node;
        }
        LocalDateTime sampleTime = toLocalDateTime(tsMillis);
        List<IngestSampleItemDTO> items = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> fields = values.properties().iterator(); fields.hasNext();) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            if ("device".equals(key) || "ts".equals(key)) {
                continue;
            }
            BigDecimal value = toNumericValue(field.getValue());
            if (value == null) {
                continue;
            }
            IngestSampleItemDTO item = new IngestSampleItemDTO();
            item.setDeviceCode(deviceCode);
            item.setPointCode(key);
            item.setValue(value);
            item.setSampleTime(sampleTime);
            items.add(item);
        }
        return items;
    }

    // ts 兼容毫秒数字与秒级字符串（10 位按秒解释）
    private long parseTsMillis(JsonNode tsNode) {
        if (tsNode.isNumber()) {
            long value = tsNode.asLong();
            return value < 100_000_000_000L ? value * 1000L : value;
        }
        try {
            long value = Long.parseLong(tsNode.asText().trim());
            return value < 100_000_000_000L ? value * 1000L : value;
        } catch (NumberFormatException ex) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "ts 不是合法的时间戳");
        }
    }

    private long receivedAtMillis(LocalDateTime receivedAt) {
        return receivedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private LocalDateTime toLocalDateTime(long tsMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(tsMillis), ZoneId.systemDefault());
    }

    // 数值提取：数值直取；字符串可解析数值直取（BOOLEAN 语义 0/1 同 ingest 校验口径）；其余丢弃
    private BigDecimal toNumericValue(JsonNode valueNode) {
        if (valueNode.isNumber()) {
            return valueNode.decimalValue();
        }
        if (valueNode.isBoolean()) {
            return valueNode.asBoolean() ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (valueNode.isTextual()) {
            try {
                return new BigDecimal(valueNode.asText().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}
