package com.example.platform.ingest.dto;

import com.example.platform.ingest.entity.IngestPayloadMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 自定义报文映射模板 DTO（ADR-009）：特征规则与字段映射以结构化列表收发，
 * 落库时序列化为 feature_json / mappings_json
 */
@Schema(description = "自定义报文映射模板")
@Data
public class PayloadMappingDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "模板ID（更新时必传）", example = "1")
    private Long id;

    @Schema(description = "模板名称", example = "某牌温湿度开关", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称最长100字符")
    private String name;

    @Schema(description = "所属机房ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属机房不能为空")
    private Long cloudRoomId;

    @Schema(description = "是否启用：1=启用 0=停用", example = "1")
    private Integer enabled;

    @Schema(description = "特征匹配规则（≥1 条，全部命中才路由到本模板）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "至少配置一条特征匹配规则")
    @Valid
    private List<PayloadFeatureRule> features;

    @Schema(description = "设备编码提取路径（报文 JSON 内部点路径）；与 deviceCodeFixed 二选一")
    @Size(max = 200, message = "设备编码路径最长200字符")
    private String deviceCodePath;

    @Schema(description = "固定设备编码；与 deviceCodePath 二选一")
    @Size(max = 100, message = "固定设备编码最长100字符")
    private String deviceCodeFixed;

    @Schema(description = "采样时间提取路径（毫秒/秒级时间戳）；空=服务器接收时刻")
    @Size(max = 200, message = "采样时间路径最长200字符")
    private String sampleTimePath;

    @Schema(description = "字段映射（≥1 条）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "至少配置一条字段映射")
    @Valid
    private List<PayloadFieldMapping> mappings;

    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime updateTime;

    /*
     * 方法功能:实体转 DTO（feature_json/mappings_json 反序列化为结构化列表）
     * 参数:entity-模板实体, engine-转译引擎（复用其 JSON 解析口径）
     * 返回数据:PayloadMappingDTO-接口形态
     * */
    public static PayloadMappingDTO fromEntity(IngestPayloadMapping entity,
                                               com.example.platform.ingest.template.PayloadTranslationEngine engine) {
        PayloadMappingDTO dto = new PayloadMappingDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setCloudRoomId(entity.getCloudRoomId());
        dto.setEnabled(entity.getEnabled());
        dto.setFeatures(engine.parseFeatures(entity));
        dto.setDeviceCodePath(entity.getDeviceCodePath());
        dto.setDeviceCodeFixed(entity.getDeviceCodeFixed());
        dto.setSampleTimePath(entity.getSampleTimePath());
        dto.setMappings(engine.parseMappings(entity));
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    /*
     * 方法功能:DTO 转实体（结构化列表序列化为 feature_json/mappings_json）
     * 参数:objectMapper-Jackson 3 序列化器
     * 返回数据:IngestPayloadMapping-实体形态
     * */
    public IngestPayloadMapping toEntity(tools.jackson.databind.ObjectMapper objectMapper) {
        IngestPayloadMapping entity = new IngestPayloadMapping();
        entity.setId(id);
        entity.setName(name);
        entity.setCloudRoomId(cloudRoomId);
        entity.setEnabled(enabled == null ? 1 : enabled);
        entity.setFeatureJson(objectMapper.writeValueAsString(features));
        entity.setDeviceCodePath(emptyToNull(deviceCodePath));
        entity.setDeviceCodeFixed(emptyToNull(deviceCodeFixed));
        entity.setSampleTimePath(emptyToNull(sampleTimePath));
        entity.setMappingsJson(objectMapper.writeValueAsString(mappings));
        return entity;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
