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
 * 点位告警规则创建请求 DTO
 */
@Schema(description = "点位告警规则创建请求")
@Data
public class PointAlarmRuleCreateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "所属模板指标项ID（关联 device_type_point_template）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "模板指标项不能为空")
    private Long templateItemId;

    @Schema(description = "设备ID（传入=设备级覆盖规则；不传=模板级规则）", example = "2")
    private Integer deviceId;

    @Schema(description = "规则类型：METRIC-阈值 / OFFLINE-离线 / COMM-通信异常", example = "METRIC",
            allowableValues = {"METRIC", "OFFLINE", "COMM"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "规则类型不能为空")
    @Pattern(regexp = "^(METRIC|OFFLINE|COMM)$", message = "规则类型仅允许 METRIC/OFFLINE/COMM")
    private String ruleType;

    @Schema(description = "规则名（告警文案前缀）", example = "高温警告", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "规则名不能为空")
    @Size(max = 100, message = "规则名最长100个字符")
    private String ruleName;

    @Schema(description = "比较符：GT/LT/GE/LE/EQ/NE；OFFLINE/COMM 规则不传", example = "GT",
            allowableValues = {"GT", "LT", "GE", "LE", "EQ", "NE"})
    @Pattern(regexp = "^(GT|LT|GE|LE|EQ|NE)$", message = "比较符仅允许 GT/LT/GE/LE/EQ/NE")
    private String operator;

    @Schema(description = "阈值；OFFLINE 规则为离线分钟数（1-1440）", example = "35.0")
    @DecimalMin(value = "-99999999999999.9999", message = "阈值超出 DECIMAL(18,4) 范围")
    @DecimalMax(value = "99999999999999.9999", message = "阈值超出 DECIMAL(18,4) 范围")
    private BigDecimal threshold;

    @Schema(description = "告警级别：1-紧急 2-严重 3-一般 4-警告 5-提示", example = "2",
            allowableValues = {"1", "2", "3", "4", "5"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "告警级别不能为空")
    @Min(value = 1, message = "告警级别最小为1")
    @Max(value = 5, message = "告警级别最大为5")
    private Integer alarmLevel;

    @Schema(description = "是否启用，默认true", example = "true")
    private Boolean enabled = Boolean.TRUE;

    @Schema(description = "备注", example = "温度高于35℃触发警告")
    @Size(max = 200, message = "备注最长200个字符")
    private String remark;
}
