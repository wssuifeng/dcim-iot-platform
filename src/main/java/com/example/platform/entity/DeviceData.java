package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.platform.common.group.CreateGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备数据实体类，映射 device_data 表，记录设备上报的采集数据及指标值
 */
@Schema(description = "设备数据")
@Data
@TableName("device_data")
public class DeviceData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "数据ID（主键）", example = "1001")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "设备ID", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField("device_id")
    @NotNull(message = "设备ID不能为空", groups = CreateGroup.class)
    private Integer deviceId;

    @Schema(description = "指标标识（如 temperature）", example = "temperature")
    @TableField("metric")
    private String metric;

    @Schema(description = "数据值（保留两位小数，范围 -999.99 ~ 999.99）", example = "42.50", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "数据值不能为空", groups = CreateGroup.class)
    @DecimalMin(value = "-999.99", message = "数据值不能小于-999.99", groups = CreateGroup.class)
    @DecimalMax(value = "999.99", message = "数据值不能超过999.99", groups = CreateGroup.class)
    @TableField("data_value")
    private BigDecimal dataValue;

    @Schema(description = "采集时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "collect_time", fill = FieldFill.INSERT)
    private LocalDateTime collectTime;

    @Schema(hidden = true)
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
