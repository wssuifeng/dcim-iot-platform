package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模拟运行记录实体类，映射 simulation_run_record 表
 *
 * <p>记录每次场景运行的事实和状态（PENDING/RUNNING/SUCCESS/FAILED/CANCELLED）。
 * 所有数据范围校验依赖 organization_id 和 cloud_room_id，不得依赖 Device.location。</p>
 */
@Schema(description = "模拟运行记录")
@Data
@TableName("simulation_run_record")
public class SimulationRunRecord implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "运行ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "关联场景ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "场景ID不能为空")
    @TableField("scene_id")
    private Long sceneId;

    @Schema(description = "运行时场景快照（JSON，便于复现）")
    @TableField("scene_snapshot")
    private String sceneSnapshot;

    @Schema(description = "触发用户ID", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "触发用户ID不能为空")
    @TableField("trigger_user_id")
    private Long triggerUserId;

    @Schema(description = "所属组织ID（数据范围校验依据）", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "组织ID不能为空")
    @TableField("organization_id")
    private Long organizationId;

    @Schema(description = "所属机房ID（可空）", example = "1")
    @TableField("cloud_room_id")
    private Long cloudRoomId;

    @Schema(description = "状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED", example = "PENDING",
            allowableValues = {"PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELLED"})
    private String status;

    @Schema(description = "步骤总数", example = "5")
    @TableField("total_steps")
    private Integer totalSteps;

    @Schema(description = "成功步骤数", example = "5")
    @TableField("success_steps")
    private Integer successSteps;

    @Schema(description = "失败原因（可为空）")
    @TableField("error_message")
    private String errorMessage;

    @Schema(description = "开始执行时间")
    @TableField("start_time")
    private LocalDateTime startTime;

    @Schema(description = "结束执行时间")
    @TableField("end_time")
    private LocalDateTime endTime;

    @Schema(description = "创建时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}