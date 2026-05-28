package com.example.platform.service.impl;

import com.example.platform.service.CacheService;
import com.example.platform.service.NotificationDedupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/*
 * 类功能:告警通知去重服务实现类，基于Redis SETNX实现原子去重
 *
 * 【实现细节】
 * 1. 去重Key格式: notify:alert:{alertId}
 * 2. 使用CacheService.setIfAbsent（底层SETNX）原子判断+写入
 * 3. setIfAbsent返回true → 键不存在，首次通知，应发送
 * 4. setIfAbsent返回false → 键已存在，已发过，跳过
 * 5. 告警处理时调用releaseDedupMark主动删除Key，释放去重标记
 * 6. TTL=48h仅作兜底，防止Key孤立（正常流程走主动删除）
 *
 * 【为什么基于alertId而非(deviceId, alertType)？】
 * 旧方案Key=notify:dedup:{deviceId}:{alertType}，告警处理完成后
 * 同类告警再触发时旧Key可能仍存在，导致新告警被错误去重。
 * 基于alertId后，每次新告警产生新的alertId，Key天然隔离。
 *
 * 【降级策略——宁可重复不可遗漏】
 * Redis不可用时：
 *   - shouldSendNotification: 返回true（放行通知）
 *   - releaseDedupMark: 仅记录日志，不影响正确性（新alertId天然隔离）
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDedupServiceImpl implements NotificationDedupService {

    private static final String DEDUP_KEY_PREFIX = "notify:alert:";
    private static final long DEDUP_TTL_HOURS = 48;

    private final CacheService cacheService;

    @Override
    public boolean shouldSendNotification(Integer alertId) {
        String dedupKey = DEDUP_KEY_PREFIX + alertId;
        try {
            boolean isFirst = cacheService.setIfAbsent(
                    dedupKey, "1", DEDUP_TTL_HOURS, TimeUnit.HOURS);
            if (isFirst) {
                log.debug("通知去重通过: alertId={}", alertId);
                return true;
            }
            log.debug("通知已去重跳过: alertId={}", alertId);
            return false;
        } catch (Exception e) {
            log.warn("通知去重检查异常，降级放行通知: alertId={}", alertId, e);
            return true;
        }
    }

    @Override
    public void releaseDedupMark(Integer alertId) {
        String dedupKey = DEDUP_KEY_PREFIX + alertId;
        try {
            cacheService.delete(dedupKey);
            log.debug("释放告警去重标记: alertId={}", alertId);
        } catch (Exception e) {
            log.warn("释放告警去重标记失败，不影响正确性（新alertId天然隔离）: alertId={}", alertId, e);
        }
    }
}
