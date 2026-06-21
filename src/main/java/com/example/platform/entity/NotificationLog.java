package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知发送日志实体类，映射 notification_log 表，记录每次告警通知的发送结果
 */
@Schema(description = "通知发送日志")
@Data
@TableName("notification_log")
public class NotificationLog implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "日志ID", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "关联告警ID", example = "5")
    @TableField("alert_id")
    private Integer alertId;

    @Schema(description = "通知类型：EMAIL / WEBSOCKET", example = "EMAIL")
    @TableField("notification_type")
    private String notificationType;

    @Schema(description = "接收方（邮箱地址或用户ID）", example = "admin@example.com")
    private String recipient;

    @Schema(description = "通知内容")
    private String content;

    @Schema(description = "发送状态：0-待发送 1-成功 2-失败", example = "1")
    private Integer status;

    @Schema(description = "失败原因")
    @TableField("error_message")
    private String errorMessage;

    @Schema(description = "发送时间")
    @TableField("send_time")
    private LocalDateTime sendTime;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
