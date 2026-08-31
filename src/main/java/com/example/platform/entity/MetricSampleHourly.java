package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指标小时聚合实体类，映射 metric_sample_hourly 表（S8-4 趋势降采样数据源）
 * 每设备每指标每小时一行：min/max/avg/cnt；唯一键 device_id+point_id+bucket_start 幂等 upsert
 */
@Schema(description = "指标小时聚合")
@Data
@TableName("metric_sample_hourly")
public class MetricSampleHourly implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "聚合行ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "设备ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField("device_id")
    private Integer deviceId;

    @Schema(description = "指标ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField("point_id")
    private Long pointId;

    @Schema(description = "整点桶起始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField("bucket_start")
    private LocalDateTime bucketStart;

    @Schema(description = "桶内最小值")
    @TableField("min_value")
    private BigDecimal minValue;

    @Schema(description = "桶内最大值")
    @TableField("max_value")
    private BigDecimal maxValue;

    @Schema(description = "桶内均值")
    @TableField("avg_value")
    private BigDecimal avgValue;

    @Schema(description = "桶内采样条数")
    @TableField("cnt")
    private Long cnt;
}
