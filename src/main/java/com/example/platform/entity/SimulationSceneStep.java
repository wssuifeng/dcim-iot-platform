package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟场景步骤实体类，映射 simulation_scene_step 表
 *
 * <p>每个步骤对应一次采样写入或延时动作；步骤顺序 step_order 从 1 开始递增。</p>
 */
@Schema(description = "模拟场景步骤")
@Data
@TableName("simulation_scene_step")
public class SimulationSceneStep implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "步骤ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属场景ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "场景ID不能为空")
    @TableField("scene_id")
    private Long sceneId;

    @Schema(description = "步骤顺序（从1开始递增）", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "步骤顺序不能为空")
    @TableField("step_order")
    private Integer stepOrder;

    @Schema(description = "目标点位ID（关联 point_definition）", example = "10",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "点位ID不能为空")
    @TableField("point_id")
    private Long pointId;

    @Schema(description = "目标设备ID（关联 devices，为空表示运行时由调用方指定）", example = "5")
    @TableField("target_device_id")
    private Integer targetDeviceId;

    @Schema(description = "步骤注入的采样值", example = "220.50")
    @TableField("sample_value")
    private BigDecimal sampleValue;

    @Schema(description = "步骤动作：WRITE_SAMPLE/SLEEP", example = "WRITE_SAMPLE",
            allowableValues = {"WRITE_SAMPLE", "SLEEP"})
    private String action;

    @Schema(description = "执行前延迟毫秒", example = "0")
    @TableField("delay_ms")
    private Integer delayMs;

    @Schema(description = "步骤描述")
    @Size(max = 200, message = "步骤描述最长200个字符")
    private String description;

    @Schema(description = "创建时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Schema(hidden = true)
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}