package com.example.platform.ingest.service;

import com.example.platform.ingest.dto.IngestSampleBatchDTO;
import com.example.platform.ingest.dto.PayloadDryRunVO;
import com.example.platform.ingest.dto.PayloadMappingDTO;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.entity.IngestRawMessage;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

/*
 * 类功能:自定义报文映射模板服务（零散设备接入，ADR-009）
 *   模板 CRUD + dry-run 预览 + 开放通道挂载（特征匹配转译 + 原始报文留存）
 * */
public interface IngestPayloadMappingService {

    /*
     * 方法功能:按机房查询映射模板列表
     * 参数:roomId-机房ID
     * 返回数据:List<PayloadMappingDTO>-模板列表（结构化 features/mappings）
     * */
    List<PayloadMappingDTO> listByRoom(Long roomId);

    /*
     * 方法功能:新建映射模板（校验特征/映射/设备编码/指标编码存在性）
     * 参数:dto-模板草稿, operatorId-操作人
     * 返回数据:PayloadMappingDTO-落库后的模板
     * */
    PayloadMappingDTO create(PayloadMappingDTO dto, Long operatorId);

    /*
     * 方法功能:更新映射模板
     * 参数:id-模板ID, dto-模板草稿, operatorId-操作人
     * 返回数据:PayloadMappingDTO-更新后的模板
     * */
    PayloadMappingDTO update(Long id, PayloadMappingDTO dto, Long operatorId);

    /*
     * 方法功能:删除映射模板（逻辑删除）
     * 参数:id-模板ID, operatorId-操作人
     * 返回数据:无
     * */
    void delete(Long id, Long operatorId);

    /*
     * 方法功能:dry-run 预览——用草稿模板（可不落库）对原始报文做匹配+转译，展示结果不落采样
     * 参数:draft-模板草稿, rawMessage-原始报文 JSON 文本
     * 返回数据:PayloadDryRunVO-命中模板/设备编码/采样项/说明
     * */
    PayloadDryRunVO dryRun(PayloadMappingDTO draft, String rawMessage);

    /*
     * 方法功能:开放通道挂载——报文依次过机房模板池特征匹配，命中即转译为采样批次；
     *   未命中（或报文非法 JSON）时留存原始报文（每凭证仅最新一条）并返回空
     * 参数:credential-接入凭证, root-报文 JSON 根节点（非法 JSON 时为 null）, rawBody-原始报文文本
     * 返回数据:Optional<IngestSampleBatchDTO>-命中转译结果；空=未命中
     * */
    Optional<IngestSampleBatchDTO> tryTranslate(IngestCredential credential, JsonNode root, String rawBody);

    /*
     * 方法功能:查询机房内凭证留存的最新原始报文（映射配置页参照物）
     * 参数:roomId-机房ID
     * 返回数据:IngestRawMessage-最新原始报文；无则 null
     * */
    IngestRawMessage latestRawMessage(Long roomId);
}
