-- ============================================================
-- V24: MQTT 子凭证（阶段八·S8-5，按评审定稿设计）
--   机房凭证体系新增 MQTT 专用子凭证：每机房一组独立用户名/密码，仅用于
--   MQTT 连接层鉴权（broker 侧校验），不参与 HMAC；连接自动识别归属机房。
--   标准 HMAC 通道（dcim/{accessKey}/…）保持不变，两者共存互不影响。
--   开放命名空间：dcim-open/{mqttUsername}/…，平台侧双重校验 username↔topic。
--   网安：开放通道纳入后期专项（渗透测试/加固），本阶段按最小权限实现并在
--   代码注释标注安全审查点（见 MqttAuthProvider / IngestMqttListener）。
-- ============================================================

ALTER TABLE ingest_credential
    ADD COLUMN mqtt_username VARCHAR(64) DEFAULT NULL
        COMMENT 'MQTT 子凭证用户名（连接层鉴权；NULL=未启用；开放命名空间 dcim-open/{mqttUsername}/）' AFTER telemetry_default_device,
    ADD COLUMN mqtt_password VARCHAR(100) DEFAULT NULL
        COMMENT 'MQTT 子凭证密码（broker 侧校验；与 secret 同为平台持有明文，重置即换）' AFTER mqtt_username;

ALTER TABLE ingest_credential
    ADD UNIQUE KEY uk_ingest_credential_mqtt_username (mqtt_username);
