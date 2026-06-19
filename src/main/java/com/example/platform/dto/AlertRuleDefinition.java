package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 单条告警规则定义DTO，描述一个指标的告警触发条件、级别及通知模板
 */
@Schema(description = "单条告警规则定义")
@Data
public class AlertRuleDefinition {

    @Schema(description = "指标标识（需与 dict_metric.metric_key 对应）", example = "temperature", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "指标标识不能为空")
    private String metricKey;

    @Schema(description = "比较运算符：GT(大于)、LT(小于)、GTE(大于等于)、LTE(小于等于)、EQ(等于)", example = "GT", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "运算符不能为空")
    @Pattern(regexp = "^(GT|LT|GTE|LTE|EQ)$", message = "运算符仅支持GT/LT/GTE/LTE/EQ")
    private String operator;

    @Schema(description = "告警阈值", example = "40.0", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "阈值不能为空")
    private Double threshold;

    @Schema(description = "告警级别：1-紧急，2-严重，3-一般，4-警告，5-提示", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "告警级别不能为空")
    @Min(value = 1, message = "告警级别最小为1")
    @Max(value = 5, message = "告警级别最大为5")
    private Integer level;

    @Schema(description = "告警消息模板（占位符：{metricName}/{unit}/{value}/{threshold}）", example = "{metricName}当前值{value}{unit}，超过阈值{threshold}{unit}", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "告警消息模板不能为空")
    private String messageTemplate;
}
