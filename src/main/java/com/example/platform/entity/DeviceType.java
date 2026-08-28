package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备类型实体类，映射 device_types 表，定义设备分类信息
 */
@Schema(description = "设备类型")
@Data
@TableName("device_types")
public class DeviceType implements Serializable {

    @Schema(description = "类型ID（主键，自增）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "类型名称", example = "温度传感器", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField("type_name")
    private String typeName;

    @Schema(description = "类型编码（全局唯一，仅允许字母数字下划线）", example = "TEMP_SENSOR", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField("type_code")
    private String typeCode;

    @Schema(description = "类型描述", example = "各类温度采集设备")
    private String description;

    @Schema(description = "该类型的基础告警模版ID", example = "1")
    @TableField("alarm_template_id")
    private Long alarmTemplateId;

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
