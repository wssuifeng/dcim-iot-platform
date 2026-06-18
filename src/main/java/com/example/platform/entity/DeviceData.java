package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.platform.common.group.CreateGroup;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/*
 * 类功能:设备数据实体类，映射device_data表，记录设备上报的采集数据及指标值
 * */
@Data
@TableName("device_data")
public class DeviceData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 数据ID（主键） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 设备ID */
    @TableField("device_id")
    @NotNull(message = "设备ID不能为空", groups = CreateGroup.class)
    private Integer deviceId;

    /** 指标标识（如 temperature） */
    @TableField("metric")
    private String metric;

    /** 数据值（保留两位小数） */
    @NotNull(message = "数据值不能为空", groups = CreateGroup.class)
    @DecimalMin(value = "-999.99", message = "数据值不能小于-999.99", groups = CreateGroup.class)
    @DecimalMax(value = "999.99", message = "数据值不能超过999.99", groups = CreateGroup.class)
    @TableField("data_value")
    private BigDecimal dataValue;

    /** 采集时间 */
    @TableField(value = "collect_time", fill = FieldFill.INSERT)
    private LocalDateTime collectTime;

    /** 逻辑删除标记：0-未删除，1-已删除 */
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

}
