-- ============================================================
-- V22: 告警规则下行动作 webhook（阶段八·S8-9）
--   规则级开关：alarm_template_item 新增 action_webhook_url，非空即启用——
--   该规则命中触发/升级时回调外部 URL（POST 平台标准告警 JSON）。
--   定位为通知形态钩子，不做控制闭环；3s 超时、失败仅记录通知日志
--   （channel=ACTION_WEBHOOK），不重试不阻塞分发链。
-- ============================================================

ALTER TABLE alarm_template_item
    ADD COLUMN action_webhook_url VARCHAR(500) DEFAULT NULL
        COMMENT '下行动作 webhook：非空即启用，规则命中触发/升级时回调（S8-9，通知钩子非控制闭环）' AFTER hysteresis;
