package com.example.platform.dto;

import com.example.platform.entity.InAppNotification;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知 VO
 */
@Schema(description = "站内通知")
@Data
public class InAppNotificationVO {

    @Schema(description = "通知ID")
    private Long id;

    @Schema(description = "接收用户ID")
    private Long recipientUserId;

    @Schema(description = "通知标题")
    private String title;

    @Schema(description = "通知内容")
    private String content;

    @Schema(description = "来源类型")
    private String sourceType;

    @Schema(description = "来源记录ID")
    private Long sourceId;

    @Schema(description = "已读状态：0-未读 1-已读")
    private Integer isRead;

    @Schema(description = "已读时间")
    private LocalDateTime readTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    public static InAppNotificationVO fromEntity(InAppNotification notification) {
        InAppNotificationVO vo = new InAppNotificationVO();
        vo.setId(notification.getId());
        vo.setRecipientUserId(notification.getRecipientUserId());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setSourceType(notification.getSourceType());
        vo.setSourceId(notification.getSourceId());
        vo.setIsRead(notification.getIsRead());
        vo.setReadTime(notification.getReadTime());
        vo.setCreateTime(notification.getCreateTime());
        return vo;
    }
}