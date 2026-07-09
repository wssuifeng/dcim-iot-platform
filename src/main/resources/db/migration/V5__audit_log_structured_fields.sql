-- ============================================================
-- Flyway V5: 安全审计日志结构化字段扩展
--
-- 契约依据：共享契约 03-共享契约.md 审计章节
--   结构化字段：actorUserId, actorUsername, role, organizationId, cloudRoomId,
--               action, targetType, targetId, result, errorCode, ip, traceId, createdAt
--   本迁移仅新增列（均可为空），不改变既有 user_id/username/operation/ip/detail/create_time
--   列的语义和读写方式，保证 SysAuditLogService.log(userId, username, operation, ip, detail)
--   现有调用方无需修改即可编译通过。新字段的写入逻辑由 INFRA-CLAUDE-02 负责。
--   审计日志不得存储密码、Token、密钥或连接字符串（服务层约束，此处仅建列）。
-- ============================================================

ALTER TABLE sys_audit_log
    ADD COLUMN role VARCHAR(20) DEFAULT NULL COMMENT '操作人角色（结构化字段）' AFTER username,
    ADD COLUMN organization_id BIGINT DEFAULT NULL COMMENT '操作人所属组织ID（结构化字段）' AFTER role,
    ADD COLUMN cloud_room_id BIGINT DEFAULT NULL COMMENT '操作人所属机房ID（结构化字段）' AFTER organization_id,
    ADD COLUMN action VARCHAR(50) DEFAULT NULL COMMENT '标准化操作动作（结构化字段，与 operation 并存）' AFTER cloud_room_id,
    ADD COLUMN target_type VARCHAR(50) DEFAULT NULL COMMENT '操作目标类型（如 DEVICE/USER/ALARM_EVENT）' AFTER action,
    ADD COLUMN target_id VARCHAR(50) DEFAULT NULL COMMENT '操作目标ID（字符串存储以兼容不同主键类型）' AFTER target_type,
    ADD COLUMN result VARCHAR(20) DEFAULT NULL COMMENT '操作结果：SUCCESS/FAILURE/REJECTED' AFTER target_id,
    ADD COLUMN error_code VARCHAR(50) DEFAULT NULL COMMENT '错误码（失败/拒绝时记录，前缀由 CODEX 统一定义）' AFTER result,
    ADD COLUMN trace_id VARCHAR(64) DEFAULT NULL COMMENT '链路追踪ID' AFTER error_code;

CREATE INDEX idx_audit_log_org ON sys_audit_log(organization_id);
CREATE INDEX idx_audit_log_room ON sys_audit_log(cloud_room_id);
CREATE INDEX idx_audit_log_action ON sys_audit_log(action);
CREATE INDEX idx_audit_log_result ON sys_audit_log(result);
CREATE INDEX idx_audit_log_trace_id ON sys_audit_log(trace_id);
