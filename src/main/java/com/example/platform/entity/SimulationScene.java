package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模拟场景实体类，映射 simulation_scene 表
 *
 * <p>契约依据：共享契约 §6 模拟端契约。场景由后端服务管理，前端不能直接构造核心事实。</p>
 * <p>范围字段：organization_id（必填）、cloud_room_id（可空，表示跨机房场景）。</p>
 */
@Schema(description = "模拟场景")
@Data
@TableName("simulation_scene")
public class SimulationScene implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "场景ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "场景编码（同组织内唯一）", example = "UPS_LOAD_TEST",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "场景编码不能为空")
    @Size(max = 50, message = "场景编码最长50个字符")
    @TableField("scene_code")
    private String sceneCode;

    @Schema(description = "场景名称", example = "UPS 负载模拟测试",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "场景名称不能为空")
    @Size(max = 100, message = "场景名称最长100个字符")
    @TableField("scene_name")
    private String sceneName;

    @Schema(description = "场景描述")
    @Size(max = 500, message = "描述最长500个字符")
    private String description;

    @Schema(description = "所属组织ID（数据范围校验依据）", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "组织ID不能为空")
    @TableField("organization_id")
    private Long organizationId;

    @Schema(description = "所属机房ID（可空，表示跨机房场景）", example = "1")
    @TableField("cloud_room_id")
    private Long cloudRoomId;

    @Schema(description = "目标设备类型编码（参考五类设备契约）", example = "UPS")
    @Size(max = 50, message = "设备类型编码最长50个字符")
    @TableField("device_type_code")
    private String deviceTypeCode;

    @Schema(description = "状态：0-启用 1-停用", example = "0",
            allowableValues = {"0", "1"})
    private Integer status;

    @Schema(description = "创建用户ID", example = "10")
    @TableField("created_by")
    private Long createdBy;

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