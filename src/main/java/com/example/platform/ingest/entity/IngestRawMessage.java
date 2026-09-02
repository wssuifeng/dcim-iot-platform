package com.example.platform.ingest.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 开放通道未匹配原始报文留存实体类，映射 ingest_raw_message 表（ADR-009）
 * 每凭证仅最新一条（唯一索引覆盖，新报文 UPSERT），供映射配置 UI 作为参照物
 */
@Schema(description = "开放通道原始报文留存（每凭证一条）")
@Data
@TableName("ingest_raw_message")
public class IngestRawMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "留存ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "凭证ID（唯一键，每凭证仅最新一条）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField("credential_id")
    private Long credentialId;

    @Schema(description = "原始报文内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(description = "收到时间", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
