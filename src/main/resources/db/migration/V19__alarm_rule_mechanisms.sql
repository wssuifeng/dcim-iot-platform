-- ============================================================
-- V19: 告警规则机制增强（阶段八·S8-1 三机制 + 一补充）
--   alarm_template_item 新增五个可选字段，默认值全部等于"不启用"，
--   存量模版行为零变化；不建持久状态表——M1/M2 判定基准一律在评估时
--   从 metric_sample 尾部推导（幂等收敛、重启续算免实现）。
--   机制语义见 docs/阶段八-真实用户打磨计划/01-开发计划.md S8-1。
-- ============================================================

ALTER TABLE alarm_template_item
    ADD COLUMN breach_count INT NOT NULL DEFAULT 0
        COMMENT 'M1 连续越限次数：<=1 不启用（首点越限即告警）；>=2 连续 N 个采样点越限才告警，回落进迟滞安全区清零' AFTER threshold,
    ADD COLUMN rate_limit DECIMAL(18,4) DEFAULT NULL
        COMMENT 'M2 变化量限值：NULL 不启用；窗口内|当前-最早|超过即直接告警；累计量指标（如 power_kwh）禁用' AFTER breach_count,
    ADD COLUMN rate_window_sec INT NOT NULL DEFAULT 60
        COMMENT 'M2 变化量窗口（秒），1-86400；窗口内不足两条采样时跳过该机制回退直接阈值' AFTER rate_limit,
    ADD COLUMN re_alarm_sec INT NOT NULL DEFAULT 0
        COMMENT 'M3 再告警间隔（秒）：0 不启用；活动告警持续越限且距最近一次成功通知超过该值时重发通知' AFTER rate_window_sec,
    ADD COLUMN hysteresis DECIMAL(18,4) NOT NULL DEFAULT 0
        COMMENT '恢复迟滞：0 不启用；GT 安全区=value<threshold-hysteresis，LT 安全区=value>threshold+hysteresis' AFTER re_alarm_sec;
