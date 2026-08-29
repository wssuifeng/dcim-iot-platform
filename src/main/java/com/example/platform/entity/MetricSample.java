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
 * 指标采样实体类，映射 metric_sample 表，记录设备指标的历史采样序列
 * 注：此表无逻辑删除字段（海量历史数据，按保留策略物理清理，见 metric_retention_policy）
 */
@Schema(description = "指标采样历史")
@Data
@TableName("metric_sample")
public class MetricSample implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "采样ID（主键）", example = "1001")
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

    @Schema(description = "采样值", example = "220.50")
    @TableField("sample_value")
    private BigDecimal sampleValue;

    @Schema(description = "采样时间（设备侧时钟基准，自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "sample_time", fill = FieldFill.INSERT)
    private LocalDateTime sampleTime;

    @Schema(description = "平台接收存储时间（服务端时钟基准，数据库默认值生成）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField("receive_time")
    private LocalDateTime receiveTime;
}
