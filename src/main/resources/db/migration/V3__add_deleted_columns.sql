-- ============================================================
-- Flyway V3: 为业务表补加逻辑删除字段 deleted
-- 说明：使用存储过程实现幂等 ADD COLUMN，避免字段已存在时报错
-- 注意：Flyway 已在 spring.datasource.url 中指定目标数据库，脚本中不再包含 USE 语句
-- ============================================================

DELIMITER $$

CREATE PROCEDURE IF NOT EXISTS add_deleted_column_if_not_exists(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64)
)
BEGIN
    DECLARE v_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = p_column_name;

    IF v_count = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE ', p_table_name,
            ' ADD COLUMN ', p_column_name,
            ' TINYINT DEFAULT 0 COMMENT ''逻辑删除：0未删除 1已删除'''
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

-- 为 5 张业务表幂等添加 deleted 字段
CALL add_deleted_column_if_not_exists('device_types', 'deleted');
CALL add_deleted_column_if_not_exists('devices', 'deleted');
CALL add_deleted_column_if_not_exists('device_data', 'deleted');
CALL add_deleted_column_if_not_exists('alerts', 'deleted');
CALL add_deleted_column_if_not_exists('dict_metric', 'deleted');

DROP PROCEDURE IF EXISTS add_deleted_column_if_not_exists;
