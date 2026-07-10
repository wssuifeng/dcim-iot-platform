package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警状态流转记录实体类，映射 alarm_state_transition 表，
 * 记录 AlarmEvent.state 的每一次流转历史
 */
@Schema(description = "告警状态流转记录")
@Data
@TableName("alarm_state_transition")
public class AlarmStateTransition implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "流转记录ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "告警事件ID（关联 alarm_event）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "告警事件ID不能为空")
    @TableField("alarm_event_id")
    private Long alarmEventId;

    @Schema(description = "流转前状态（首次触发为空）", example = "TRIGGERED")
    @TableField("from_state")
    private String fromState;

    @Schema(description = "流转后状态", example = "ACKED", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "流转后状态不能为空")
    @TableField("to_state")
    private String toState;

    @Schema(description = "操作人用户ID（系统自动流转可为空）", example = "1")
    @TableField("operator_user_id")
    private Long operatorUserId;

    @Schema(description = "流转备注")
    private String remark;

    @Schema(description = "流转时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
