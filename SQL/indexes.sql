-- ============================================================
-- 业务查询组合索引（与 Flyway V2 脚本保持一致）
-- 说明：本项目已启用 Flyway，DDL 变更是以 src/main/resources/db/migration 下脚本为准。
-- 本文件仅用于本地手动参考或向非 Flyway 环境同步索引。
-- ============================================================

USE iot_device_db;

-- 支撑按设备+处理状态查询告警
CREATE INDEX idx_alerts_device_handled ON alerts(device_id, is_handled);

-- 支撑按告警级别+时间查询告警
CREATE INDEX idx_alerts_level_time ON alerts(alert_level, create_time);
