-- ============================================================
-- V11: 网关数据接收门（ingest）——机器身份凭证与幂等去重
-- 对应计划: docs/阶段二与阶段三实施计划-数据接收门与机房仿真服务.md §3.1
--
-- 设计要点:
--   1. 网关是通信主体(凭证/心跳), 设备是数据主体(报文内 deviceCode)——身份解耦;
--   2. secret 存明文而非 BCrypt: 验签需计算 sha256(secret + rawBody),
--      单向哈希无法还原 secret 参与计算(与 AWS AccessKey/Secret 同构的
--      HMAC 式签名模型), 计划书 v2 §3.1 的 BCrypt(secret) 表述已在修订记录更正;
--   3. ingest_dedup 由定时任务按窗口清理(QoS1 至少一次投递的重发去重);
--   4. 现有业务表(devices/point_definition/metric_sample 等)零改动。
-- ============================================================

CREATE TABLE ingest_credential (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    access_key     VARCHAR(64)  NOT NULL UNIQUE COMMENT '接入标识（明文传输，网关唯一）',
    secret         VARCHAR(100) NOT NULL COMMENT '签名密钥（明文存储：验签需还原参与 sha256 计算）',
    gateway_name   VARCHAR(100) NOT NULL COMMENT '网关显示名，如"机房A-动环主机01"',
    cloud_room_id  BIGINT       NOT NULL COMMENT '绑定机房（该凭证只能上报此机房设备）',
    simulated      TINYINT      NOT NULL DEFAULT 0 COMMENT '仿真网关标记：1=机房仿真服务专用凭证',
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '0-启用 1-停用',
    last_seen_at   DATETIME COMMENT '最近心跳/上报时间（离线观测依据）',
    expire_at      DATETIME COMMENT '凭证过期时间（可空=永久）',
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_ingest_room (cloud_room_id),
    KEY idx_ingest_last_seen (last_seen_at)
) COMMENT '网关接入凭证';

CREATE TABLE ingest_dedup (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    dedup_key   VARCHAR(128) NOT NULL COMMENT 'sha256(deviceCode:pointCode:seq)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ingest_dedup (dedup_key)
) COMMENT '上报幂等去重（定时按窗口清理）';
