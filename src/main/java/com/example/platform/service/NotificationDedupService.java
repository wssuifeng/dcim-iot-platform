package com.example.platform.service;

/*
 * 类功能:告警通知去重服务接口，基于Redis SETNX实现原子去重，防止短时间内重复发送通知
 *
 * 【去重策略——alertId粒度 + 主动删除 + TTL兜底】
 * 去重Key: notify:alert:{alertId}
 * 核心控制: 告警处理时主动调用releaseDedupMark删除Key
 * TTL兜底: 48小时（防止Key孤立，正常流程走主动删除）
 *
 * 【为什么用alertId而非(deviceId, alertType)？】
 * 旧方案Key=notify:dedup:{deviceId}:{alertType}，存在严重缺陷：
 *   告警处理完成后，同类告警再触发时，旧Key可能仍存在（TTL未过期），
 *   导致新告警被错误去重，通知遗漏。
 *   基于alertId后，每次新告警产生新的alertId，Key天然隔离，
 *   即使旧Key未删除也不影响新告警的去重判断。
 *
 * 【为什么用SETNX而非hasKey+set？】
 * hasKey和set是两条独立命令，并发场景下：
 *   线程A: hasKey → false
 *   线程B: hasKey → false  （A还没set，B也通过了）
 *   线程A: set → 发送通知
 *   线程B: set → 覆盖A的值 → 再次发送通知（重复！）
 * SETNX是原子操作，只有键不存在时才设置成功，天然防止并发重复。
 *
 * 【降级策略——宁可重复不可遗漏】
 * 告警场景下，遗漏通知可能导致设备故障未及时处理，后果严重；
 * 重复通知只是造成干扰，告警已入库不会丢失。因此Redis不可用时放行通知。
 * */
public interface NotificationDedupService {

    /*
     * 方法功能:判断指定告警是否应该发送通知（基于alertId去重）
     * 参数:alertId-告警ID
     * 返回数据:boolean-true表示应该发送（首次），false表示重复应跳过
     *
     * 【降级语义】
     * Redis不可用时返回true（放行通知），保证告警不遗漏。
     * */
    boolean shouldSendNotification(Integer alertId);

    /*
     * 方法功能:告警处理完成后主动释放去重标记
     * 参数:alertId-已处理的告警ID
     * 返回数据:无
     *
     * 【调用时机】
     * AlertServiceImpl.batchHandle() 处理成功后调用。
     *
     * 【失败不影响正确性】
     * 即使delete失败（Redis不可用），新告警使用新的alertId，
     * Key天然隔离，不会导致错误去重。
     * */
    void releaseDedupMark(Integer alertId);
}
