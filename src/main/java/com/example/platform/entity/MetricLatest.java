package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指标最新值实体类，映射 metric_latest 表，记录设备指标的当前值快照
 */
@Schema(description = "指标最新值快照")
@Data
@TableName("metric_latest")
public class MetricLatest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "设备ID（关联 devices）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备ID不能为空")
    @TableField("device_id")
    private Integer deviceId;

    @Schema(description = "指标定义ID（关联 point_definition）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "指标定义不能为空")
    @TableField("point_id")
    private Long pointId;

    @Schema(description = "最新值", example = "220.50")
    @TableField("latest_value")
    private BigDecimal latestValue;

    @Schema(description = "最新值采集时间")
    @TableField("latest_time")
    private LocalDateTime latestTime;

    @Schema(description = "记录更新时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
