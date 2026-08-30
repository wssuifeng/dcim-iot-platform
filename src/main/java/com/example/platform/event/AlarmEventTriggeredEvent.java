package com.example.platform.event;

/*
 * 类功能:告警事件产生/升级/再提醒事件（T2事务提交后发布，仅向前传播至通知分发）
 * */
public record AlarmEventTriggeredEvent(
        Long alarmEventId,
        String kind) {

    public static final String KIND_CREATED = "CREATED";
    public static final String KIND_UPGRADED = "UPGRADED";
    public static final String KIND_RESEND = "RESEND";
}
