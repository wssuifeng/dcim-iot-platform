package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

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
    @NotBlank(message = "指标标识不能为空")
    @Pattern(regexp = "^[a-z_]+$", message = "指标标识只能包含小写字母和下划线")
    @Size(max = 50, message = "指标标识长度不能超过50")
    private String metricKey;

    @Schema(description = "指标中文名", example = "温度", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "指标中文名不能为空")
    @Size(max = 50, message = "指标中文名长度不能超过50")
    private String metricName;

    @Schema(description = "数据单位", example = "℃")
    @Size(max = 20, message = "单位长度不能超过20")
    private String metricUnit;

    @Schema(description = "指标描述", example = "设备温度采集值")
    @Size(max = 200, message = "描述长度不能超过200")
    private String description;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Schema(hidden = true)
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
