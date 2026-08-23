package com.example.platform.service.impl;

import com.example.platform.dto.DashboardStatsDTO;
import com.example.platform.entity.AlarmEvent;
import com.example.platform.mapper.AlarmEventMapper;
import com.example.platform.service.CacheService;
import com.example.platform.service.DashboardStatsService;
import com.example.platform.service.DeviceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/*
 * 类功能:仪表盘统计服务实现类，提供带互斥锁防击穿的缓存统计查询
 *
 * 【缓存击穿防护——互斥锁方案】
 * 流程：
 *   1. 读缓存 stats:dashboard
 *   2. 缓存命中 → 直接返回
 *   3. 缓存未命中 → setIfAbsent(stats:dashboard:lock) 尝试获锁
 *     - 获锁成功 → 查DB构建数据 → set回填缓存(TTL=30s) → delete释放锁
 *     - 获锁失败 → sleep(100ms) → 重试读缓存（最多3次）
 *   4. 重试耗尽仍无缓存 → 降级直接查DB返回（不回填缓存，避免异常时写入脏数据）
 *
 * 【Redis异常降级】
 * CacheService的setIfAbsent在Redis异常时返回false，不会抛异常，
 * 因此获锁失败后重试耗尽时直接查DB，保证服务可用性。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardStatsServiceImpl implements DashboardStatsService {

    private static final String CACHE_KEY = "stats:dashboard";
    private static final String LOCK_KEY = "stats:dashboard:lock";
    private static final long CACHE_TTL_SECONDS = 30;
    private static final long LOCK_TTL_SECONDS = 3;
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL_MS = 100;

    private final CacheService cacheService;
    private final DeviceService deviceService;
    private final AlarmEventMapper alarmEventMapper;

    @Override
    public DashboardStatsDTO getDashboardStats() {
        DashboardStatsDTO cached = cacheService.get(CACHE_KEY, DashboardStatsDTO.class);
        if (cached != null) {
            return cached;
        }
        return buildWithMutexLock();
    }

    /*
     * 方法功能:互斥锁保护的缓存重建逻辑
     * 返回数据:DashboardStatsDTO-统计数据（优先从缓存获取，否则查DB）
     * */
    private DashboardStatsDTO buildWithMutexLock() {
        boolean lockAcquired = cacheService.setIfAbsent(
                LOCK_KEY, "1", LOCK_TTL_SECONDS, TimeUnit.SECONDS);

        if (lockAcquired) {
            try {
                DashboardStatsDTO stats = buildFromDB();
                cacheService.set(CACHE_KEY, stats, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
                log.debug("仪表盘缓存重建完成, TTL={}s", CACHE_TTL_SECONDS);
                return stats;
            } finally {
                cacheService.delete(LOCK_KEY);
            }
        }

        return retryReadCache();
    }

    /*
     * 方法功能:获锁失败后的重试读缓存逻辑
     * 返回数据:DashboardStatsDTO-重试成功返回缓存数据，耗尽则降级查DB
     * */
    private DashboardStatsDTO retryReadCache() {
        for (int i = 0; i < MAX_RETRY_COUNT; i++) {
            try {
                Thread.sleep(RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            DashboardStatsDTO cached = cacheService.get(CACHE_KEY, DashboardStatsDTO.class);
            if (cached != null) {
                return cached;
            }
        }

        log.warn("仪表盘缓存重试耗尽，降级直接查DB");
        return buildFromDB();
    }

    /*
     * 方法功能:从数据库聚合查询构建仪表盘统计数据
     * 返回数据:DashboardStatsDTO-包含设备统计和告警统计
     *
     * 【数据来源精确映射】
     * - DeviceStats.total    ← deviceService.count()
     * - DeviceStats.online   ← countByStatus() 中 status=1 的 count
     * - DeviceStats.offline  ← countByStatus() 中 status=0 的 count
     * - DeviceStats.fault    ← countByStatus() 中 status=2 的 count
     * - AlertStats.total     ← alarmEventMapper.selectCount(全部)
     * - AlertStats.unhandled ← alarmEventMapper.selectCount(活动态 NOT IN RECOVERED/CLOSED)
     * - AlertStats.last24h   ← alarmEventMapper.selectCount(triggered_time >= 24h前)
     * */
    private DashboardStatsDTO buildFromDB() {
        DashboardStatsDTO stats = new DashboardStatsDTO();
        stats.setDeviceStats(buildDeviceStats());
        stats.setAlertStats(buildAlertStats());
        return stats;
    }

    private DashboardStatsDTO.DeviceStats buildDeviceStats() {
        DashboardStatsDTO.DeviceStats deviceStats = new DashboardStatsDTO.DeviceStats();

        long total = deviceService.count();
        deviceStats.setTotal((int) total);

        List<Map<String, Object>> statusCounts = deviceService.countByStatus();
        for (Map<String, Object> entry : statusCounts) {
            int status = ((Number) entry.get("status")).intValue();
            int count = ((Number) entry.get("count")).intValue();
            switch (status) {
                case 0 -> deviceStats.setOffline(count);
                case 1 -> deviceStats.setOnline(count);
                case 2 -> deviceStats.setFault(count);
            }
        }

        return deviceStats;
    }

    private DashboardStatsDTO.AlertStats buildAlertStats() {
        DashboardStatsDTO.AlertStats alertStats = new DashboardStatsDTO.AlertStats();

        // 活动告警 = 状态不在 RECOVERED/CLOSED（告警状态机权威态）
        List<String> activeStates = List.of("RECOVERED", "CLOSED");
        alertStats.setTotal(alarmEventMapper.selectCount(null));
        alertStats.setUnhandled(alarmEventMapper.selectCount(new LambdaQueryWrapper<AlarmEvent>()
                .notIn(AlarmEvent::getState, activeStates)));

        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        alertStats.setLast24h(alarmEventMapper.selectCount(new LambdaQueryWrapper<AlarmEvent>()
                .ge(AlarmEvent::getTriggeredTime, yesterday)));

        return alertStats;
    }
}
