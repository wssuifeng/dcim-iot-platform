package com.example.platform.dto;

import com.example.platform.entity.AlarmHandlingRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警处理记录 VO
 */
@Schema(description = "告警处理记录")
@Data
public class AlarmHandlingRecordVO {

    @Schema(description = "处理记录ID")
    private Long id;

    @Schema(description = "告警事件ID")
    private Long alarmEventId;

    @Schema(description = "处理人用户ID")
    private Long handlerUserId;

    @Schema(description = "处理动作")
    private String handlingAction;

    @Schema(description = "处理说明")
    private String handlingNote;

    @Schema(description = "处理时间")
    private LocalDateTime createTime;

    public static AlarmHandlingRecordVO fromEntity(AlarmHandlingRecord record) {
        AlarmHandlingRecordVO vo = new AlarmHandlingRecordVO();
        vo.setId(record.getId());
        vo.setAlarmEventId(record.getAlarmEventId());
        vo.setHandlerUserId(record.getHandlerUserId());
        vo.setHandlingAction(record.getHandlingAction());
        vo.setHandlingNote(record.getHandlingNote());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }
}