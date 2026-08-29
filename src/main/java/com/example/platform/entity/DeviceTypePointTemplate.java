package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备类型指标模板实体类，映射 device_type_point_template 表，
 * 描述某类设备类型（device_types）默认应包含的指标定义集合
 */
@Schema(description = "设备类型指标模板")
@Data
@TableName("device_type_point_template")
public class DeviceTypePointTemplate implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "模板ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "设备类型ID（关联 device_types）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备类型不能为空")
    @TableField("type_id")
    private Integer typeId;

    @Schema(description = "指标定义ID（关联 point_definition）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "指标定义不能为空")
    @TableField("point_id")
    private Long pointId;

    @Schema(description = "是否必选指标：0-可选 1-必选", example = "1")
    @TableField("is_required")
    private Integer isRequired;

    @Schema(description = "展示排序", example = "0")
    @TableField("sort_order")
    private Integer sortOrder;

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
