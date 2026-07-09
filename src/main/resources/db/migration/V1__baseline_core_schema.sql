-- ============================================================
-- Flyway V1: 产品化基线 —— 核心表结构（吸收 v1.0 历史 V1-V6 的最终状态）
--
-- 说明：
--   本文件是产品化迁移基线重置窗口内对 v1.0 历史 V1-V6 的一次性折叠合并，
--   面向空库初始化，不要求兼容已运行过 v1.0 V1-V6 的生产数据库。
--   折叠内容：
--     - v1.0 V1 建表（device_types/devices/device_data/alerts/dict_metric/sys_user/sys_audit_log）
--     - v1.0 V2 组合索引（alerts 按 device_id+is_handled、alert_level+create_time）
--     - v1.0 V3 逻辑删除字段 deleted（直接建表时加入，无需存储过程幂等补丁）
--     - v1.0 V4 无结构变更（仅数据库参数说明，不纳入迁移）
--     - v1.0 V5 dict_metric 列重命名为 create_time/update_time；alerts.alert_level 注释修正
--     - v1.0 V6 notification_log 表
--   Alert（v1.0 遗留告警表）保留结构不变，作为兼容表；
--   权威告警状态机（AlarmEvent 等）在 V4__alarm_and_notification_model.sql 中新增。
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
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
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
    location VARCHAR(100) COMMENT '设备位置（展示用文本，范围归属见 organization_id/cloud_room_id）',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    last_online_time DATETIME COMMENT '最后在线时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
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
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    CONSTRAINT fk_data_device FOREIGN KEY (device_id) REFERENCES devices(id),
    INDEX idx_device_data_device_collect (device_id, collect_time),
    INDEX idx_device_metric_collect (device_id, metric, collect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备数据表';

-- ==================== 4. 告警记录表（v1.0 遗留，保留结构不变，仅作兼容） ====================
CREATE TABLE IF NOT EXISTS alerts (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '告警ID（主键）',
    device_id INT NOT NULL COMMENT '设备ID（必填，关联到 devices 表）',
    alert_type VARCHAR(30) COMMENT '告警类型（如“高温告警”、“离线告警”）',
    alert_level TINYINT DEFAULT 1 COMMENT '告警级别：1-紧急 2-严重 3-一般 4-警告 5-提示',
    alert_message VARCHAR(500) COMMENT '告警详细描述',
    is_handled TINYINT DEFAULT 0 COMMENT '是否处理：0未处理 1已处理',
    handle_time DATETIME COMMENT '处理时间',
    handle_user VARCHAR(50) COMMENT '处理人（用户名或工号）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '告警产生时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    CONSTRAINT fk_alert_device FOREIGN KEY (device_id) REFERENCES devices(id),
    INDEX idx_alerts_device_type_handled (device_id, alert_type, is_handled),
    INDEX idx_alerts_handled_time (is_handled, create_time),
    INDEX idx_alerts_device_handled (device_id, is_handled),
    INDEX idx_alerts_level_time (alert_level, create_time),
    UNIQUE KEY uk_device_type_active (device_id, alert_type, is_handled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警记录表（v1.0兼容，权威状态机见 alarm_event）';

-- ==================== 5. 指标字典表 ====================
CREATE TABLE IF NOT EXISTS dict_metric (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    metric_key VARCHAR(50) NOT NULL COMMENT '指标标识(如 temperature)，唯一键，关联 device_data.metric',
    metric_name VARCHAR(50) NOT NULL COMMENT '指标中文名(如 温度)，用于前端展示和告警文案',
    metric_unit VARCHAR(20) DEFAULT NULL COMMENT '数据单位(如 ℃、%)，用于前端展示',
    description VARCHAR(200) DEFAULT NULL COMMENT '指标描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    UNIQUE KEY uk_metric_key (metric_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标字典配置表';

-- ==================== 6. 用户表 ====================
-- 角色取值范围：v1.0 兼容角色 ADMIN/OPERATOR/VIEWER；产品化新增 PLATFORM_ADMIN/ORG_ADMIN。
-- 列类型保持 VARCHAR，不做枚举约束，避免与 Java 侧校验规则（INFRA-CODEX-02负责）产生耦合冲突。
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名（登录账号，4-20位字母数字下划线）',
    password VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密存储，原文明文8-32位）',
    nickname VARCHAR(50) DEFAULT NULL COMMENT '昵称（展示用）',
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER' COMMENT '角色标识(PLATFORM_ADMIN/ORG_ADMIN/OPERATOR/VIEWER，兼容旧ADMIN)',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态 (0正常 1停用 2剔除)',
    login_fail_count INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    lock_time DATETIME DEFAULT NULL COMMENT '账号锁定截止时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

-- ==================== 7. 安全审计日志表（基础字段，结构化扩展见 V5） ====================
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

-- ==================== 8. 通知发送日志表（发送记录，非已读状态） ====================
CREATE TABLE IF NOT EXISTS notification_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    alert_id INT NOT NULL COMMENT '关联告警ID（v1.0 alerts 表，非 alarm_event）',
    notification_type VARCHAR(20) NOT NULL COMMENT '通知类型：EMAIL / WEBSOCKET',
    recipient VARCHAR(100) COMMENT '接收方（邮箱地址或用户ID）',
    content VARCHAR(500) COMMENT '通知内容',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '发送状态：0-待发送 1-成功 2-失败',
    error_message VARCHAR(500) COMMENT '失败原因',
    send_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_notification_alert (alert_id),
    INDEX idx_notification_type_status (notification_type, status),
    INDEX idx_notification_send_time (send_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知发送日志表（发送记录，已读状态见 in_app_notification）';
