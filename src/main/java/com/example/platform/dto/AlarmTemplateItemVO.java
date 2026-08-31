package com.example.platform.dto;

import com.example.platform.entity.AlarmTemplateItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 告警模版规则行响应 VO（含指标展示回填）
 */
@Schema(description = "告警模版规则行响应")
@Data
public class AlarmTemplateItemVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "规则行ID")
    private Long id;

    @Schema(description = "所属模版ID")
    private Long templateId;

    @Schema(description = "指标ID（NULL=通用规则）")
    private Long pointId;

    @Schema(description = "指标编码（回填）", example = "temperature")
    private String pointCode;

    @Schema(description = "指标名称（回填）", example = "温度")
    private String pointName;

    @Schema(description = "单位（回填）", example = "℃")
    private String unit;

    @Schema(description = "规则类型：METRIC/OFFLINE/COMM")
    private String ruleType;

    @Schema(description = "规则名")
    private String ruleName;

    @Schema(description = "比较符")
    private String operator;

    @Schema(description = "阈值")
    private BigDecimal threshold;

    @Schema(description = "M1 连续越限次数：≤1 不启用；≥2 连续 N 个采样点越限才告警")
    private Integer breachCount;

    @Schema(description = "M2 变化量限值：NULL 不启用；窗口内变化量超过即直接告警")
    private BigDecimal rateLimit;

    @Schema(description = "M2 变化量窗口（秒）")
    private Integer rateWindowSec;

    @Schema(description = "M3 再告警间隔（秒）：0 不启用")
    private Integer reAlarmSec;

    @Schema(description = "恢复迟滞：0 不启用")
    private BigDecimal hysteresis;

    @Schema(description = "下行动作 webhook：非空即启用")
    private String actionWebhookUrl;

    @Schema(description = "告警级别：1-5")
    private Integer alarmLevel;

    @Schema(description = "是否启用：0停用 1启用")
    private Integer enabled;

    @Schema(description = "展示排序")
    private Integer sortOrder;

    public static AlarmTemplateItemVO fromEntity(AlarmTemplateItem item) {
        AlarmTemplateItemVO vo = new AlarmTemplateItemVO();
        vo.setId(item.getId());
        vo.setTemplateId(item.getTemplateId());
        vo.setPointId(item.getPointId());
        vo.setRuleType(item.getRuleType());
        vo.setRuleName(item.getRuleName());
        vo.setOperator(item.getOperator());
        vo.setThreshold(item.getThreshold());
        vo.setBreachCount(item.getBreachCount());
        vo.setRateLimit(item.getRateLimit());
        vo.setRateWindowSec(item.getRateWindowSec());
        vo.setReAlarmSec(item.getReAlarmSec());
        vo.setHysteresis(item.getHysteresis());
        vo.setActionWebhookUrl(item.getActionWebhookUrl());
        vo.setAlarmLevel(item.getAlarmLevel());
        vo.setEnabled(item.getEnabled());
        vo.setSortOrder(item.getSortOrder());
        return vo;
    }
}
