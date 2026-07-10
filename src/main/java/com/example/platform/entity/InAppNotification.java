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
 * 站内通知实体类，映射 in_app_notification 表，承担已读状态；
 * 区别于 NotificationLog（notification_log 表），后者只是发送日志，不作为已读状态来源
 */
@Schema(description = "站内通知")
@Data
@TableName("in_app_notification")
public class InAppNotification implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "通知ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "接收用户ID（关联 sys_user）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "接收用户不能为空")
    @TableField("recipient_user_id")
    private Long recipientUserId;

    @Schema(description = "通知标题", example = "设备离线告警", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "通知标题不能为空")
    private String title;

    @Schema(description = "通知内容")
    private String content;

    @Schema(description = "来源类型（如 ALARM_EVENT）", example = "ALARM_EVENT")
    @TableField("source_type")
    private String sourceType;

    @Schema(description = "来源记录ID（如 alarm_event.id）", example = "1")
    @TableField("source_id")
    private Long sourceId;

    @Schema(description = "已读状态：0-未读 1-已读", example = "0")
    @TableField("is_read")
    private Integer isRead;

    @Schema(description = "已读时间")
    @TableField("read_time")
    private LocalDateTime readTime;

    @Schema(description = "创建时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
