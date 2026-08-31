package com.example.platform.service;

import com.example.platform.entity.AlarmEvent;

/*
 * 类功能:告警下行动作服务接口（S8-9）——规则命中触发/升级时回调规则配置的外部 URL
 * */
public interface AlarmActionWebhookService {

    /*
     * 方法功能:按命中规则行的 action_webhook_url 回调外部系统（非空即启用）
     * 参数:event-告警事件, templateItemId-命中规则行（null 时无动作）
     * 返回数据:无
     * */
    void invokeAction(AlarmEvent event, Long templateItemId);
}
