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
 * 模拟生成数据引用实体类，映射 simulation_generated_data_ref 表
 *
 * <p>无逻辑删除：必须保留可追溯事实。按 metric_retention_policy 物理清理。</p>
 * <p>每条记录对应一次 MetricSample 写入，metric_sample_id 是生成数据可追溯的核心依据。</p>
 */
@Schema(description = "模拟生成数据引用")
@Data
@TableName("simulation_generated_data_ref")
public class SimulationGeneratedDataRef implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "引用ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "关联运行ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "运行ID不能为空")
    @TableField("run_id")
    private Long runId;

    @Schema(description = "关联步骤ID（可空，用于运行级聚合场景）", example = "10")
    @TableField("step_id")
    private Long stepId;

    @Schema(description = "生成数据目标设备ID", example = "5",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备ID不能为空")
    @TableField("device_id")
    private Integer deviceId;

    @Schema(description = "生成数据目标点位ID", example = "10",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "点位ID不能为空")
    @TableField("point_id")
    private Long pointId;

    @Schema(description = "写入 metric_sample 的主键（可追溯依据）", example = "1001")
    @TableField("metric_sample_id")
    private Long metricSampleId;

    @Schema(description = "动作：WRITE_SAMPLE/SLEEP", example = "WRITE_SAMPLE")
    private String action;

    @Schema(description = "注入的采样值", example = "220.50")
    @TableField("sample_value")
    private BigDecimal sampleValue;

    @Schema(description = "生成时间", example = "2026-07-10T12:00:00",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "生成时间不能为空")
    @TableField("generated_at")
    private LocalDateTime generatedAt;

    @Schema(description = "创建时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}