package com.example.platform.ingest.service.impl;

import com.example.platform.common.exception.BusinessException;
import com.example.platform.ingest.dto.PayloadDryRunVO;
import com.example.platform.ingest.dto.PayloadFieldMapping;
import com.example.platform.ingest.dto.PayloadFeatureRule;
import com.example.platform.ingest.dto.PayloadMappingDTO;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.entity.IngestPayloadMapping;
import com.example.platform.ingest.entity.IngestRawMessage;
import com.example.platform.ingest.mapper.IngestCredentialMapper;
import com.example.platform.ingest.mapper.IngestPayloadMappingMapper;
import com.example.platform.ingest.mapper.IngestRawMessageMapper;
import com.example.platform.ingest.template.PayloadTranslationEngine;
import com.example.platform.mapper.PointDefinitionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:IngestPayloadMappingServiceImpl 单元测试（ADR-009）
 * 覆盖：保存校验（设备编码二选一/指标编码登记）、tryTranslate 命中转译与未命中留存原始报文、
 *   dry-run 命中与特征未命中说明
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("自定义报文映射服务测试")
class IngestPayloadMappingServiceImplTest {

    @Mock
    private IngestPayloadMappingMapper mappingMapper;
    @Mock
    private IngestRawMessageMapper rawMessageMapper;
    @Mock
    private IngestCredentialMapper credentialMapper;
    @Mock
    private PointDefinitionMapper pointDefinitionMapper;

    private IngestPayloadMappingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new IngestPayloadMappingServiceImpl(mappingMapper, rawMessageMapper,
                credentialMapper, pointDefinitionMapper,
                new PayloadTranslationEngine(new ObjectMapper()), new ObjectMapper());
    }

    private PayloadMappingDTO draft() {
        PayloadMappingDTO dto = new PayloadMappingDTO();
        dto.setName("某牌温控");
        dto.setCloudRoomId(10L);
        dto.setEnabled(1);
        PayloadFeatureRule feature = new PayloadFeatureRule();
        feature.setPath("model");
        feature.setOp("eq");
        feature.setValue("TC-1");
        dto.setFeatures(List.of(feature));
        PayloadFieldMapping field = new PayloadFieldMapping();
        field.setPath("data.temp");
        field.setPointCode("temperature");
        field.setScale(new BigDecimal("0.1"));
        dto.setMappings(List.of(field));
        dto.setDeviceCodePath("sn");
        return dto;
    }

    private IngestPayloadMapping savedEntity() {
        return draft().toEntity(new ObjectMapper());
    }

    private IngestCredential credential() {
        IngestCredential credential = new IngestCredential();
        credential.setId(5L);
        credential.setCloudRoomId(10L);
        return credential;
    }

    @Test
    @DisplayName("保存校验：设备编码来源未配置（路径与固定值均空）拒绝")
    void should_reject_whenDeviceCodeSourceMissing() {
        PayloadMappingDTO dto = draft();
        dto.setDeviceCodePath(null);
        assertThatThrownBy(() -> service.create(dto, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("设备编码来源");
    }

    @Test
    @DisplayName("保存校验：指标编码未在指标字典登记拒绝")
    void should_reject_whenPointCodeNotRegistered() {
        when(pointDefinitionMapper.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> service.create(draft(), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登记的指标编码");
        verify(mappingMapper, never()).insert(any(IngestPayloadMapping.class));
    }

    @Test
    @DisplayName("tryTranslate：特征命中转译为采样批次且不留存原始报文")
    void should_translateOnFeatureMatch() {
        when(mappingMapper.selectList(any())).thenReturn(List.of(savedEntity()));

        var result = service.tryTranslate(credential(),
                new ObjectMapper().readTree("{\"model\":\"TC-1\",\"sn\":\"dev-9\",\"data\":{\"temp\":\"452\"}}"),
                "{}");

        assertThat(result).isPresent();
        assertThat(result.get().getItems()).hasSize(1);
        assertThat(result.get().getItems().get(0).getPointCode()).isEqualTo("temperature");
        assertThat(result.get().getItems().get(0).getValue()).isEqualByComparingTo("45.2");
        verify(rawMessageMapper, never()).insert(any(IngestRawMessage.class));
    }

    @Test
    @DisplayName("tryTranslate：全部未命中留存原始报文（首存插入）并返回空")
    void should_saveRaw_whenNoTemplateMatches() {
        when(mappingMapper.selectList(any())).thenReturn(List.of(savedEntity()));
        when(rawMessageMapper.selectOne(any())).thenReturn(null);

        String body = "{\"model\":\"OTHER\",\"sn\":\"dev-9\",\"data\":{\"temp\":1}}";
        var result = service.tryTranslate(credential(),
                new ObjectMapper().readTree(body), body);

        assertThat(result).isEmpty();
        ArgumentCaptor<IngestRawMessage> captor = ArgumentCaptor.forClass(IngestRawMessage.class);
        verify(rawMessageMapper).insert(captor.capture());
        assertThat(captor.getValue().getCredentialId()).isEqualTo(5L);
        assertThat(captor.getValue().getContent()).isEqualTo(body);
    }

    @Test
    @DisplayName("dry-run：草稿模板命中时返回设备编码与采样项；未命中给说明不产采样")
    void should_dryRunDraft() {
        var hit = service.dryRun(draft(),
                "{\"model\":\"TC-1\",\"sn\":\"dev-9\",\"data\":{\"temp\":\"452\"}}");
        assertThat(hit.getMatchedMapping()).isNotNull();
        assertThat(hit.getDeviceCode()).isEqualTo("dev-9");
        assertThat(hit.getSamples()).hasSize(1);
        assertThat(hit.getNotes()).isEmpty();

        var miss = service.dryRun(draft(), "{\"model\":\"OTHER\"}");
        assertThat(miss.getMatchedMapping()).isNull();
        assertThat(miss.getSamples()).isEmpty();
        assertThat(miss.getNotes()).anyMatch(note -> note.contains("特征匹配未命中"));
    }

    @Test
    @DisplayName("dry-run：非法 JSON 报文给说明")
    void should_noteOnBrokenJson() {
        var result = service.dryRun(draft(), "not-json");
        assertThat(result.getMatchedMapping()).isNull();
        assertThat(result.getNotes()).anyMatch(note -> note.contains("不是合法 JSON"));
    }
}
