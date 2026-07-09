package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备点位绑定实体类，映射 device_point_binding 表，
 * 描述某台具体设备（devices）实际启用的点位
 */
@Schema(description = "设备点位绑定")
@Data
@TableName("device_point_binding")
public class DevicePointBinding implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "绑定ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "设备ID（关联 devices）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备ID不能为空")
    @TableField("device_id")
    private Integer deviceId;

    @Schema(description = "点位定义ID（关联 point_definition）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "点位定义不能为空")
    @TableField("point_id")
    private Long pointId;

    @Schema(description = "绑定状态：0-启用 1-停用", example = "0")
    private Integer status;

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
