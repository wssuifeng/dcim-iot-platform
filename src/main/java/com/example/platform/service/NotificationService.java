package com.example.platform.service;

import com.example.platform.entity.Alert;

/*
 * 类功能:告警通知服务接口，定义告警产生后的多渠道通知能力
 * */
public interface NotificationService {

    /*
     * 方法功能:发送告警通知（邮件等），由实现类决定具体通道
     * 参数:alert-已持久化的告警实体
     * 返回数据:boolean-发送成功返回true
     * */
    boolean sendAlert(Alert alert);
}
