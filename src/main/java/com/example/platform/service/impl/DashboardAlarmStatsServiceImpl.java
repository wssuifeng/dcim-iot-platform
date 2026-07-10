package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.DashboardAlarmStatsVO;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.AlarmEvent;
import com.example.platform.mapper.AlarmEventMapper;
import com.example.platform.service.DashboardAlarmStatsService;
import com.example.platform.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/*
 * 类功能:告警看板统计服务实现类
 *
 * 【契约】共享契约 §5：聚合数据不跨组织或机房泄露；
 *   所有聚合查询必须经过服务层范围校验，由 DataScopeService 解析用户范围。
 *
 * 【聚合策略】
 *   - 按授权组织 ID 集合过滤 AlarmEvent；
 *   - 状态分布、级别分布、近 7 日趋势、设备排名全部基于范围过滤后的数据集计算；
 *   - PLATFORM_ADMIN 看全量；VIEWER 按授权组织集合看。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardAlarmStatsServiceImpl implements DashboardAlarmStatsService {

    private final AlarmEventMapper alarmEventMapper;
    private final DataScopeService dataScopeService;

    private static final int DEVICE_RANKING_LIMIT = 10;
    private static final int RECENT_DAYS = 7;

    @Override
    public DashboardAlarmStatsVO getAlarmDashboardStats(Long currentUserId) {
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        List<AlarmEvent> scopedEvents = listScopedEvents(context);

        DashboardAlarmStatsVO vo = new DashboardAlarmStatsVO();
        vo.setScopeDescription(buildScopeDescription(context));
        vo.setStateDistribution(aggregateStateDistribution(scopedEvents));
        vo.setLevelDistribution(aggregateLevelDistribution(scopedEvents));
        vo.setDailyTrend(aggregateDailyTrend(scopedEvents));
        vo.setDeviceRanking(aggregateDeviceRanking(scopedEvents));
        return vo;
    }

    private List<AlarmEvent> listScopedEvents(DataScopeContext context) {
        LambdaQueryWrapper<AlarmEvent> wrapper = new LambdaQueryWrapper<>();
        if (!context.isPlatformAdmin()) {
            if (context.getOrganizationIds().isEmpty()) {
                return Collections.emptyList();
            }
            wrapper.in(AlarmEvent::getOrganizationId, context.getOrganizationIds());
        }
        return alarmEventMapper.selectList(wrapper);
    }

    private String buildScopeDescription(DataScopeContext context) {
        if (context.isPlatformAdmin()) {
            return "PLATFORM: 全量";
        }
        String scope = context.getScopeType() == null ? "UNKNOWN" : context.getScopeType();
        return scope + ": 组织" + context.getOrganizationIds();
    }

    private Map<String, Long> aggregateStateDistribution(List<AlarmEvent> events) {
        return events.stream()
                .filter(e -> e.getState() != null)
                .collect(Collectors.groupingBy(AlarmEvent::getState, Collectors.counting()));
    }

    private Map<String, Long> aggregateLevelDistribution(List<AlarmEvent> events) {
        return events.stream()
                .filter(e -> e.getAlarmLevel() != null)
                .collect(Collectors.groupingBy(
                        e -> "L" + e.getAlarmLevel(),
                        TreeMap::new,
                        Collectors.counting()));
    }

    private Map<String, Long> aggregateDailyTrend(List<AlarmEvent> events) {
        // 近 7 日每天告警触发数
        LocalDate today = LocalDate.now();
        Map<String, Long> trend = new LinkedHashMap<>();
        for (int i = RECENT_DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            trend.put(day.toString(), 0L);
        }
        for (AlarmEvent event : events) {
            if (event.getTriggeredTime() == null) {
                continue;
            }
            LocalDate day = event.getTriggeredTime().toLocalDate();
            String key = day.toString();
            if (trend.containsKey(key)) {
                trend.put(key, trend.get(key) + 1);
            }
        }
        return trend;
    }

    private List<Map<String, Object>> aggregateDeviceRanking(List<AlarmEvent> events) {
        Map<Integer, Long> counts = events.stream()
                .filter(e -> e.getDeviceId() != null)
                .collect(Collectors.groupingBy(AlarmEvent::getDeviceId, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(DEVICE_RANKING_LIMIT)
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("deviceId", entry.getKey());
                    item.put("alarmCount", entry.getValue());
                    return item;
                })
                .collect(Collectors.toList());
    }
}