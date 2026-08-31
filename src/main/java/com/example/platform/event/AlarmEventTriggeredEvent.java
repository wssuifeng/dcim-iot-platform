package com.example.platform.event;

/*
 * 类功能:告警事件产生/升级/再提醒事件（T2事务提交后发布，仅向前传播至通知分发）
 *   templateItemId 为命中规则行（S8-9 下行动作 webhook 的规则级开关定位）；
 *   M3 再提醒与人工流转可能无命中规则行，此时为 null。
 * */
public record AlarmEventTriggeredEvent(
        Long alarmEventId,
        String kind,
        Long templateItemId) {

    public static final String KIND_CREATED = "CREATED";
    public static final String KIND_UPGRADED = "UPGRADED";
    public static final String KIND_RESEND = "RESEND";
}
