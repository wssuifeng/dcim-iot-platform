package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    @NotNull(message = "设备ID不能为空")
    private Integer deviceId;

    /** 数据指标标识（如 temperature、humidity） */
    @TableField("metric")
    private String metric;  // <--- 新增字段，替代 dataUnit

    /** 数据值（保留两位小数） */
    @NotNull(message = "数据值不能为空")
    @DecimalMin(value = "-999.99", message = "数据值不能小于-999.99")
    @DecimalMax(value = "999.99", message = "数据值不能超过999.99")
    @TableField("data_value")
    private BigDecimal dataValue;

    /** 采集时间 */
    @TableField(value = "collect_time", fill = FieldFill.INSERT)
    private LocalDateTime collectTime;

    /*
     * 逻辑删除字段（当前未启用）
     * 如需开启逻辑删除，请：
     * 1. 在数据库中执行：ALTER TABLE device_data ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除';
     * 2. 取消下方字段及 @TableLogic 注解的注释
     */
    // @TableLogic(value = "0", delval = "1")
    // private Integer deleted;

}
