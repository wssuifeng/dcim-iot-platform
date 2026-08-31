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

    private Long cloudRoomId;

    /* 命中规则行 ID（评估链命中时透传；离线检测等无规则来源为 null）——S8-9 下行动作定位用 */
    private Long templateItemId;
}
