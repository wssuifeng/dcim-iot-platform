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
     * 方法功能:评估单个采样（规则三级降级检索→阈值校验→trigger去重/升级/自动恢复）
     * 参数:event-采样保存事件（T1提交后发布）
     * 返回数据:无
     * */
    void evaluate(MetricSampleSavedEvent event);
}
