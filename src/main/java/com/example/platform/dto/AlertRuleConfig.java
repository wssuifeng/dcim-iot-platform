package com.example.platform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/*
 * 类功能:告警规则配置DTO，包含一组告警规则定义，用于设备级或类型级的告警配置更新
 * */
@Data
public class AlertRuleConfig {

    /** 告警规则定义列表 */
    @NotEmpty(message = "告警规则列表不能为空")
    @Valid
    private List<AlertRuleDefinition> rules;
}
