package com.example.platform.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.MetricSampleMapper;
import com.example.platform.service.MetricRetentionPolicyService;
import com.example.platform.service.PointDefinitionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/*
 * 类功能:采样历史保留清理任务，按 metric_retention_policy（全局默认+点位覆盖，平台级统一 D08）
 *   分批删除过期采样，避免大表长事务锁竞争
 *
 * 【清理策略】逐点位解析生效保留天数（点位策略优先→全局默认→90兜底），
 *   DELETE ... LIMIT 循环批删至 0 行；metric_latest 与 alarm_event 不设 TTL（D22）。
 * */
@Slf4j
@Component
public class MetricSampleCleanupTask {

    private final MetricSampleMapper metricSampleMapper;
    private final PointDefinitionService pointDefinitionService;
    private final MetricRetentionPolicyService retentionPolicyService;

    @Value("${task.cleanup-batch-size:5000}")
    private int batchSize;

    public MetricSampleCleanupTask(MetricSampleMapper metricSampleMapper,
                                   PointDefinitionService pointDefinitionService,
                                   MetricRetentionPolicyService retentionPolicyService) {
        this.metricSampleMapper = metricSampleMapper;
        this.pointDefinitionService = pointDefinitionService;
        this.retentionPolicyService = retentionPolicyService;
    }

    /*
     * 方法功能:每天凌晨按保留策略清理过期的指标采样（逐点位分批删除）
     * 参数:无
     * 返回数据:无
     * */
    @Scheduled(cron = "${task.cleanup-metric-sample-cron:0 0 2 * * ?}")
    public void cleanupExpiredSamples() {
        LambdaQueryWrapper<PointDefinition> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(PointDefinition::getId);
        List<PointDefinition> points = pointDefinitionService.list(wrapper);
        if (points.isEmpty()) {
            return;
        }

        int totalDeleted = 0;
        for (PointDefinition point : points) {
            try {
                totalDeleted += cleanupPoint(point.getId());
            } catch (Exception ex) {
                log.warn("点位采样清理失败 pointId={} error={}", point.getId(), ex.getMessage());
            }
        }
        if (totalDeleted > 0) {
            log.info("采样保留清理完成：点位数={} 删除总行数={}", points.size(), totalDeleted);
        }
    }

    private int cleanupPoint(Long pointId) {
        int retentionDays = retentionPolicyService.resolveEffectiveRetentionDays(pointId);
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        int deleted = 0;
        int batch;
        do {
            batch = metricSampleMapper.deleteBatchBefore(pointId, before, batchSize);
            deleted += batch;
        } while (batch >= batchSize);
        if (deleted > 0) {
            log.info("点位采样清理 pointId={} retentionDays={} deleted={}", pointId, retentionDays, deleted);
        }
        return deleted;
    }
}
