package com.example.platform.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/*
 * 类功能:单条告警规则定义DTO，描述一个指标的告警触发条件、级别及通知模板
 * */
@Data
public class AlertRuleDefinition {

    /** 指标标识（如 temperature），需与 dict_metric 表中的 metric_key 对应 */
    @NotBlank(message = "指标标识不能为空")
    private String metricKey;

    /** 比较运算符：GT(大于)、LT(小于)、GTE(大于等于)、LTE(小于等于)、EQ(等于) */
    @NotBlank(message = "运算符不能为空")
    @Pattern(regexp = "^(GT|LT|GTE|LTE|EQ)$", message = "运算符仅支持GT/LT/GTE/LTE/EQ")
    private String operator;

    /** 告警阈值 */
    @NotNull(message = "阈值不能为空")
    private Double threshold;

    /** 告警级别：1-紧急，2-严重，3-一般，4-警告，5-提示 */
    @NotNull(message = "告警级别不能为空")
    @Min(value = 1, message = "告警级别最小为1")
    @Max(value = 5, message = "告警级别最大为5")
    private Integer level;

    /** 告警消息模板，支持占位符：{metricName}/{unit}/{value}/{threshold} */
    @NotBlank(message = "告警消息模板不能为空")
    private String messageTemplate;
}
