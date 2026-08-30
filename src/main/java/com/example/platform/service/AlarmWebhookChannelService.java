package com.example.platform.service;

import com.example.platform.dto.AlarmWebhookChannelSaveDTO;
import com.example.platform.dto.AlarmWebhookChannelVO;

import java.util.List;

/*
 * 类功能:告警 webhook 渠道管理服务接口（ADMIN-only：CRUD + 启停 + 测试发送）
 * */
public interface AlarmWebhookChannelService {

    /*
     * 方法功能:查询全部渠道配置
     * 参数:无
     * 返回数据:渠道配置列表
     * */
    List<AlarmWebhookChannelVO> listChannels();

    /*
     * 方法功能:创建渠道配置
     * 参数:dto-渠道配置
     * 返回数据:创建后的渠道配置
     * */
    AlarmWebhookChannelVO createChannel(AlarmWebhookChannelSaveDTO dto);

    /*
     * 方法功能:更新渠道配置
     * 参数:id-渠道ID, dto-渠道配置
     * 返回数据:更新后的渠道配置
     * */
    AlarmWebhookChannelVO updateChannel(Long id, AlarmWebhookChannelSaveDTO dto);

    /*
     * 方法功能:删除渠道配置
     * 参数:id-渠道ID
     * 返回数据:无
     * */
    void deleteChannel(Long id);

    /*
     * 方法功能:测试发送——渲染一条标注[测试]的固定样例报文直接发送（不产生告警事件、不入告警分发链、不写通知日志）
     * 参数:id-渠道ID
     * 返回数据:发送结果描述（成功含响应摘要；失败抛业务异常含错误原因）
     * */
    String testSend(Long id);
}
