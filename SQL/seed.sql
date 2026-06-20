-- ============================================================
-- 文件：SQL/seed.sql
-- 功能：开发环境与系统基础数据初始化
-- 说明：
--   1. 本文件仅包含 INSERT 语句，不含 DDL；表结构由 Flyway 迁移脚本统一管理
--   2. 执行前请确保已清空业务表或确认不存在主键/唯一键冲突
--   3. sys_user 密码使用 BCrypt 加密，明文密码见注释
-- ============================================================

USE iot_device_db;

-- ==================== 1. 指标字典 ====================
INSERT INTO dict_metric (metric_key, metric_name, metric_unit, description) VALUES
    ('temperature', '温度', '℃', '环境温度采集指标'),
    ('humidity', '湿度', '%', '环境湿度采集指标'),
    ('smoke', '烟雾浓度', 'ppm', '烟雾检测指标'),
    ('voltage', '电压', 'V', '设备电压采集指标');

-- ==================== 2. 设备类型 ====================
INSERT INTO device_types (type_name, type_code, description) VALUES
    ('温度传感器', 'TEMP_SENSOR', '环境温度监测'),
    ('湿度传感器', 'HUMIDITY_SENSOR', '环境湿度监测'),
    ('烟雾探测器', 'SMOKE_DETECTOR', '火灾烟雾检测'),
    ('门禁设备', 'DOOR_ACCESS', '门禁出入管理');

-- ==================== 3. 设备信息 ====================
INSERT INTO devices (device_name, device_code, type_id, status, location, ip_address) VALUES
    ('1号楼温度传感器', 'TEMP-001', 1, 1, '1号楼3层东侧', '192.168.1.101'),
    ('2号楼温度传感器', 'TEMP-002', 1, 1, '2号楼1层大厅', '192.168.1.102'),
    ('1号楼湿度传感器', 'HUMIDITY-001', 2, 1, '1号楼3层东侧', '192.168.1.103'),
    ('2号楼湿度传感器', 'HUMIDITY-002', 2, 0, '2号楼1层西侧', '192.168.1.104'),
    ('3号楼烟雾探测器', 'SMOKE-001', 3, 1, '3号楼2层西侧', '192.168.1.105'),
    ('仓库烟雾探测器', 'SMOKE-002', 3, 2, '仓库东南角', '192.168.1.106'),
    ('大门门禁', 'DOOR-001', 4, 1, '主入口大门', '192.168.1.107'),
    ('后门门禁', 'DOOR-002', 4, 1, '后勤通道后门', '192.168.1.108');

-- ==================== 4. 系统用户（角色） ====================
-- 明文密码（大小写敏感）：Admin@123 / Operator@123 / Viewer@123
-- 如登录失败，可使用下方 UPDATE 语句直接重置为明文（生产环境请删除此段）
INSERT INTO sys_user (username, password, nickname, role, status) VALUES
    ('admin', '$2a$10$RpmHhWVdt7tAjKPU5VtM1uBHK5d99h9Y5S/KjJPk6PEfYzvwsABni', '系统管理员', 'ADMIN', 0),
    ('operator', '$2a$10$mgRq4VDaCzZLjiGVuGSoYO9woY1wQ7hsD0j0d7iANngfV.H41CvPm', '运维人员', 'OPERATOR', 0),
    ('viewer', '$2a$10$k0e1NN/2EbzdADpGh2GecOT0NYhJNy1J7beYFRLESdU2.Qk0VnnFO', '只读用户', 'VIEWER', 0);

-- 重置密码为明文（仅供开发调试，生产环境请删除）
-- UPDATE sys_user SET password = '$2a$10$RpmHhWVdt7tAjKPU5VtM1uBHK5d99h9Y5S/KjJPk6PEfYzvwsABni' WHERE username = 'admin';
-- UPDATE sys_user SET password = '$2a$10$mgRq4VDaCzZLjiGVuGSoYO9woY1wQ7hsD0j0d7iANngfV.H41CvPm' WHERE username = 'operator';
-- UPDATE sys_user SET password = '$2a$10$k0e1NN/2EbzdADpGh2GecOT0NYhJNy1J7beYFRLESdU2.Qk0VnnFO' WHERE username = 'viewer';

-- ==================== 5. 设备数据（最近 7 天示例） ====================
-- 说明：使用存储过程批量生成 100 条示例数据，执行后自动删除存储过程
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS seed_device_data()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE v_device_id INT;
    DECLARE v_metric VARCHAR(50);
    DECLARE v_value DECIMAL(10, 2);

    WHILE i <= 100 DO
        SET v_device_id = 1 + FLOOR(RAND() * 8);

        -- 根据设备类型选择指标与数值范围
        SET v_metric = CASE
            WHEN v_device_id IN (1, 2) THEN 'temperature'
            WHEN v_device_id IN (3, 4) THEN 'humidity'
            WHEN v_device_id IN (5, 6) THEN 'smoke'
            ELSE 'voltage'
        END;

        SET v_value = CASE v_metric
            WHEN 'temperature' THEN ROUND(20 + RAND() * 30, 2)
            WHEN 'humidity' THEN ROUND(30 + RAND() * 50, 2)
            WHEN 'smoke' THEN ROUND(RAND() * 100, 2)
            ELSE ROUND(11 + RAND() * 2, 2)
        END;

        INSERT INTO device_data (device_id, metric, data_value, collect_time)
        VALUES (v_device_id, v_metric, v_value, DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 10080) MINUTE));

        SET i = i + 1;
    END WHILE;
END //
DELIMITER ;

CALL seed_device_data();
DROP PROCEDURE IF EXISTS seed_device_data;

-- ==================== 6. 告警记录 ====================
INSERT INTO alerts (device_id, alert_type, alert_level, alert_message, is_handled, handle_user, handle_time) VALUES
    (6, '设备故障', 3, '仓库烟雾探测器通信中断，请检查设备', 0, NULL, NULL),
    (4, '数据异常', 2, '2号楼湿度传感器数据波动异常', 1, 'operator', NOW()),
    (1, '温度告警', 2, '1号楼温度传感器温度超过阈值', 0, NULL, NULL),
    (5, '低电量', 1, '3号楼烟雾探测器电量低于20%', 0, NULL, NULL),
    (2, '离线告警', 1, '2号楼温度传感器已离线超过1小时', 1, 'operator', NOW());
