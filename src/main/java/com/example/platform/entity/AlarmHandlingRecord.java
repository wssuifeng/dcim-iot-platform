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
 * 告警处理记录实体类，映射 alarm_handling_record 表，
 * 记录针对某个 AlarmEvent 的处理动作历史（如确认、分派、解决、关闭）
 */
@Schema(description = "告警处理记录")
@Data
@TableName("alarm_handling_record")
public class AlarmHandlingRecord implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "处理记录ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "告警事件ID（关联 alarm_event）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "告警事件ID不能为空")
    @TableField("alarm_event_id")
    private Long alarmEventId;

    @Schema(description = "处理人用户ID", example = "1")
    @TableField("handler_user_id")
    private Long handlerUserId;

    @Schema(description = "处理动作（如 ACK/ASSIGN/RESOLVE/CLOSE）", example = "ACK", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "处理动作不能为空")
    @TableField("handling_action")
    private String handlingAction;

    @Schema(description = "处理说明")
    @TableField("handling_note")
    private String handlingNote;

    @Schema(description = "处理时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
