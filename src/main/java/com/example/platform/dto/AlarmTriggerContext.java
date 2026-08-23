package com.example.platform.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 告警触发上下文（评估引擎与离线检测委托状态机产生告警的入参）
 */
@lombok.Builder
@Data
public class AlarmTriggerContext implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Integer deviceId;

    private Long pointId;

    private String alarmType;

    private Integer alarmLevel;

    private String alarmMessage;

    private Long organizationId;

    private Long cloudRoomId;
}
