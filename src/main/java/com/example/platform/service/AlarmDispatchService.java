package com.example.platform.service;

import com.example.platform.event.AlarmEventRecoveredEvent;
import com.example.platform.event.AlarmEventTriggeredEvent;

/*
 * 类功能:告警分发服务接口（T3 分发所有者，无事务）
 *
 * 【分发策略（D23 逐人兜底）】在线者走 WS 定向摘要，离线者走邮件；
 *   恢复事件仅 WS 摘要+站内通知，不发邮件（D24 不打扰）；
 *   每通道独立写发送日志，任何通道失败不影响告警与其他通道。
 * */
public interface AlarmDispatchService {

    /*
     * 方法功能:分发产生/升级告警（解析收件人→在线分组→WS摘要→离线邮件→站内通知→发送日志）
     * 参数:event-告警触发事件（T2提交后发布，kind=CREATED/UPGRADED）
     * 返回数据:无
     * */
    void dispatch(AlarmEventTriggeredEvent event);

    /*
     * 方法功能:分发自动恢复告警（仅 WS 摘要与站内通知，不发邮件）
     * 参数:event-告警恢复事件
     * 返回数据:无
     * */
    void onRecovered(AlarmEventRecoveredEvent event);
}
