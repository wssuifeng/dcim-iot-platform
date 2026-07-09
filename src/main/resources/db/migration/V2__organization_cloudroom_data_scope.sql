-- ============================================================
-- Flyway V2: 组织、机房与数据范围基础模型
--
-- 契约依据：共享契约 03-共享契约.md 数据范围章节
--   scopeType: PLATFORM / ORG_ALL / ROOMS / READ_ONLY
--   数据范围是服务层安全边界，不是前端概念（服务层校验逻辑属于 INFRA-CLAUDE-02）
-- ============================================================

-- ==================== 1. 组织表 ====================
CREATE TABLE IF NOT EXISTS sys_organization (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '组织ID（主键）',
    org_name VARCHAR(100) NOT NULL COMMENT '组织名称',
    org_code VARCHAR(50) NOT NULL COMMENT '组织编码（全局唯一）',
    description VARCHAR(200) COMMENT '组织描述',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常 1-停用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    UNIQUE KEY uk_org_code (org_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织表';

-- ==================== 2. 云机房表 ====================
CREATE TABLE IF NOT EXISTS sys_cloud_room (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '机房ID（主键）',
    organization_id BIGINT NOT NULL COMMENT '所属组织ID',
    room_name VARCHAR(100) NOT NULL COMMENT '机房名称',
    room_code VARCHAR(50) NOT NULL COMMENT '机房编码（组织内唯一）',
    location VARCHAR(200) COMMENT '机房物理位置描述',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-正常 1-停用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    CONSTRAINT fk_cloud_room_org FOREIGN KEY (organization_id) REFERENCES sys_organization(id),
    UNIQUE KEY uk_org_room_code (organization_id, room_code),
    INDEX idx_cloud_room_org (organization_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='云机房表';

-- ==================== 3. 用户范围分配表 ====================
CREATE TABLE IF NOT EXISTS sys_data_scope_assignment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分配ID（主键）',
    user_id BIGINT NOT NULL COMMENT '用户ID（关联 sys_user）',
    organization_id BIGINT DEFAULT NULL COMMENT '组织ID（PLATFORM范围可为空）',
    cloud_room_id BIGINT DEFAULT NULL COMMENT '机房ID（仅 ROOMS 范围类型使用）',
    scope_type VARCHAR(20) NOT NULL COMMENT '范围类型：PLATFORM/ORG_ALL/ROOMS/READ_ONLY',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    CONSTRAINT fk_scope_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_scope_org FOREIGN KEY (organization_id) REFERENCES sys_organization(id),
    CONSTRAINT fk_scope_room FOREIGN KEY (cloud_room_id) REFERENCES sys_cloud_room(id),
    INDEX idx_scope_user (user_id),
    INDEX idx_scope_org (organization_id),
    INDEX idx_scope_room (cloud_room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户数据范围分配表';

-- ==================== 4. sys_user 增补组织归属 ====================
ALTER TABLE sys_user
    ADD COLUMN organization_id BIGINT DEFAULT NULL COMMENT '所属组织ID（PLATFORM_ADMIN可为空）' AFTER role;

ALTER TABLE sys_user
    ADD CONSTRAINT fk_user_org FOREIGN KEY (organization_id) REFERENCES sys_organization(id);

-- ==================== 5. devices 增补组织/机房归属 ====================
-- location 字段保留为展示用文本，范围校验以 organization_id/cloud_room_id 为准。
ALTER TABLE devices
    ADD COLUMN organization_id BIGINT DEFAULT NULL COMMENT '所属组织ID（数据范围校验依据）' AFTER type_id,
    ADD COLUMN cloud_room_id BIGINT DEFAULT NULL COMMENT '所属机房ID（数据范围校验依据）' AFTER organization_id;

ALTER TABLE devices
    ADD CONSTRAINT fk_device_org FOREIGN KEY (organization_id) REFERENCES sys_organization(id),
    ADD CONSTRAINT fk_device_room FOREIGN KEY (cloud_room_id) REFERENCES sys_cloud_room(id);

CREATE INDEX idx_devices_org ON devices(organization_id);
CREATE INDEX idx_devices_room ON devices(cloud_room_id);
