package com.example.platform.task;

import com.example.platform.ingest.mapper.IngestDedupMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/*
 * 类功能:上报幂等去重键清理任务，按保留窗口删除过期 dedup 键
 *
 * 幂等只需覆盖网关重发窗口（QoS1 重投与断网补传），超出窗口的重复
 * 键不再有拦截价值，删除避免表无限增长。
 * */
@Slf4j
@Component
public class IngestDedupCleanupTask {

    private final IngestDedupMapper ingestDedupMapper;

    // 幂等保留窗口（天）：网关重发/补传场景足够，默认 7 天
    @Value("${task.cleanup-ingest-dedup-retention-days:7}")
    private int retentionDays;

    public IngestDedupCleanupTask(IngestDedupMapper ingestDedupMapper) {
        this.ingestDedupMapper = ingestDedupMapper;
    }

    /*
     * 方法功能:每天凌晨清理超出保留窗口的上报幂等键
     * 参数:无
     * 返回数据:无
     * */
    @Scheduled(cron = "${task.cleanup-ingest-dedup-cron:0 30 2 * * ?}")
    public void cleanupExpiredDedupKeys() {
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        int deleted = ingestDedupMapper.deleteBefore(before);
        if (deleted > 0) {
            log.info("上报幂等键清理完成：保留{}天 删除{}行", retentionDays, deleted);
        }
    }
}
