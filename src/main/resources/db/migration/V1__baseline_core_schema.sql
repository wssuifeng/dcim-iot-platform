-- ============================================================
-- Flyway V1: 产品化基线 —— 核心表结构（二次基线重置）
--
-- 说明：
--   本文件是"数据上报与告警模块重构"（见 docs/2026-08-24-数据上报与告警模块重构设计.md）
--   的二次基线折叠，面向空库初始化，不兼容任何历史运行库（需 DROP 整库重建）。
--   相对上一版基线的结构变化：
--     - 移除 v1.0 遗留表：alerts / device_data / dict_metric / notification_log
--       （权威告警为 alarm_event[V4]，采样为 metric_sample[V3]，指标字典为 point_definition[V3]，
--         发送日志为 alarm_notification_log[V4]）
--     - devices / device_types 不再含 alert_rule_config JSON 列（规则迁至 device_point_alarm_rule[V4]）
--     - sys_user 不含 organization_id 列（组织关系统一由 organization_membership[V7] 承担）
-- ============================================================

-- ==================== 1. 设备类型表 ====================
CREATE TABLE IF NOT EXISTS device_types (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '类型ID（主键，自动增长）',
    type_name VARCHAR(50) NOT NULL COMMENT '类型名称',
    type_code VARCHAR(50) NOT NULL COMMENT '类型编码（可用于程序内标识）',
    description VARCHAR(200) COMMENT '类型描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    UNIQUE INDEX uk_type_name (type_name),
    UNIQUE INDEX uk_type_code (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备类型表';

-- ==================== 2. 设备信息表 ====================
CREATE TABLE IF NOT EXISTS devices (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '设备ID（主键，全平台共享自增序列）',
    device_name VARCHAR(50) NOT NULL COMMENT '设备名称',
    device_code VARCHAR(30) UNIQUE COMMENT '设备编码（唯一标识，如设备SN）',
    type_id INT COMMENT '类型ID（关联 device_types 表）',
    status TINYINT DEFAULT 0 COMMENT '状态：0离线 1在线 2故障',
    location VARCHAR(100) COMMENT '设备位置（展示用文本，范围归属见 organization_id/cloud_room_id）',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    last_online_time DATETIME COMMENT '最后在线时间（上报刷新/离线检测依据）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    CONSTRAINT fk_device_type FOREIGN KEY (type_id) REFERENCES device_types(id),
    INDEX idx_devices_device_name (device_name),
    INDEX idx_devices_status (status),
    INDEX idx_devices_type_id (type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备信息表';

-- ==================== 3. 用户表 ====================
-- 角色取值：组织角色 ORG_ADMIN/OPERATOR/VIEWER；平台角色经 platform_role 列（V6）表达 PLATFORM_ADMIN。
-- 组织成员关系统一由 organization_membership（V7）承担，本表不保留组织归属列。
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名（登录账号，4-20位字母数字下划线）',
    password VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密存储，原文明文8-32位）',
    nickname VARCHAR(50) DEFAULT NULL COMMENT '昵称（展示用）',
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER' COMMENT '组织角色标识(ORG_ADMIN/OPERATOR/VIEWER，兼容旧ADMIN)',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态 (0正常 1停用 2剔除)',
    login_fail_count INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    lock_time DATETIME DEFAULT NULL COMMENT '账号锁定截止时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

-- ==================== 4. 安全审计日志表（基础字段，结构化扩展见 V5） ====================
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
