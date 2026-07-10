package com.example.platform.dto;

import com.example.platform.entity.AlarmStateTransition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警状态流转记录 VO
 */
@Schema(description = "告警状态流转记录")
@Data
public class AlarmStateTransitionVO {

    @Schema(description = "流转记录ID")
    private Long id;

    @Schema(description = "告警事件ID")
    private Long alarmEventId;

    @Schema(description = "流转前状态（首次触发为空）")
    private String fromState;

    @Schema(description = "流转后状态")
    private String toState;

    @Schema(description = "操作人用户ID")
    private Long operatorUserId;

    @Schema(description = "流转备注")
    private String remark;

    @Schema(description = "流转时间")
    private LocalDateTime createTime;

    public static AlarmStateTransitionVO fromEntity(AlarmStateTransition transition) {
        AlarmStateTransitionVO vo = new AlarmStateTransitionVO();
        vo.setId(transition.getId());
        vo.setAlarmEventId(transition.getAlarmEventId());
        vo.setFromState(transition.getFromState());
        vo.setToState(transition.getToState());
        vo.setOperatorUserId(transition.getOperatorUserId());
        vo.setRemark(transition.getRemark());
        vo.setCreateTime(transition.getCreateTime());
        return vo;
    }
}