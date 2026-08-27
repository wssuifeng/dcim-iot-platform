package com.example.platform.dto;

import com.example.platform.entity.AlarmNotificationLog;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警发送日志 VO
 */
@Schema(description = "告警发送日志响应")
@Data
public class AlarmNotificationLogVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "日志ID")
    private Long id;

    @Schema(description = "关联告警事件ID")
    private Long alarmEventId;

    @Schema(description = "发送通道：WEBSOCKET/EMAIL")
    private String channel;

    @Schema(description = "接收方（用户ID或邮箱）")
    private String recipient;

    @Schema(description = "发送内容摘要")
    private String content;

    @Schema(description = "发送状态：0待发 1已投递 2失败")
    private Integer status;

    @Schema(description = "失败原因")
    private String errorMessage;

    @Schema(description = "发送时间")
    private LocalDateTime sentTime;

    public static AlarmNotificationLogVO fromEntity(AlarmNotificationLog log) {
        AlarmNotificationLogVO vo = new AlarmNotificationLogVO();
        vo.setId(log.getId());
        vo.setAlarmEventId(log.getAlarmEventId());
        vo.setChannel(log.getChannel());
        vo.setRecipient(log.getRecipient());
        vo.setContent(log.getContent());
        vo.setStatus(log.getStatus());
        vo.setErrorMessage(log.getErrorMessage());
        vo.setSentTime(log.getSentTime());
        return vo;
    }
}
