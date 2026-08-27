package com.example.platform.dto;

import com.example.platform.entity.DevicePointAlarmRule;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 点位告警规则 VO（含点位展示回填与来源标记）
 */
@Schema(description = "点位告警规则响应")
@Data
public class PointAlarmRuleVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final String SOURCE_DEVICE_OVERRIDE = "DEVICE_OVERRIDE";
    public static final String SOURCE_TEMPLATE = "TEMPLATE";

    @Schema(description = "规则ID")
    private Long id;

    @Schema(description = "所属模板指标项ID")
    private Long templateItemId;

    @Schema(description = "设备ID（NULL=模板级规则）")
    private Integer deviceId;

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
    private java.math.BigDecimal threshold;

    @Schema(description = "告警级别：1-5")
    private Integer alarmLevel;

    @Schema(description = "是否启用：0停用 1启用")
    private Integer enabled;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "生效层级来源（仅 effective 接口返回）：DEVICE_OVERRIDE/TEMPLATE")
    private String source;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    public static PointAlarmRuleVO fromEntity(DevicePointAlarmRule rule) {
        PointAlarmRuleVO vo = new PointAlarmRuleVO();
        vo.setId(rule.getId());
        vo.setTemplateItemId(rule.getTemplateItemId());
        vo.setDeviceId(rule.getDeviceId());
        vo.setPointId(rule.getPointId());
        vo.setRuleType(rule.getRuleType());
        vo.setRuleName(rule.getRuleName());
        vo.setOperator(rule.getOperator());
        vo.setThreshold(rule.getThreshold());
        vo.setAlarmLevel(rule.getAlarmLevel());
        vo.setEnabled(rule.getEnabled());
        vo.setRemark(rule.getRemark());
        vo.setUpdateTime(rule.getUpdateTime());
        return vo;
    }
}
