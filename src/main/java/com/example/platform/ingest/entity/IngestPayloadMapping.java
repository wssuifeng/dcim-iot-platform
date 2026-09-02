package com.example.platform.ingest.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 自定义报文映射模板实体类，映射 ingest_payload_mapping 表（零散设备接入，ADR-009）
 * 模板挂机房：凭证→机房→模板池，报文先经特征匹配路由再按 mappings 转译
 */
@Schema(description = "自定义报文映射模板")
@Data
@TableName("ingest_payload_mapping")
public class IngestPayloadMapping implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "模板ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "模板名称", example = "某牌温湿度开关", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称最长100个字符")
    private String name;

    @Schema(description = "所属机房ID（模板对机房内全部开放通道凭证生效）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属机房不能为空")
    @TableField("cloud_room_id")
    private Long cloudRoomId;

    @Schema(description = "是否启用：1=启用 0=停用", example = "1")
    private Integer enabled;

    @Schema(description = "特征匹配规则 JSON 数组（全部命中才路由到本模板）")
    @NotBlank(message = "特征匹配规则不能为空")
    @TableField("feature_json")
    private String featureJson;

    @Schema(description = "设备编码提取路径（报文 JSON 内部点路径）；与 deviceCodeFixed 二选一")
    @TableField("device_code_path")
    private String deviceCodePath;

    @Schema(description = "固定设备编码（deviceCodePath 为空时使用）")
    @TableField("device_code_fixed")
    private String deviceCodeFixed;

    @Schema(description = "采样时间提取路径（毫秒/秒级时间戳）；空=服务器接收时刻")
    @TableField("sample_time_path")
    private String sampleTimePath;

    @Schema(description = "字段映射 JSON 数组：[{path, pointCode, scale}]")
    @NotBlank(message = "字段映射不能为空")
    @TableField("mappings_json")
    private String mappingsJson;

    @Schema(description = "逻辑删除标记", hidden = true)
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
