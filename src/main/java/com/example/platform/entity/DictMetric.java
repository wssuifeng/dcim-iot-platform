package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/*
 * 类功能:指标字典实体类，映射dict_metric表，存储指标的中文名、单位等元数据信息
 * */
@Data
@TableName("dict_metric")
public class DictMetric implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 指标标识 (如 temperature) */
    private String metricKey;

    /** 指标中文名 (如 温度) */
    private String metricName;

    /** 数据单位 (如 ℃) */
    private String metricUnit;

    /** 指标描述 */
    private String description;

    /** 逻辑删除标记：0-未删除，1-已删除 */
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
