-- ============================================================
-- V21: 采样数据月分区 + 小时聚合表（阶段八·S8-4）
--   1. metric_sample 主键调整为 (id, sample_time)——MySQL 分区键必须在
--      唯一键内；AUTO_INCREMENT 列在组合主键首列，语义不变；
--   2. 按 sample_time 做 RANGE COLUMNS 月分区：不建 MAXVALUE 兜底分区
--      （有它则无法直接 ADD PARTITION），跨月写入由每日分区供给任务保证；
--      供给失败以告警可见（PARTITION_SUPPLY_FAILED）；
--   3. 新增 metric_sample_hourly 小时聚合表并回填存量——趋势端点 >48h
--      跨度改走聚合表（趋势路由），聚合任务每小时整点后 5 分钟跑最近
--      2 个完整小时（ON DUPLICATE KEY 幂等，覆盖 1h 内迟到重发样本）；
--   4. TTL 混合清理：整月过期分区由分区任务 DROP（精确快），月内残余
--      行级删除继续走指标保留策略任务。
--   注意：ALTER ... PARTITION BY 对存量表是全表重建（COPY），本迁移
--   在低峰执行；预期重建窗口 = 存量行数 / 磁盘顺序写速率（百万级分钟量级）。
-- ============================================================

-- ==================== 1. 主键调整 ====================
ALTER TABLE metric_sample DROP PRIMARY KEY, ADD PRIMARY KEY (id, sample_time);

-- ==================== 2. 月分区 ====================
ALTER TABLE metric_sample PARTITION BY RANGE COLUMNS (sample_time) (
    PARTITION p202608 VALUES LESS THAN ('2026-09-01'),
    PARTITION p202609 VALUES LESS THAN ('2026-10-01'),
    PARTITION p202610 VALUES LESS THAN ('2026-11-01'),
    PARTITION p202611 VALUES LESS THAN ('2026-12-01'),
    PARTITION p202612 VALUES LESS THAN ('2027-01-01'),
    PARTITION p202701 VALUES LESS THAN ('2027-02-01'),
    PARTITION p202702 VALUES LESS THAN ('2027-03-01')
);

-- ==================== 3. 小时聚合表 ====================
CREATE TABLE metric_sample_hourly (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '聚合行ID（主键）',
    device_id    INT    NOT NULL COMMENT '设备ID',
    point_id     BIGINT NOT NULL COMMENT '指标ID',
    bucket_start DATETIME NOT NULL COMMENT '整点桶起始时间',
    min_value    DECIMAL(18,4) NOT NULL COMMENT '桶内最小值',
    max_value    DECIMAL(18,4) NOT NULL COMMENT '桶内最大值',
    avg_value    DECIMAL(18,4) NOT NULL COMMENT '桶内均值',
    cnt          BIGINT NOT NULL COMMENT '桶内采样条数（加权均值的权重）',
    create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_metric_hourly_device_point_bucket (device_id, point_id, bucket_start),
    KEY idx_metric_hourly_bucket (bucket_start)
) COMMENT '指标小时聚合表（长跨度趋势降采样数据源）';

-- ==================== 4. 存量回填 ====================
INSERT INTO metric_sample_hourly (device_id, point_id, bucket_start, min_value, max_value, avg_value, cnt)
SELECT device_id, point_id,
       DATE_FORMAT(sample_time, '%Y-%m-%d %H:00:00'),
       MIN(sample_value), MAX(sample_value), AVG(sample_value), COUNT(*)
FROM metric_sample
GROUP BY device_id, point_id, DATE_FORMAT(sample_time, '%Y-%m-%d %H:00:00');
