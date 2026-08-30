package com.example.platform.service;

import com.example.platform.entity.AlarmEvent;

/*
 * 类功能:告警 webhook 分发器接口——站内/邮件之外并行挂载的 IM webhook 渠道分发
 * */
public interface AlarmWebhookDispatcher {

    /*
     * 方法功能:按启用渠道分发告警到各 IM webhook（min_level 过滤 + 失败退避重试 + 逐渠道写通知日志）
     * 参数:event-告警事件, eventName-事件类型（ALARM_CREATED/ALARM_UPGRADED/ALARM_RESEND/ALARM_RECOVERED）
     * 返回数据:无
     * */
    void dispatch(AlarmEvent event, String eventName);
}
