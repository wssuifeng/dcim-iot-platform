package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 告警模版规则行实体类，映射 alarm_template_item 表
 * 模版内的单条告警规则；point_id 为空表示通用规则（如 OFFLINE 离线检测）
 */
@Schema(description = "告警模版规则行")
@Data
@TableName("alarm_template_item")
public class AlarmTemplateItem implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "规则行ID（主键，自增）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属模版ID（关联 alarm_template）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属模版不能为空")
    @TableField("template_id")
    private Long templateId;

    @Schema(description = "指标定义ID（NULL=通用规则如 OFFLINE/COMM）", example = "1")
    @TableField("point_id")
    private Long pointId;

    @Schema(description = "规则类型：METRIC-阈值 / OFFLINE-离线 / COMM-通信异常", example = "METRIC",
            allowableValues = {"METRIC", "OFFLINE", "COMM"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "规则类型不能为空")
    @TableField("rule_type")
    private String ruleType;

    @Schema(description = "规则名（告警文案前缀）", example = "高温警告", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "规则名不能为空")
    @Size(max = 100, message = "规则名最长100个字符")
    @TableField("rule_name")
    private String ruleName;

    @Schema(description = "比较符：GT/LT/GE/LE/EQ/NE；OFFLINE/COMM 规则为空", example = "GT",
            allowableValues = {"GT", "LT", "GE", "LE", "EQ", "NE"})
    @Size(max = 10, message = "比较符最长10个字符")
    @TableField("operator")
    private String operator;

    @Schema(description = "阈值；OFFLINE 规则表示离线分钟数（1-1440）", example = "35.0")
    @TableField("threshold")
    private BigDecimal threshold;

    @Schema(description = "告警级别：1-紧急 2-严重 3-一般 4-警告 5-提示（数字小=严重）", example = "2",
            allowableValues = {"1", "2", "3", "4", "5"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "告警级别不能为空")
    @Min(value = 1, message = "告警级别最小为1")
    @Max(value = 5, message = "告警级别最大为5")
    @TableField("alarm_level")
    private Integer alarmLevel;

    @Schema(description = "是否启用：0-停用 1-启用", example = "1")
    @TableField("enabled")
    private Integer enabled;

    @Schema(description = "展示排序", example = "0")
    @TableField("sort_order")
    private Integer sortOrder;
}
