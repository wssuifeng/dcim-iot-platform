package com.example.platform.dto;

import com.example.platform.entity.AlarmEvent;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 告警触发结果（CREATED=新建 / UPGRADED=升级 / SKIPPED=活动告警去重跳过）
 */
@Data
public class AlarmTriggerResult implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final String KIND_CREATED = "CREATED";
    public static final String KIND_UPGRADED = "UPGRADED";
    public static final String KIND_SKIPPED = "SKIPPED";

    private AlarmEvent alarmEvent;

    private String kind;

    public static AlarmTriggerResult of(AlarmEvent event, String kind) {
        AlarmTriggerResult result = new AlarmTriggerResult();
        result.setAlarmEvent(event);
        result.setKind(kind);
        return result;
    }

    public boolean isNotifiable() {
        return !KIND_SKIPPED.equals(kind);
    }
}
