-- ============================================================
-- V14: 机房与接入凭证合一——1:1 约束 + 冗余列清理
-- 对应重构: 创建机房即初始化该机房网关凭证；网关只认平台地址+AK+SK，
--   云平台管理概念（机房、密钥生命周期）全部收敛平台侧。
--
-- 变更:
--   1. uk_ingest_room 唯一约束：机房↔凭证 1:1（数据库级）；
--   2. 删 gateway_name 列（告警消息与前端展示改用机房名）；
--   3. 删 simulated 列（后端零消费的纯标记字段）。
-- ============================================================

-- 1:1 唯一约束（存量已核无重复；未来创建机房时服务层同事务建凭证）
ALTER TABLE ingest_credential
    ADD UNIQUE KEY uk_ingest_room (cloud_room_id);

-- 冗余列清理
ALTER TABLE ingest_credential DROP COLUMN gateway_name;
ALTER TABLE ingest_credential DROP COLUMN simulated;
