package com.example.platform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 告警规则配置顶层对象
 */
@Data
public class AlertRuleConfig {

    /**
     * 告警规则列表，至少包含一条规则。
     * 列表中的每条规则必须通过 {@link AlertRuleDefinition} 中定义的校验。
     */
    @NotEmpty(message = "告警规则列表不能为空")
    @Valid
    private List<AlertRuleDefinition> rules;

    // 未来还可以扩展通用配置，比如：告警静默期(秒)
    // private Integer silentPeriod;
}