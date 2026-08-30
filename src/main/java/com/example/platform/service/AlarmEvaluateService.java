package com.example.platform.service;

import com.example.platform.event.MetricSampleSavedEvent;

/*
 * 类功能:告警评估引擎服务接口（T2 评估事务所有者）
 *
 * 【三段单向链路】采样(T1)→评估(T2)→分发(T3)；本服务监听采样事件，
 *   独立新事务执行规则评估，评估失败不影响已提交的采样（D15）。
 * */
public interface AlarmEvaluateService {

    /*
     * 方法功能:评估单个采样（三区判定+机制命中竞争+trigger去重/升级/静默窗口自动恢复/M3再告警）
     * 参数:event-采样保存事件（T1提交后发布）
     * 返回数据:无
     * */
    void evaluate(MetricSampleSavedEvent event);

    /*
     * 方法功能:停报兜底——扫描活动 METRIC 告警，最新采样已进安全区且距今超过静默窗口的做超窗确认恢复
     * 参数:无
     * 返回数据:本次确认恢复的告警事件数
     * */
    int recoverStaleConfirmedAlarms();
}
