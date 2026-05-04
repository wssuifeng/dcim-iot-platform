package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("device_data")
public class DeviceData implements Serializable {

    /** 数据ID（主键，BIGINT 类型，用于海量数据） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;               // 对应数据库 BIGINT

    /** 设备ID（关联 devices 表） */
    @TableField("device_id")
    private Integer deviceId;

    /** 数据值（保留两位小数） */
    @TableField("data_value")
    private BigDecimal dataValue;

    /** 数据单位（如 ℃、%） */
    @TableField("data_unit")
    private String dataUnit;

    /** 采集时间（默认当前时间） */
    @TableField("collect_time")
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