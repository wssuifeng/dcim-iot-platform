package com.example.platform.ingest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.ingest.dto.IngestSampleBatchDTO;
import com.example.platform.ingest.dto.PayloadDryRunVO;
import com.example.platform.ingest.dto.PayloadMappingDTO;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.entity.IngestPayloadMapping;
import com.example.platform.ingest.entity.IngestRawMessage;
import com.example.platform.ingest.mapper.IngestCredentialMapper;
import com.example.platform.ingest.mapper.IngestPayloadMappingMapper;
import com.example.platform.ingest.mapper.IngestRawMessageMapper;
import com.example.platform.ingest.service.IngestPayloadMappingService;
import com.example.platform.ingest.template.PayloadTranslationEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
 * 类功能:自定义报文映射模板服务实现（零散设备接入，ADR-009）
 *
 * 【路由口径】凭证→机房→模板池（enabled=1 按 id 升序），报文依次过特征匹配，
 *   第一个全命中且转译出 ≥1 条采样的模板生效；全不命中留存原始报文（每凭证最新一条）。
 * 【dry-run】草稿模板（可不落库）对原始报文做匹配+转译，纯预览不落采样。
 * 【校验口径】保存时校验设备编码来源（路径/固定值二选一）与指标编码存在于指标字典，
 *   防止拼错字段静默产出垃圾数据。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestPayloadMappingServiceImpl implements IngestPayloadMappingService {

    // 转译采样条目上限（与 ingest 批量口径一致）
    private static final int MAX_TRANSLATED_ITEMS = 200;

    private final IngestPayloadMappingMapper mappingMapper;
    private final IngestRawMessageMapper rawMessageMapper;
    private final IngestCredentialMapper credentialMapper;
    private final com.example.platform.mapper.PointDefinitionMapper pointDefinitionMapper;
    private final PayloadTranslationEngine engine;
    private final ObjectMapper objectMapper;

    @Override
    public List<PayloadMappingDTO> listByRoom(Long roomId) {
        return mappingMapper.selectList(new LambdaQueryWrapper<IngestPayloadMapping>()
                        .eq(IngestPayloadMapping::getCloudRoomId, roomId)
                        .orderByAsc(IngestPayloadMapping::getId))
                .stream()
                .map(entity -> PayloadMappingDTO.fromEntity(entity, engine))
                .toList();
    }

    @Override
    public PayloadMappingDTO create(PayloadMappingDTO dto, Long operatorId) {
        validate(dto);
        IngestPayloadMapping entity = dto.toEntity(objectMapper);
        entity.setId(null);
        mappingMapper.insert(entity);
        log.info("自定义报文映射模板创建 id={} name={} roomId={} operator={}",
                entity.getId(), entity.getName(), entity.getCloudRoomId(), operatorId);
        return PayloadMappingDTO.fromEntity(entity, engine);
    }

    @Override
    public PayloadMappingDTO update(Long id, PayloadMappingDTO dto, Long operatorId) {
        IngestPayloadMapping existing = mappingMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "映射模板不存在");
        }
        validate(dto);
        IngestPayloadMapping entity = dto.toEntity(objectMapper);
        entity.setId(id);
        mappingMapper.updateById(entity);
        log.info("自定义报文映射模板更新 id={} name={} operator={}", id, entity.getName(), operatorId);
        return PayloadMappingDTO.fromEntity(mappingMapper.selectById(id), engine);
    }

    @Override
    public void delete(Long id, Long operatorId) {
        mappingMapper.deleteById(id);
        log.info("自定义报文映射模板删除 id={} operator={}", id, operatorId);
    }

    @Override
    public PayloadDryRunVO dryRun(PayloadMappingDTO draft, String rawMessage) {
        PayloadDryRunVO result = new PayloadDryRunVO();
        if (rawMessage == null || rawMessage.isBlank()) {
            result.getNotes().add("原始报文为空");
            return result;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawMessage);
        } catch (Exception ex) {
            result.getNotes().add("报文不是合法 JSON: " + ex.getMessage());
            return result;
        }
        if (draft == null) {
            result.getNotes().add("未提供模板草稿");
            return result;
        }
        IngestPayloadMapping draftEntity = draft.toEntity(objectMapper);
        if (!engine.matches(draftEntity, root)) {
            result.getNotes().add("特征匹配未命中：报文不满足模板特征规则，不会路由到该模板");
            return result;
        }
        result.setMatchedMapping(draft);
        translateInto(draftEntity, root, result);
        return result;
    }

    @Override
    public Optional<IngestSampleBatchDTO> tryTranslate(IngestCredential credential,
                                                       JsonNode root, String rawBody) {
        List<IngestPayloadMapping> mappings = mappingMapper.selectList(
                new LambdaQueryWrapper<IngestPayloadMapping>()
                        .eq(IngestPayloadMapping::getCloudRoomId, credential.getCloudRoomId())
                        .eq(IngestPayloadMapping::getEnabled, 1)
                        .orderByAsc(IngestPayloadMapping::getId));
        if (root != null) {
            LocalDateTime receivedAt = LocalDateTime.now();
            for (IngestPayloadMapping mapping : mappings) {
                if (!engine.matches(mapping, root)) {
                    continue;
                }
                List<com.example.platform.ingest.dto.IngestSampleItemDTO> items =
                        engine.translate(mapping, root, receivedAt);
                if (items.isEmpty()) {
                    // 特征命中但无有效字段：视为未命中继续尝试其余模板
                    continue;
                }
                if (items.size() > MAX_TRANSLATED_ITEMS) {
                    items = items.subList(0, MAX_TRANSLATED_ITEMS);
                }
                IngestSampleBatchDTO batch = new IngestSampleBatchDTO();
                batch.setItems(new ArrayList<>(items));
                return Optional.of(batch);
            }
        }
        saveLatestRaw(credential.getId(), rawBody);
        return Optional.empty();
    }

    @Override
    public IngestRawMessage latestRawMessage(Long roomId) {
        List<IngestCredential> credentials = credentialMapper.selectList(
                new LambdaQueryWrapper<IngestCredential>()
                        .eq(IngestCredential::getCloudRoomId, roomId));
        IngestRawMessage latest = null;
        for (IngestCredential credential : credentials) {
            IngestRawMessage raw = rawMessageMapper.selectOne(
                    new LambdaQueryWrapper<IngestRawMessage>()
                            .eq(IngestRawMessage::getCredentialId, credential.getId()));
            if (raw != null && (latest == null
                    || raw.getCreateTime().isAfter(latest.getCreateTime()))) {
                latest = raw;
            }
        }
        return latest;
    }

    // 未命中留存原始报文：每凭证仅最新一条（唯一键 UPSERT 语义），供映射配置页参照
    private void saveLatestRaw(Long credentialId, String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return;
        }
        try {
            IngestRawMessage existing = rawMessageMapper.selectOne(
                    new LambdaQueryWrapper<IngestRawMessage>()
                            .eq(IngestRawMessage::getCredentialId, credentialId));
            if (existing == null) {
                IngestRawMessage raw = new IngestRawMessage();
                raw.setCredentialId(credentialId);
                raw.setContent(rawBody);
                rawMessageMapper.insert(raw);
            } else {
                rawMessageMapper.update(null, new LambdaUpdateWrapper<IngestRawMessage>()
                        .eq(IngestRawMessage::getId, existing.getId())
                        .set(IngestRawMessage::getContent, rawBody)
                        .set(IngestRawMessage::getCreateTime, LocalDateTime.now()));
            }
        } catch (Exception ex) {
            // 留存失败不影响主流程（仅损失配置参照物）
            log.warn("原始报文留存失败 credentialId={} error={}", credentialId, ex.getMessage());
        }
    }

    // dry-run 转译填充：设备编码 + 采样项 + 说明
    private void translateInto(IngestPayloadMapping draftEntity, JsonNode root, PayloadDryRunVO result) {
        String deviceCode = draftEntity.getDeviceCodePath() != null && !draftEntity.getDeviceCodePath().isBlank()
                ? textOf(PayloadTranslationEngine.resolve(root, draftEntity.getDeviceCodePath()))
                : draftEntity.getDeviceCodeFixed();
        result.setDeviceCode(deviceCode);
        if (deviceCode == null || deviceCode.isBlank()) {
            result.getNotes().add("设备编码未取到：检查设备编码路径或固定值配置");
            return;
        }
        List<com.example.platform.ingest.dto.IngestSampleItemDTO> items =
                engine.translate(draftEntity, root, LocalDateTime.now());
        result.setSamples(items);
        if (items.isEmpty()) {
            result.getNotes().add("未转译出有效采样：检查字段路径是否指向数值/可解析数字的字符串");
        }
    }

    private String textOf(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    // 保存校验：设备编码来源二选一必填 + 指标编码必须存在于指标字典
    private void validate(PayloadMappingDTO dto) {
        boolean hasPath = dto.getDeviceCodePath() != null && !dto.getDeviceCodePath().isBlank();
        boolean hasFixed = dto.getDeviceCodeFixed() != null && !dto.getDeviceCodeFixed().isBlank();
        if (hasPath == hasFixed) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "设备编码来源必须二选一：报文路径或固定设备编码");
        }
        List<String> pointCodes = dto.getMappings().stream()
                .map(com.example.platform.ingest.dto.PayloadFieldMapping::getPointCode)
                .distinct()
                .toList();
        Long exists = pointDefinitionMapper.selectCount(
                new LambdaQueryWrapper<com.example.platform.entity.PointDefinition>()
                        .in(com.example.platform.entity.PointDefinition::getPointCode, pointCodes));
        if (exists == null || exists < pointCodes.size()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "存在未登记的指标编码，请先在指标字典登记: " + pointCodes);
        }
    }
}
