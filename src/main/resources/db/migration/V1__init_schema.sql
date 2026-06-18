-- ============================================================
-- Flyway V1: 初始化物联网设备管理数据库表结构
-- 说明：使用 CREATE TABLE IF NOT EXISTS，支持在已有数据库上幂等执行
-- 注意：Flyway 已在 spring.datasource.url 中指定目标数据库，脚本中不再包含 USE 语句
-- ============================================================

-- ==================== 1. 设备类型表 ====================
CREATE TABLE IF NOT EXISTS device_types (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '类型ID（主键，自动增长）',
    type_name VARCHAR(50) NOT NULL COMMENT '类型名称',
    type_code VARCHAR(50) NOT NULL COMMENT '类型编码（可用于程序内标识）',
    description VARCHAR(200) COMMENT '类型描述',
    alert_rule_config JSON COMMENT '告警规则配置（JSON格式，支持分级阈值，作为默认模板）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_type_name (type_name),
    UNIQUE INDEX uk_type_code (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备类型表';

-- ==================== 2. 设备信息表 ====================
CREATE TABLE IF NOT EXISTS devices (
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
    CONSTRAINT fk_device_type FOREIGN KEY (type_id) REFERENCES device_types(id),
    INDEX idx_devices_device_name (device_name),
    INDEX idx_devices_status (status),
    INDEX idx_devices_type_id (type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备信息表';

-- ==================== 3. 设备数据表 ====================
CREATE TABLE IF NOT EXISTS device_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '数据ID（主键，使用 BIGINT 以存储海量数据）',
    device_id INT NOT NULL COMMENT '设备ID（必填）',
    metric VARCHAR(50) DEFAULT NULL COMMENT '数据指标标识（如 temperature、humidity，用于规则路由）',
    data_value DECIMAL(10,2) COMMENT '数据值（保留两位小数）',
    collect_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '采集时间（默认当前时间）',
    CONSTRAINT fk_data_device FOREIGN KEY (device_id) REFERENCES devices(id),
    INDEX idx_device_data_device_collect (device_id, collect_time),
    INDEX idx_device_metric_collect (device_id, metric, collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备数据表';

-- ==================== 4. 告警记录表 ====================
CREATE TABLE IF NOT EXISTS alerts (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '告警ID（主键）',
    device_id INT NOT NULL COMMENT '设备ID（必填，关联到 devices 表）',
    alert_type VARCHAR(30) COMMENT '告警类型（如“高温告警”、“离线告警”）',
    alert_level TINYINT DEFAULT 1 COMMENT '告警级别：1一般 2严重 3紧急',
    alert_message VARCHAR(500) COMMENT '告警详细描述',
    is_handled TINYINT DEFAULT 0 COMMENT '是否处理：0未处理 1已处理',
    handle_time DATETIME COMMENT '处理时间',
    handle_user VARCHAR(50) COMMENT '处理人（用户名或工号）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '告警产生时间',
    CONSTRAINT fk_alert_device FOREIGN KEY (device_id) REFERENCES devices(id),
    INDEX idx_alerts_device_type_handled (device_id, alert_type, is_handled),
    INDEX idx_alerts_handled_time (is_handled, create_time),
    UNIQUE KEY uk_device_type_active (device_id, alert_type, is_handled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录表';

-- ==================== 5. 指标字典表 ====================
CREATE TABLE IF NOT EXISTS dict_metric (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    metric_key VARCHAR(50) NOT NULL COMMENT '指标标识(如 temperature)，唯一键，关联 device_data.metric',
    metric_name VARCHAR(50) NOT NULL COMMENT '指标中文名(如 温度)，用于前端展示和告警文案',
    metric_unit VARCHAR(20) DEFAULT NULL COMMENT '数据单位(如 ℃、%)，用于前端展示',
    description VARCHAR(200) DEFAULT NULL COMMENT '指标描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_metric_key (metric_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标字典配置表';

-- ==================== 6. 用户表 ====================
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名（登录账号，4-20位字母数字下划线）',
    password VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密存储，原文明文8-32位）',
    nickname VARCHAR(50) DEFAULT NULL COMMENT '昵称（展示用）',
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER' COMMENT '角色标识(ADMIN:超管, OPERATOR:运维, VIEWER:普通)',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态 (0正常 1停用 2剔除)',
    login_fail_count INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    lock_time DATETIME DEFAULT NULL COMMENT '账号锁定截止时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

-- ==================== 7. 安全审计日志表 ====================
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT DEFAULT NULL COMMENT '操作人ID',
    username VARCHAR(50) DEFAULT NULL COMMENT '操作人账号',
    operation VARCHAR(50) NOT NULL COMMENT '操作类型(如：LOGIN_SUCCESS, LOGIN_FAIL, CREATE_USER)',
    ip VARCHAR(50) DEFAULT NULL COMMENT '操作IP地址',
    detail VARCHAR(500) DEFAULT NULL COMMENT '操作详情(如密码错误1次)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全审计日志表';
