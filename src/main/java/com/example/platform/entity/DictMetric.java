package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 指标字典实体类，映射 dict_metric 表，存储指标的中文名、单位等元数据信息
 */
@Schema(description = "指标字典")
@Data
@TableName("dict_metric")
public class DictMetric implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "指标ID", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "指标标识（如 temperature）", example = "temperature", requiredMode = Schema.RequiredMode.REQUIRED)
    private String metricKey;

    @Schema(description = "指标中文名", example = "温度", requiredMode = Schema.RequiredMode.REQUIRED)
    private String metricName;

    @Schema(description = "数据单位", example = "℃")
    private String metricUnit;

    @Schema(description = "指标描述", example = "设备温度采集值")
    private String description;

    @Schema(hidden = true)
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
