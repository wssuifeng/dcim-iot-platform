package com.example.platform.event;

/*
 * 类功能:告警自动恢复完成事件（T2事务提交后发布，仅向前传播至通知分发）
 * */
public record AlarmEventRecoveredEvent(
        Long alarmEventId) {
}
