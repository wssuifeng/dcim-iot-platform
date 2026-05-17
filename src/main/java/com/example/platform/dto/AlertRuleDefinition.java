package com.example.platform.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 单条告警规则定义
 */
@Data
public class AlertRuleDefinition {

    /**
     * 指标标识，用于指定监控的指标名称。
     * <p>示例：<code>"temperature"</code>（温度）、<code>"humidity"</code>（湿度）、<code>"cpu_usage"</code>（CPU使用率）</p>
     */
    @NotBlank(message = "指标标识不能为空")
    private String metric;

    /**
     * 比较操作符，用于将指标值与阈值进行比较。
     * <p>支持的操作符：{@literal >}、{@literal <}、{@literal >=}、{@literal <=}、{@code ==}</p>
     * <p>注意：字符串类型指标建议仅使用 {@code ==} 或 {@literal !=}，本配置目前仅支持数值比较操作符。</p>
     */
    @NotBlank(message = "比较操作符不能为空")
    @Pattern(regexp = ">|<|>=|<=|==", message = "操作符只支持 >, <, >=, <=, ==")
    private String operator;

    /**
     * 阈值，触发告警的临界值。
     * <p>类型为 {@link BigDecimal}，可精确表示小数，避免浮点数精度问题。</p>
     */
    @NotNull(message = "阈值不能为空")
    private BigDecimal threshold;

    /**
     * 告警级别，数值越小表示紧急程度越高。
     * <p>取值范围：1 ～ 5</p>
     * <ul>
     *   <li>1：紧急（Critical）</li>
     *   <li>2：严重（Major）</li>
     *   <li>3：一般（Minor）</li>
     *   <li>4：警告（Warning）</li>
     *   <li>5：提示（Info）</li>
     * </ul>
     */
    @NotNull(message = "告警级别不能为空")
    @Min(value = 1, message = "告警级别最小为1")
    @Max(value = 5, message = "告警级别最大为5")
    private Integer level;

    /**
     * 告警类型，用于分类不同的告警场景。
     * <p>示例：<code>"threshold"</code>（阈值告警）、<code>"trend"</code>（趋势告警）、<code>"absence"</code>（缺失告警）</p>
     * <p>建议使用常量或枚举管理，保证类型一致性。</p>
     */
    @NotBlank(message = "告警类型不能为空")
    private String alertType;

    /**
     * 消息模板，用于生成告警通知内容。
     * <p>支持占位符，例如：<code>"指标 ${metric} 当前值 ${value} 超过阈值 ${threshold}"</code>。</p>
     * <p>若未配置，系统可使用默认模板。</p>
     */
    private String messageTemplate;
}
