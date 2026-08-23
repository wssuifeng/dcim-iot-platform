package com.example.platform.service;

import com.example.platform.entity.AlarmEvent;

import java.util.List;

/*
 * 类功能:告警邮件兜底发送器（替代旧EmailNotificationServiceImpl，无去重逻辑——
 *   活动告警唯一键已在状态机去重，D17）
 * */
public interface AlarmEmailSender {

    /*
     * 方法功能:群发告警邮件并逐人记录发送日志（失败仅记日志不抛出）
     * 参数:alarmEvent-告警事件, emails-收件人邮箱列表
     * 返回数据:无
     * */
    void send(AlarmEvent alarmEvent, List<String> emails);
}
