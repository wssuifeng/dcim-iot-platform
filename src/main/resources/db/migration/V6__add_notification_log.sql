-- ============================================================
-- Flyway V6: 创建通知发送日志表 notification_log
-- 说明：记录每次告警通知的发送情况，支持邮件/WebSocket双通道追溯
-- ============================================================

CREATE TABLE IF NOT EXISTS notification_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    alert_id INT NOT NULL COMMENT '关联告警ID',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知发送日志表';
