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
import java.time.LocalDateTime;

/**
 * 点位告警规则实体类，映射 device_point_alarm_rule 表，
 * 定义针对模板指标项的告警判断条件，支持设备级覆盖（三级降级：设备级→组织模板→平台模板）
 */
@Schema(description = "点位告警规则")
@Data
@TableName("device_point_alarm_rule")
public class DevicePointAlarmRule implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "规则ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属模板指标项ID（关联 device_type_point_template）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "模板指标项不能为空")
    @TableField("template_item_id")
    private Long templateItemId;

    @Schema(description = "设备ID（NULL=模板级规则；非空=该设备覆盖规则，优先生效）", example = "2")
    @TableField("device_id")
    private Integer deviceId;

    @Schema(description = "点位定义ID（NULL=通用规则如离线/通信异常）", example = "1")
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
    @Size(max = 5, message = "比较符最长5个字符")
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

    @Schema(description = "归属组织ID（冗余自模板项归属；设备覆盖规则=设备所属组织）", example = "0")
    @TableField("organization_id")
    private Long organizationId;

    @Schema(description = "备注")
    @Size(max = 200, message = "备注最长200个字符")
    @TableField("remark")
    private String remark;

    @Schema(description = "创建时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Schema(hidden = true)
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
