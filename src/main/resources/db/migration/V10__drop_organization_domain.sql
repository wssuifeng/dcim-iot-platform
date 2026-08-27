-- ============================================================
-- Flyway V10: 用户域收束——从多组织 SaaS 回归单租户自部署
--
-- 决策背景（ADR-001）：多组织/四级数据范围体系使每个后续功能
--   承担越权防护成本，定位收束为单租户自部署 DCIM 平台。
--
-- 本迁移内容：
--   1. DROP 六张组织域表：组织、成员关系、创建/加入申请、
--      数据范围分配、平台注册策略
--   2. DROP 十张业务表的 organization_id 列（机房/设备/点位/
--      模板/告警/告警规则/通知日志/审计/仿真场景/仿真运行）
--   3. sys_user 角色收束：PLATFORM_ADMIN/ORG_ADMIN/ADMIN → ADMIN，
--      DROP platform_role 列（email 列保留，开放注册链路在用）
--
-- 注意：本迁移只删列不折叠基线，V1-V9 完整历史保留。
-- ============================================================

-- ==================== 1. 解除指向 sys_organization 的外键 ====================
-- 机房/设备的 organization_id 外键会阻止组织表删除，先解除（列随后删除）
ALTER TABLE sys_cloud_room DROP FOREIGN KEY fk_cloud_room_org;
ALTER TABLE devices DROP FOREIGN KEY fk_device_org;

-- ==================== 2. 组织域表整体删除 ====================
DROP TABLE IF EXISTS organization_join_application;
DROP TABLE IF EXISTS organization_creation_application;
DROP TABLE IF EXISTS organization_membership;
DROP TABLE IF EXISTS sys_data_scope_assignment;
DROP TABLE IF EXISTS platform_registration_settings;
DROP TABLE IF EXISTS sys_organization;

-- ==================== 3. sys_user 角色收束 ====================
-- 历史角色值统一归一化为 ADMIN（v1.0 ADMIN / PLATFORM_ADMIN / ORG_ADMIN）
UPDATE sys_user SET role = 'ADMIN'
    WHERE role IN ('PLATFORM_ADMIN', 'ORG_ADMIN', 'ADMIN');
ALTER TABLE sys_user DROP COLUMN platform_role;

-- ==================== 4. 业务表 organization_id 列删除 ====================
ALTER TABLE sys_cloud_room DROP COLUMN organization_id;
ALTER TABLE devices DROP COLUMN organization_id;
ALTER TABLE point_definition DROP COLUMN organization_id;
ALTER TABLE device_type_point_template DROP COLUMN organization_id;
ALTER TABLE alarm_event DROP COLUMN organization_id;
ALTER TABLE device_point_alarm_rule DROP COLUMN organization_id;
ALTER TABLE alarm_notification_log DROP COLUMN organization_id;
ALTER TABLE sys_audit_log DROP COLUMN organization_id;
ALTER TABLE simulation_scene DROP COLUMN organization_id;
ALTER TABLE simulation_run_record DROP COLUMN organization_id;
