package com.example.platform.task;

import com.example.platform.dto.AlarmTriggerContext;
import com.example.platform.entity.MetricRetentionPolicy;
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.MetricRetentionPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/*
 * 类功能:采样表月分区供给与过期回收任务（S8-4）
 *
 * 【为什么必须有供给任务】MySQL 不自动创建分区，V21 又刻意不建 MAXVALUE 兜底分区
 *   （有它则无法 ADD PARTITION）——缺任务或任务故障时，跨月首日写入直接失败，
 *   因此供给失败必须以告警可见（PARTITION_SUPPLY_FAILED，复用告警工作流与通知链路）。
 * 【供给】每日检查未来 2 个月分区，缺失即 ADD PARTITION；
 * 【回收】分区级 TTL：整月已过保留期的分区 DROP（精确且快）；月内残余行级删除
 *   继续由指标保留策略清理任务承担（drop 分区 + 行级删除混合口径）。
 * */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricSamplePartitionTask {

    public static final String ALARM_TYPE_PARTITION_SUPPLY = "PARTITION_SUPPLY_FAILED";

    private final JdbcTemplate jdbcTemplate;
    private final AlarmEventService alarmEventService;
    private final MetricRetentionPolicyService metricRetentionPolicyService;

    // 查询/操作的目标表（固定 metric_sample；information_schema 查询限定当前库）
    private static final String TABLE_NAME = "metric_sample";

    // 全局保留策略兜底天数（与指标保留策略服务一致）
    @Value("${metric.retention-default-days:90}")
    private int retentionDefaultDays;

    /*
     * 方法功能:每日检查未来 2 个月分区供给 + 整月过期分区回收
     * 参数:无
     * 返回数据:无
     * */
    @Scheduled(cron = "${task.metric-partition-cron:0 15 0 * * ?}")
    public void ensureAndRecycle() {
        try {
            ensureFuturePartitions(YearMonth.now());
        } catch (Exception ex) {
            log.error("采样表分区供给失败 error={}", ex.getMessage(), ex);
            triggerSupplyAlarm(ex);
        }
        try {
            dropExpiredPartitions(LocalDate.now());
        } catch (Exception ex) {
            log.error("采样表过期分区回收失败 error={}", ex.getMessage(), ex);
        }
    }

    // 供给未来 [current+1, current+2] 两个月分区（缺失才建，幂等）；current 可注入便于测试
    void ensureFuturePartitions(YearMonth current) {
        for (int i = 1; i <= 2; i++) {
            YearMonth target = current.plusMonths(i);
            String partitionName = partitionName(target);
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.PARTITIONS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND PARTITION_NAME = ?",
                    Integer.class, TABLE_NAME, partitionName);
            if (exists != null && exists > 0) {
                continue;
            }
            String boundary = target.plusMonths(1).atDay(1).toString();
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " ADD PARTITION (PARTITION "
                    + partitionName + " VALUES LESS THAN ('" + boundary + "'))");
            log.info("采样表分区供给完成 partition={} boundary<{}", partitionName, boundary);
        }
    }

    // 整月过期分区回收：分区上界 <= now - 保留天数 才 DROP（月内残余行级删除走保留策略任务）
    void dropExpiredPartitions(LocalDate today) {
        int retentionDays = resolveRetentionDays();
        LocalDate cutoff = today.minusDays(retentionDays);
        List<Map<String, Object>> partitions = jdbcTemplate.queryForList(
                "SELECT PARTITION_NAME, PARTITION_DESCRIPTION FROM information_schema.PARTITIONS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND PARTITION_NAME IS NOT NULL",
                TABLE_NAME);
        for (Map<String, Object> partition : partitions) {
            String name = String.valueOf(partition.get("PARTITION_NAME"));
            String boundary = String.valueOf(partition.get("PARTITION_DESCRIPTION"));
            LocalDate boundaryDate;
            try {
                boundaryDate = LocalDate.parse(boundary);
            } catch (Exception ex) {
                continue;
            }
            if (!boundaryDate.isBefore(cutoff) && !boundaryDate.isEqual(cutoff)) {
                continue;
            }
            jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " DROP PARTITION " + name);
            log.info("采样表过期分区回收完成 partition={} boundary={} retentionDays={}",
                    name, boundary, retentionDays);
        }
    }

    // 全局保留策略优先，缺省走配置兜底
    private int resolveRetentionDays() {
        try {
            MetricRetentionPolicy global = metricRetentionPolicyService.getGlobalPolicyOrThrow();
            if (global != null && global.getRetentionDays() != null) {
                return global.getRetentionDays();
            }
        } catch (Exception ex) {
            log.warn("读取全局保留策略失败，使用默认值 error={}", ex.getMessage());
        }
        return retentionDefaultDays;
    }

    private void triggerSupplyAlarm(Exception cause) {
        try {
            AlarmTriggerContext context = AlarmTriggerContext.builder()
                    .deviceId(null)
                    .pointId(null)
                    .alarmType(ALARM_TYPE_PARTITION_SUPPLY)
                    .alarmLevel(2)
                    .alarmMessage("采样表分区供给失败：未来月份分区缺失且自动补建出错（" + cause.getMessage()
                            + "），跨月写入将失败，请检查数据库权限")
                    .cloudRoomId(null)
                    .build();
            var result = alarmEventService.trigger(context);
            if (result.isNotifiable()) {
                log.warn("分区供给失败告警已触发 alarmEventId={}", result.getAlarmEvent().getId());
            }
        } catch (Exception ex) {
            log.error("分区供给失败告警触发异常 error={}", ex.getMessage(), ex);
        }
    }

    static String partitionName(YearMonth month) {
        return "p" + String.format("%04d%02d", month.getYear(), month.getMonthValue());
    }
}
