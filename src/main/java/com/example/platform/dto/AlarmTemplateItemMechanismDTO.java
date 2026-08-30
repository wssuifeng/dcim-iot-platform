package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 告警规则机制字段组（S8-1：M1 连续越限 / M2 变化量 / M3 再告警 / 恢复迟滞）
 * 整组更新语义：作为整体可选字段出现——不传=保持现值（部分更新安全），
 * 传了=整组替换，组内 null=该项回落"不启用"默认值
 */
@Schema(description = "告警规则机制字段组（可选；不传=保持现值，组内 null=禁用该项）")
@Data
public class AlarmTemplateItemMechanismDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "M1 连续越限次数：null/≤1 不启用（首点越限即告警）；≥2 连续 N 个采样点越限才告警", example = "3")
    @Min(value = 0, message = "连续越限次数不能为负")
    @Max(value = 999, message = "连续越限次数最大为999")
    private Integer breachCount;

    @Schema(description = "M2 变化量限值：null 不启用；窗口内|当前-最早|超过即直接告警；累计量指标（如 power_kwh）禁用", example = "5.0")
    @DecimalMin(value = "0", message = "变化量限值不能为负")
    @DecimalMax(value = "99999999999999.9999", message = "变化量限值超出 DECIMAL(18,4) 范围")
    private BigDecimal rateLimit;

    @Schema(description = "M2 变化量窗口（秒），1-86400，null 按 60 处理", example = "60")
    @Min(value = 1, message = "变化量窗口最小为1秒")
    @Max(value = 86400, message = "变化量窗口最大为86400秒")
    private Integer rateWindowSec;

    @Schema(description = "M3 再告警间隔（秒）：null/0 不启用；活动告警持续越限且距最近一次成功通知超过该值时重发通知", example = "1800")
    @Min(value = 0, message = "再告警间隔不能为负")
    @Max(value = 604800, message = "再告警间隔最大为7天")
    private Integer reAlarmSec;

    @Schema(description = "恢复迟滞：null/0 不启用；GT 安全区=value<threshold-hysteresis，LT 安全区=value>threshold+hysteresis", example = "1.5")
    @DecimalMin(value = "0", message = "恢复迟滞不能为负")
    @DecimalMax(value = "99999999999999.9999", message = "恢复迟滞超出 DECIMAL(18,4) 范围")
    private BigDecimal hysteresis;
}
