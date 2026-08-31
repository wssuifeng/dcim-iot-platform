-- ============================================================
-- V23: TB 兼容遥测 token（阶段八·S8-10，边界决策见 ADR-008）
--   token 映射机房凭证（非设备级）；不复用 secret——URL path 会进访问日志，
--   telemetry_token 是独立可重置的低敏感度凭证，泄露面与 secret 隔离。
-- ============================================================

ALTER TABLE ingest_credential
    ADD COLUMN telemetry_token VARCHAR(64) DEFAULT NULL
        COMMENT 'TB 兼容遥测 token（/api/v1/{token}/telemetry；随机 40-hex，唯一；NULL=未启用）' AFTER secret,
    ADD COLUMN telemetry_default_device VARCHAR(100) DEFAULT NULL
        COMMENT 'TB 纯格式报文的默认设备编码（单设备直连场景；报文显式带 device 字段时优先）' AFTER telemetry_token;

ALTER TABLE ingest_credential
    ADD UNIQUE KEY uk_ingest_credential_telemetry_token (telemetry_token);
