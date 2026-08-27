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
 * 上报幂等去重记录实体类，映射 ingest_dedup 表，短生命周期（定时按窗口清理）
 */
@Schema(description = "上报幂等去重记录")
@Data
@TableName("ingest_dedup")
public class IngestDedup implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "记录ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "去重键 sha256(deviceCode:pointCode:seq)", example = "a3f5...")
    @TableField("dedup_key")
    private String dedupKey;

    @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
