package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 告警模版规则行更新请求 DTO（指标与规则类型创建后不可变更）
 */
@Schema(description = "告警模版规则行更新请求")
@Data
public class AlarmTemplateItemUpdateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "规则名（告警文案前缀）", example = "高温警告", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "规则名不能为空")
    @Size(max = 100, message = "规则名最长100个字符")
    private String ruleName;

    @Schema(description = "比较符：GT/LT/GE/LE/EQ/NE；OFFLINE/COMM 规则为空", example = "GT",
            allowableValues = {"GT", "LT", "GE", "LE", "EQ", "NE"})
    @Pattern(regexp = "^(GT|LT|GE|LE|EQ|NE)$", message = "比较符仅允许 GT/LT/GE/LE/EQ/NE")
    private String operator;

    @Schema(description = "阈值；OFFLINE 规则为离线分钟数（1-1440）", example = "35.0")
    @DecimalMin(value = "-99999999999999.9999", message = "阈值超出 DECIMAL(18,4) 范围")
    @DecimalMax(value = "99999999999999.9999", message = "阈值超出 DECIMAL(18,4) 范围")
    private BigDecimal threshold;

    @Schema(description = "下行动作 webhook：非空即启用，规则命中触发/升级时回调外部系统（通知钩子非控制闭环）", example = "https://ops.example.com/alarm-hook")
    @Size(max = 500, message = "下行动作 webhook 地址最长500个字符")
    private String actionWebhookUrl;

    @Schema(description = "S8-1 机制字段组（M1 连续越限/M2 变化量/M3 再告警/恢复迟滞）：不传=保持现值；组内 null=该项禁用")
    private AlarmTemplateItemMechanismDTO mechanism;

    @Schema(description = "告警级别：1-紧急 2-严重 3-一般 4-警告 5-提示", example = "2",
            allowableValues = {"1", "2", "3", "4", "5"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "告警级别不能为空")
    @Min(value = 1, message = "告警级别最小为1")
    @Max(value = 5, message = "告警级别最大为5")
    private Integer alarmLevel;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    @Schema(description = "展示排序", example = "0")
    private Integer sortOrder;
}
