-- ============================================================
-- Flyway V2: 添加业务查询组合索引
-- 说明：MySQL 不支持 CREATE INDEX IF NOT EXISTS，Flyway 保证每个版本只执行一次
-- 注意：Flyway 已在 spring.datasource.url 中指定目标数据库，脚本中不再包含 USE 语句
-- ============================================================

-- 支撑按设备+处理状态查询告警
CREATE INDEX idx_alerts_device_handled ON alerts(device_id, is_handled);

-- 支撑按告警级别+时间查询告警
CREATE INDEX idx_alerts_level_time ON alerts(alert_level, create_time);
