package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("dict_metric")
public class DictMetric {
    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 指标标识 (如 temperature) */
    private String metricKey;

    /** 指标中文名 (如 温度) */
    private String metricName;

    /** 数据单位 (如 ℃) */
    private String metricUnit;

    private String description;
}
