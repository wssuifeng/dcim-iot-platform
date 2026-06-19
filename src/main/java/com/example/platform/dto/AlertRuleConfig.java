package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 告警规则配置DTO，包含一组告警规则定义，用于设备级或类型级的告警配置更新
 */
@Schema(description = "告警规则配置（设备级/类型级）")
@Data
public class AlertRuleConfig {

    @Schema(description = "告警规则定义列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "告警规则列表不能为空")
    @Valid
    private List<AlertRuleDefinition> rules;
}
