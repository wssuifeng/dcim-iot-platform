package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 指标定义实体类，映射 point_definition 表，描述设备可采集的物理量指标
 */
@Schema(description = "指标定义")
@Data
@TableName("point_definition")
public class PointDefinition implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "指标定义ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "指标编码（全局唯一）", example = "voltage_a", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "指标编码不能为空")
    @Size(max = 50, message = "指标编码最长50个字符")
    @TableField("point_code")
    private String pointCode;

    @Schema(description = "指标名称（展示用）", example = "A相电压", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "指标名称不能为空")
    @Size(max = 100, message = "指标名称最长100个字符")
    @TableField("point_name")
    private String pointName;

    @Schema(description = "单位", example = "V")
    @Size(max = 20, message = "单位最长20个字符")
    private String unit;

    @Schema(description = "数据类型：NUMBER/BOOLEAN/STRING", example = "NUMBER",
            allowableValues = {"NUMBER", "BOOLEAN", "STRING"})
    @TableField("data_type")
    private String dataType;

    @Schema(description = "指标描述", example = "UPS输出A相电压")
    @Size(max = 200, message = "描述最长200个字符")
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
