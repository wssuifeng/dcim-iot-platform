package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 指标保留策略实体类，映射 metric_retention_policy 表；
 * pointId 为空表示全局默认保留策略
 */
@Schema(description = "指标数据保留策略")
@Data
@TableName("metric_retention_policy")
public class MetricRetentionPolicy implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "点位定义ID（为空表示全局默认策略）", example = "1")
    @TableField("point_id")
    private Long pointId;

    @Schema(description = "保留天数", example = "90")
    @TableField("retention_days")
    private Integer retentionDays;

    @Schema(description = "创建时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
