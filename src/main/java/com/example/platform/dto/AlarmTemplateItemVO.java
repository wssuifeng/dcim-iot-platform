package com.example.platform.dto;

import com.example.platform.entity.AlarmTemplateItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 告警模版规则行响应 VO（含点位展示回填）
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

    @Schema(description = "点位ID（NULL=通用规则）")
    private Long pointId;

    @Schema(description = "点位编码（回填）", example = "temperature")
    private String pointCode;

    @Schema(description = "点位名称（回填）", example = "温度")
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
        vo.setAlarmLevel(item.getAlarmLevel());
        vo.setEnabled(item.getEnabled());
        vo.setSortOrder(item.getSortOrder());
        return vo;
    }
}
