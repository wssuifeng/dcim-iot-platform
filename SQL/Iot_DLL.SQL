-- ============================================================
-- 文件名：iot_device_db_init.sql
-- 功能描述：物联网设备管理数据库初始化脚本
--   1. 创建数据库并设置字符集（utf8mb4 支持表情符号等多字节字符）
--   2. 设备类型表（device_types）：保存设备分类及默认告警配置
--   3. 设备信息表（devices）：保存每台设备的详细信息，支持独立告警配置
--   4. 设备数据表（device_data）：保存设备上报的时序数据，引用设备
--   5. 告警记录表（alerts）：保存告警信息，引用设备
-- 说明：脚本可重复执行，每次会先删除同名的表再重建。
-- ============================================================

-- ==================== 创建数据库 ====================
-- 如果数据库不存在则创建，指定字符集和排序规则
CREATE DATABASE IF NOT EXISTS iot_device_db
    DEFAULT CHARSET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- 切换到该数据库，后续操作都在此库中进行
USE iot_device_db;

-- ==================== 1. 设备类型表 ====================
-- 作用：存储设备的分类信息及其告警规则配置（作为该类型下所有设备的默认配置）。
DROP TABLE IF EXISTS device_types;
CREATE TABLE device_types (
                              id INT PRIMARY KEY AUTO_INCREMENT COMMENT '类型ID（主键，自动增长）',
                              type_name VARCHAR(50) NOT NULL COMMENT '类型名称',
                              type_code VARCHAR(50) NOT NULL COMMENT '类型编码（可用于程序内标识）',
                              description VARCHAR(200) COMMENT '类型描述',
                              alert_rule_config JSON COMMENT '告警规则配置（JSON格式，支持分级阈值，作为默认模板）',
                              create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引优化：明确命名唯一索引
                              UNIQUE INDEX uk_type_name (type_name),
                              UNIQUE INDEX uk_type_code (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备类型表';

-- ==================== 2. 设备信息表 ====================
-- 作用：存储每一台具体设备的信息。
--       通过 type_id 与设备类型表关联；支持通过自身 alert_rule_config 覆盖类型的默认配置。
DROP TABLE IF EXISTS devices;
CREATE TABLE devices (
                         id INT PRIMARY KEY AUTO_INCREMENT COMMENT '设备ID（主键）',
                         device_name VARCHAR(50) NOT NULL COMMENT '设备名称',
                         device_code VARCHAR(30) UNIQUE COMMENT '设备编码（唯一标识，如设备SN）',
                         type_id INT COMMENT '类型ID（关联 device_types 表）',
                         alert_rule_config JSON COMMENT '设备独立告警配置（若非空，则优先级高于设备类型配置）',
                         status TINYINT DEFAULT 0 COMMENT '状态：0离线 1在线 2故障',
                         location VARCHAR(100) COMMENT '设备位置',
                         ip_address VARCHAR(50) COMMENT 'IP地址',
                         last_online_time DATETIME COMMENT '最后在线时间',
                         create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 【外键约束】确保 type_id 在 device_types 表中存在，防止输入无效的类型ID
                         CONSTRAINT fk_device_type FOREIGN KEY (type_id) REFERENCES device_types(id),

    -- 【索引】提升常用查询性能
                         INDEX idx_devices_device_name (device_name),
                         INDEX idx_devices_status (status),
                         INDEX idx_devices_type_id (type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备信息表';

-- ==================== 3. 设备数据表 ====================
-- 作用：记录设备定时上报的业务数据（如温度值、湿度值），每条数据属于某个设备。
--       时间字段默认为插入时的当前时间，便于绘制历史曲线。
DROP TABLE IF EXISTS device_data;
CREATE TABLE device_data (
                             id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '数据ID（主键，使用 BIGINT 以存储海量数据）',
                             device_id INT NOT NULL COMMENT '设备ID（必填）',
                             data_value DECIMAL(10,2) COMMENT '数据值（保留两位小数）',
                             data_unit VARCHAR(20) COMMENT '数据单位（如 ℃、%）',
                             collect_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '采集时间（默认当前时间）',

    -- 【外键约束】确保 device_id 是 devices 表中存在的设备，防止幽灵数据
                             CONSTRAINT fk_data_device FOREIGN KEY (device_id) REFERENCES devices(id),

    -- 【索引优化】
    -- 联合索引：物联网查询最常用的场景是"查某台设备在某段时间内的数据"，联合索引性能远超单列索引
                             INDEX idx_device_data_device_collect (device_id, collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备数据表';
-- 1. 删除旧的数据单位字段
ALTER TABLE device_data DROP COLUMN data_unit;

-- 2. 添加新的指标标识字段 (建议放在 device_id 之后)
ALTER TABLE device_data ADD COLUMN metric VARCHAR(50) DEFAULT NULL COMMENT '数据指标标识（如 temperature、humidity，用于规则路由）' AFTER device_id;

-- 3. (可选但强烈建议) 添加联合索引，优化基于设备和指标的查询性能
ALTER TABLE device_data ADD INDEX idx_device_metric_collect (device_id, metric, collect_time);

-- ==================== 4. 告警记录表 ====================
-- 作用：存储设备触发的告警信息，包含告警级别、是否已处理、处理人等信息。
--       用于运维人员跟踪和闭环告警。
DROP TABLE IF EXISTS alerts;
CREATE TABLE alerts (
                        id INT PRIMARY KEY AUTO_INCREMENT COMMENT '告警ID（主键）',
                        device_id INT NOT NULL COMMENT '设备ID（必填，关联到 devices 表）',
                        alert_type VARCHAR(30) COMMENT '告警类型（如“高温告警”、“离线告警”）',
                        alert_level TINYINT DEFAULT 1 COMMENT '告警级别：1一般 2严重 3紧急',
                        alert_message VARCHAR(500) COMMENT '告警详细描述',
                        is_handled TINYINT DEFAULT 0 COMMENT '是否处理：0未处理 1已处理',
                        handle_time DATETIME COMMENT '处理时间',
                        handle_user VARCHAR(50) COMMENT '处理人（用户名或工号）',
                        create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '告警产生时间',

    -- 【外键约束】保证告警记录的 device_id 引用的是真实设备
                        CONSTRAINT fk_alert_device FOREIGN KEY (device_id) REFERENCES devices(id),

    -- 【索引优化】
    -- 1. 联合索引：完美支撑 AlertRuleEngine 中的防重逻辑 (device_id + alert_type + is_handled)
                        INDEX idx_alerts_device_type_handled (device_id, alert_type, is_handled),
    -- 2. 联合索引：支撑运维告警列表页面最常见的查询：按时间倒序查未处理的告警
                        INDEX idx_alerts_handled_time (is_handled, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录表';
ALTER TABLE alerts
    ADD UNIQUE KEY uk_device_type_active (device_id, alert_type, is_handled);
-- ==================== 5. 指标字典表 ====================
-- 指标字典表 (数据元数据配置)
DROP TABLE IF EXISTS dict_metric;
CREATE TABLE dict_metric (
                             id INT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
                             metric_key VARCHAR(50) NOT NULL COMMENT '指标标识(如 temperature)，唯一键，关联 device_data.metric',
                             metric_name VARCHAR(50) NOT NULL COMMENT '指标中文名(如 温度)，用于前端展示和告警文案',
                             metric_unit VARCHAR(20) DEFAULT NULL COMMENT '数据单位(如 ℃、%)，用于前端展示',
                             description VARCHAR(200) DEFAULT NULL COMMENT '指标描述',
                             created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                             updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                             UNIQUE KEY uk_metric_key (metric_key) -- 保证指标标识全局唯一
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标字典配置表';

-- 初始化一些基础数据
INSERT INTO dict_metric (metric_key, metric_name, metric_unit) VALUES
                                                                   ('temperature', '温度', '℃'),
                                                                   ('humidity', '湿度', '%'),
                                                                   ('voltage', '电压', 'V');


-- ==================== 6. 用户表 ====================
DROP TABLE IF EXISTS sys_user;
CREATE TABLE `sys_user` (
                            `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                            `username` VARCHAR(50) NOT NULL COMMENT '用户名（登录账号，4-20位字母数字下划线）',
                            `password` VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密存储，原文明文8-32位）',
                            `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称（展示用）',
                            `role` VARCHAR(20) NOT NULL DEFAULT 'VIEWER' COMMENT '角色标识(ADMIN:超管, OPERATOR:运维, VIEWER:普通)',
                            `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态 (0正常 1停用 2剔除)',

    -- 登录安全控制字段 (满足 REQ-SEC-FR-001 业务规则1)
                            `login_fail_count` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
                            `lock_time` DATETIME DEFAULT NULL COMMENT '账号锁定截止时间',

    -- 基础审计字段
                            `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';
CREATE TABLE `sys_audit_log` (
                                 `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                 `user_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
                                 `username` VARCHAR(50) DEFAULT NULL COMMENT '操作人账号',
                                 `operation` VARCHAR(50) NOT NULL COMMENT '操作类型(如：LOGIN_SUCCESS, LOGIN_FAIL, CREATE_USER)',
                                 `ip` VARCHAR(50) DEFAULT NULL COMMENT '操作IP地址',
                                 `detail` VARCHAR(500) DEFAULT NULL COMMENT '操作详情(如密码错误1次)',
                                 `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
                                 PRIMARY KEY (`id`),
                                 KEY `idx_user_id` (`user_id`),
                                 KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全审计日志表';





-- ==================== 验证 ====================
SHOW DATABASES;
SHOW TABLES;

#添加逻辑删除
ALTER TABLE device_types ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除';
ALTER TABLE devices ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除';
ALTER TABLE device_data ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除';
ALTER TABLE alerts ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除';

# 回滚逻辑删除
ALTER TABLE device_types DROP COLUMN deleted;
ALTER TABLE devices      DROP COLUMN deleted;
ALTER TABLE device_data  DROP COLUMN deleted;
ALTER TABLE alerts       DROP COLUMN deleted;

# 数据查看
select * from device_types;
select * from devices;
select * from device_data;
select * from alerts;
select * from dict_metric;

SHOW CREATE TABLE device_types;
SHOW CREATE TABLE devices;
SHOW CREATE TABLE device_data;
SHOW CREATE TABLE alerts;

SELECT *
FROM devices
WHERE last_online_time is null or TIMESTAMPDIFF(MINUTE, last_online_time, NOW()) > 5;