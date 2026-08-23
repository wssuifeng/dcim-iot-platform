package com.example.platform.dto;

import com.example.platform.entity.AlarmEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警事件 VO
 */
@Schema(description = "告警事件响应")
@Data
public class AlarmEventVO {

    @Schema(description = "告警事件ID")
    private Long id;

    @Schema(description = "设备ID")
    private Integer deviceId;

    @Schema(description = "触发点位ID")
    private Long pointId;

    @Schema(description = "告警类型")
    private String alarmType;

    @Schema(description = "告警级别：1-紧急 2-严重 3-一般 4-警告 5-提示")
    private Integer alarmLevel;

    @Schema(description = "告警详细描述")
    private String alarmMessage;

    @Schema(description = "权威状态：TRIGGERED/ACKED/HANDLING/RECOVERED/CLOSED")
    private String state;

    @Schema(description = "所属组织ID")
    private Long organizationId;

    @Schema(description = "所属机房ID")
    private Long cloudRoomId;

    @Schema(description = "触发时间")
    private LocalDateTime triggeredTime;

    @Schema(description = "恢复时间")
    private LocalDateTime recoveredTime;

    @Schema(description = "关闭时间")
    private LocalDateTime closedTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "设备名称（冗余回填，列表直显）", example = "机房A-温湿度-01")
    private String deviceName;

    @Schema(description = "点位名称（冗余回填，通用告警为空）", example = "温度")
    private String pointName;

    @Schema(description = "点位单位（冗余回填，前端渲染 38.5℃ 用）", example = "℃")
    private String pointUnit;

    public static AlarmEventVO fromEntity(AlarmEvent event) {
        AlarmEventVO vo = new AlarmEventVO();
        vo.setId(event.getId());
        vo.setDeviceId(event.getDeviceId());
        vo.setPointId(event.getPointId());
        vo.setAlarmType(event.getAlarmType());
        vo.setAlarmLevel(event.getAlarmLevel());
        vo.setAlarmMessage(event.getAlarmMessage());
        vo.setState(event.getState());
        vo.setOrganizationId(event.getOrganizationId());
        vo.setCloudRoomId(event.getCloudRoomId());
        vo.setTriggeredTime(event.getTriggeredTime());
        vo.setRecoveredTime(event.getRecoveredTime());
        vo.setClosedTime(event.getClosedTime());
        vo.setCreateTime(event.getCreateTime());
        vo.setUpdateTime(event.getUpdateTime());
        return vo;
    }
}