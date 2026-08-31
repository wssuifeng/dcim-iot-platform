package com.example.platform.task;

import com.example.platform.mapper.MetricSampleHourlyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/*
 * 类功能:指标小时聚合任务（S8-4）——每小时整点后 5 分钟聚合最近 2 个完整小时
 *
 * 【为什么聚合 2 小时而非 1 小时】覆盖 1 小时内迟到的 QoS1 重发/补传样本；
 *   ON DUPLICATE KEY 整桶覆盖，重复聚合幂等。当前小时不入聚合表，聚合存在 ≤1h 滞后，
 *   长跨度趋势（>48h）对该滞后不敏感。
 * */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricSampleHourlyAggregationTask {

    private final MetricSampleHourlyMapper metricSampleHourlyMapper;

    /*
     * 方法功能:聚合最近 2 个完整小时的采样到小时桶
     * 参数:无
     * 返回数据:无
     * */
    @Scheduled(cron = "${task.metric-hourly-aggregation-cron:0 5 * * * ?}")
    public void aggregateRecentHours() {
        try {
            LocalDateTime currentHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
            // 窗口 [currentHour-2h, currentHour)：恰好覆盖 2 个已完成的小时
            int rows = metricSampleHourlyMapper.aggregateWindow(currentHour.minusHours(2), currentHour);
            log.info("指标小时聚合完成 window=[{}, {}) affected={}",
                    currentHour.minusHours(2), currentHour, rows);
        } catch (Exception ex) {
            // 聚合失败不影响业务：下一轮整点重跑同窗口（幂等）
            log.error("指标小时聚合失败 error={}", ex.getMessage(), ex);
        }
    }
}
