-- ============================================================
-- Flyway V5: 修正 dict_metric 列命名 + alerts 表注释
-- ============================================================

-- 1. 重命名 dict_metric 表字段 (MySQL 8.0+ 原生 RENAME COLUMN 语法)
ALTER TABLE dict_metric 
    RENAME COLUMN created_at TO create_time,
    RENAME COLUMN updated_at TO update_time;

-- 2. 修正 alerts 表 alert_level 注释
-- 注意：MODIFY 会保留原有除注释外的其他属性，但如果不确定，建议写全类型定义
ALTER TABLE alerts MODIFY COLUMN alert_level TINYINT DEFAULT 1
    COMMENT '告警级别：1-紧急 2-严重 3-一般 4-警告 5-提示';
