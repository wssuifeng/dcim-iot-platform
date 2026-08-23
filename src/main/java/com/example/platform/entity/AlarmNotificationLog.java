package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警发送日志实体类，映射 alarm_notification_log 表，
 * 记录每条告警经各通道（WebSocket/邮件）的发送结果；已读状态由 in_app_notification 承担
 */
@Schema(description = "告警发送日志")
@Data
@TableName("alarm_notification_log")
public class AlarmNotificationLog implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "日志ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "关联告警事件ID（alarm_event.id）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "告警事件ID不能为空")
    @TableField("alarm_event_id")
    private Long alarmEventId;

    @Schema(description = "发送通道：WEBSOCKET / EMAIL", example = "WEBSOCKET",
            allowableValues = {"WEBSOCKET", "EMAIL"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "发送通道不能为空")
    @TableField("channel")
    private String channel;

    @Schema(description = "接收方（用户ID或邮箱）", example = "3")
    @Size(max = 100, message = "接收方最长100个字符")
    @TableField("recipient")
    private String recipient;

    @Schema(description = "发送内容摘要")
    @Size(max = 500, message = "发送内容最长500个字符")
    @TableField("content")
    private String content;

    @Schema(description = "发送状态：0-待发 1-已投递 2-失败；WEBSOCKET 的已投递表示已进入用户队列，非已读", example = "1",
            allowableValues = {"0", "1", "2"})
    @TableField("status")
    private Integer status;

    @Schema(description = "失败原因")
    @Size(max = 500, message = "失败原因最长500个字符")
    @TableField("error_message")
    private String errorMessage;

    @Schema(description = "发送时间", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField("sent_time")
    private LocalDateTime sentTime;

    @Schema(description = "所属组织ID（查询隔离）", example = "1")
    @TableField("organization_id")
    private Long organizationId;

    @Schema(description = "创建时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
