-- ============================================================
-- Flyway V4: 告警状态机与站内通知模型
--
-- 契约依据：共享契约 03-共享契约.md 告警状态机 / 通知章节
--   AlarmEvent.state 为权威状态（TRIGGERED→ACKED→HANDLING→RECOVERED→CLOSED）
--   v1.0 alerts 表保留不变，仅作兼容/派生展示，不与新表建立强制外键关联
--   NotificationLog 仍是发送日志；已读状态由新增 in_app_notification 承担
-- ============================================================

-- ==================== 1. 告警事件表（权威状态机） ====================
CREATE TABLE IF NOT EXISTS alarm_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '告警事件ID（主键）',
    device_id INT NOT NULL COMMENT '设备ID（关联 devices）',
    point_id BIGINT DEFAULT NULL COMMENT '触发点位ID（关联 point_definition，可为空）',
    alarm_type VARCHAR(50) NOT NULL COMMENT '告警类型',
    alarm_level TINYINT NOT NULL DEFAULT 3 COMMENT '告警级别：1-紧急 2-严重 3-一般 4-警告 5-提示',
    alarm_message VARCHAR(500) COMMENT '告警详细描述',
    state VARCHAR(20) NOT NULL DEFAULT 'TRIGGERED' COMMENT '权威状态：TRIGGERED/ACKED/HANDLING/RECOVERED/CLOSED',
    organization_id BIGINT DEFAULT NULL COMMENT '所属组织ID（数据范围校验依据）',
    cloud_room_id BIGINT DEFAULT NULL COMMENT '所属机房ID（数据范围校验依据）',
    triggered_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
    recovered_time DATETIME DEFAULT NULL COMMENT '恢复时间',
    closed_time DATETIME DEFAULT NULL COMMENT '关闭时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    CONSTRAINT fk_alarm_event_device FOREIGN KEY (device_id) REFERENCES devices(id),
    CONSTRAINT fk_alarm_event_point FOREIGN KEY (point_id) REFERENCES point_definition(id),
    INDEX idx_alarm_event_device_state (device_id, state),
    INDEX idx_alarm_event_level_time (alarm_level, triggered_time),
    INDEX idx_alarm_event_org (organization_id),
    INDEX idx_alarm_event_room (cloud_room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警事件表（权威状态机）';

-- ==================== 2. 告警状态流转记录表 ====================
CREATE TABLE IF NOT EXISTS alarm_state_transition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '流转记录ID（主键）',
    alarm_event_id BIGINT NOT NULL COMMENT '告警事件ID（关联 alarm_event）',
    from_state VARCHAR(20) DEFAULT NULL COMMENT '流转前状态（首次触发为空）',
    to_state VARCHAR(20) NOT NULL COMMENT '流转后状态',
    operator_user_id BIGINT DEFAULT NULL COMMENT '操作人用户ID（系统自动流转可为空）',
    remark VARCHAR(500) DEFAULT NULL COMMENT '流转备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流转时间',
    CONSTRAINT fk_transition_event FOREIGN KEY (alarm_event_id) REFERENCES alarm_event(id),
    INDEX idx_transition_event (alarm_event_id),
    INDEX idx_transition_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警状态流转记录表';

-- ==================== 3. 告警处理记录表 ====================
CREATE TABLE IF NOT EXISTS alarm_handling_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '处理记录ID（主键）',
    alarm_event_id BIGINT NOT NULL COMMENT '告警事件ID（关联 alarm_event）',
    handler_user_id BIGINT DEFAULT NULL COMMENT '处理人用户ID',
    handling_action VARCHAR(50) NOT NULL COMMENT '处理动作（如：ACK、ASSIGN、RESOLVE、CLOSE）',
    handling_note VARCHAR(500) DEFAULT NULL COMMENT '处理说明',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '处理时间',
    CONSTRAINT fk_handling_event FOREIGN KEY (alarm_event_id) REFERENCES alarm_event(id),
    INDEX idx_handling_event (alarm_event_id),
    INDEX idx_handling_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警处理记录表';

-- ==================== 4. 站内通知表（已读状态，区别于 notification_log 发送日志） ====================
CREATE TABLE IF NOT EXISTS in_app_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '通知ID（主键）',
    recipient_user_id BIGINT NOT NULL COMMENT '接收用户ID（关联 sys_user）',
    title VARCHAR(100) NOT NULL COMMENT '通知标题',
    content VARCHAR(500) COMMENT '通知内容',
    source_type VARCHAR(30) DEFAULT NULL COMMENT '来源类型（如 ALARM_EVENT）',
    source_id BIGINT DEFAULT NULL COMMENT '来源记录ID（如 alarm_event.id）',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '已读状态：0-未读 1-已读',
    read_time DATETIME DEFAULT NULL COMMENT '已读时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_user_id) REFERENCES sys_user(id),
    INDEX idx_notification_recipient_read (recipient_user_id, is_read),
    INDEX idx_notification_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知表（已读状态）';
