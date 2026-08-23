-- ============================================================
-- Flyway V4: 告警状态机、点位告警规则与发送日志模型
--
-- 契约依据：共享契约 03-共享契约.md 告警状态机 / 通知章节
--   AlarmEvent.state 为权威状态（TRIGGERED→ACKED→HANDLING→RECOVERED→CLOSED）
--   v1.0 alerts 兼容表已在二次基线中移除，alarm_event 是唯一告警事实源
--   已读状态由 in_app_notification 承担；发送结果由 alarm_notification_log 承担
--
-- 二次基线新增（D03，见 docs/2026-08-24-数据上报与告警模块重构设计.md）：
--   - device_point_alarm_rule 点位告警规则表（方案A第4实体，替代 JSON 规则字段）
--   - alarm_notification_log 告警发送日志表（替代 v1.0 notification_log）
-- ============================================================

-- ==================== 1. 告警事件表（权威状态机） ====================
CREATE TABLE IF NOT EXISTS alarm_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '告警事件ID（主键）',
    device_id INT NOT NULL COMMENT '设备ID（关联 devices）',
    point_id BIGINT DEFAULT NULL COMMENT '触发点位ID（关联 point_definition，通用告警如离线可为空）',
    alarm_type VARCHAR(50) NOT NULL COMMENT '告警类型（METRIC阈值/OFFLINE离线/COMM通信异常）',
    alarm_level TINYINT NOT NULL DEFAULT 3 COMMENT '告警级别：1-紧急 2-严重 3-一般 4-警告 5-提示（数字小=严重）',
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
    INDEX idx_alarm_event_room (cloud_room_id),
    INDEX idx_alarm_event_dedup (device_id, point_id, alarm_type, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警事件表（权威状态机）';

-- ==================== 2. 告警状态流转记录表 ====================
CREATE TABLE IF NOT EXISTS alarm_state_transition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '流转记录ID（主键）',
    alarm_event_id BIGINT NOT NULL COMMENT '告警事件ID（关联 alarm_event）',
    from_state VARCHAR(20) DEFAULT NULL COMMENT '流转前状态（首次触发为空）',
    to_state VARCHAR(20) NOT NULL COMMENT '流转后状态',
    operator_user_id BIGINT DEFAULT NULL COMMENT '操作人用户ID（系统自动流转为空）',
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

-- ==================== 4. 站内通知表（已读状态） ====================
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

-- ==================== 5. 点位告警规则表（新增，D03/D18） ====================
-- 规则锚定模板指标项；设备级覆盖规则 device_id 非空（按点位替换式降级：设备级存在则该点位整体生效设备级）。
-- METRIC 规则 point_id 必填且必须属于模板已含点位；OFFLINE/COMM 通用规则 point_id 为空，
-- template_item_id 仅作层级/组织锚点，评估时按 rule_type 检索而非按点位。
CREATE TABLE IF NOT EXISTS device_point_alarm_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '规则ID（主键）',
    template_item_id BIGINT NOT NULL COMMENT '所属模板指标项（关联 device_type_point_template.id）',
    device_id INT DEFAULT NULL COMMENT 'NULL=模板级规则；非空=该设备覆盖规则（优先）',
    point_id BIGINT DEFAULT NULL COMMENT '关联 point_definition；NULL=通用规则（OFFLINE/COMM）',
    rule_type VARCHAR(20) NOT NULL DEFAULT 'METRIC' COMMENT '规则类型：METRIC-阈值 / OFFLINE-离线 / COMM-通信异常',
    rule_name VARCHAR(100) NOT NULL COMMENT '规则名（告警文案前缀）',
    operator VARCHAR(5) DEFAULT NULL COMMENT '比较符：GT/LT/GE/LE/EQ/NE；OFFLINE/COMM 规则为空',
    threshold DECIMAL(18,4) DEFAULT NULL COMMENT '阈值；OFFLINE 规则=离线分钟数（1-1440）',
    alarm_level TINYINT NOT NULL DEFAULT 3 COMMENT '告警级别：1-紧急 2-严重 3-一般 4-警告 5-提示',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '0-停用 1-启用',
    organization_id BIGINT NOT NULL DEFAULT 0 COMMENT '冗余自模板项组织归属（设备覆盖规则=设备所属组织）',
    remark VARCHAR(200) DEFAULT NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    CONSTRAINT fk_rule_template FOREIGN KEY (template_item_id) REFERENCES device_type_point_template(id),
    CONSTRAINT fk_rule_device FOREIGN KEY (device_id) REFERENCES devices(id),
    INDEX idx_rule_point_device (point_id, device_id, enabled),
    INDEX idx_rule_template (template_item_id),
    INDEX idx_rule_org (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点位告警规则表（三级降级：设备级覆盖→组织模板→平台模板）';

-- ==================== 6. 告警发送日志表（新增，D03/D28） ====================
-- status=1 对 WEBSOCKET 表示"已投递到用户队列"（非已读）；已读唯一由 in_app_notification.is_read 承担。
CREATE TABLE IF NOT EXISTS alarm_notification_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID（主键）',
    alarm_event_id BIGINT NOT NULL COMMENT '关联告警事件ID（alarm_event.id）',
    channel VARCHAR(20) NOT NULL COMMENT '发送通道：WEBSOCKET / EMAIL',
    recipient VARCHAR(100) COMMENT '接收方（用户ID或邮箱）',
    content VARCHAR(500) COMMENT '发送内容摘要',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '发送状态：0-待发 1-已投递 2-失败',
    error_message VARCHAR(500) COMMENT '失败原因',
    sent_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    organization_id BIGINT DEFAULT NULL COMMENT '所属组织ID（查询隔离）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_anl_event (alarm_event_id),
    INDEX idx_anl_org_time (organization_id, sent_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警发送日志表（发送记录，已读状态见 in_app_notification）';
