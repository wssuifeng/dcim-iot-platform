-- ============================================================
-- V20: 告警 webhook 通知渠道（阶段八·S8-2）
--   站内/邮件之外并行挂 IM webhook 渠道（企业微信/钉钉/飞书/ntfy/Telegram/通用）。
--   实现纪律：报文模板与加签算法按渠道官方文档实现（文档链接与核对记录见
--   docs/阶段八-真实用户打磨计划/01-开发计划.md S8-2），URL/密钥全部数据驱动。
--   敏感凭证（credential_json）本期明文落库 + ADMIN-only 权限，加密存储记入长期备忘。
-- ============================================================

CREATE TABLE alarm_webhook_channel (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '渠道ID（主键）',
    name           VARCHAR(100) NOT NULL COMMENT '渠道显示名，如"运维值班群"',
    channel_type   VARCHAR(20)  NOT NULL COMMENT '渠道类型：WECOM/DINGTALK/FEISHU/NTFY/TELEGRAM/GENERIC',
    webhook_url    VARCHAR(500) NOT NULL COMMENT 'webhook 地址（各平台机器人创建后获取；TELEGRAM 为完整 sendMessage URL）',
    credential_json VARCHAR(2000) DEFAULT NULL COMMENT '渠道凭证 JSON：钉钉/飞书加签 secret、Telegram chat_id、ntfy token、通用 authHeader 等',
    max_alarm_level TINYINT NOT NULL DEFAULT 3 COMMENT '推送条件：alarm_level <= max_alarm_level 即推送（1=最紧急，数字小=严重）',
    enabled        TINYINT NOT NULL DEFAULT 0 COMMENT '0-停用 1-启用',
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_webhook_channel_enabled (enabled)
) COMMENT '告警 webhook 通知渠道';
