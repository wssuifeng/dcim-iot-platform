-- ============================================================
-- Flyway V3: 设备点位与指标模型 + 五类设备类型种子数据
--
-- 契约依据：共享契约 03-共享契约.md 设备/点位/指标章节
--   复用现有 device_types 表作为契约中的 DeviceType 模型，
--   种子写入五类设备类型编码：UPS / PDU_METER / PRECISION_AC / TEMP_HUMIDITY / LEAK_DETECTOR
--
-- 二次基线调整（D07/D09，见 docs/2026-08-24-数据上报与告警模块重构设计.md）：
--   - point_definition / device_type_point_template 增补 organization_id（0=平台级全局，>0=组织私有），
--     唯一键由全局唯一调整为组织内唯一
--   - metric_sample 增补 receive_time（平台接收时间，与设备侧 sample_time 双时间基准），
--     并补充 (point_id, sample_time) 索引支撑保留清理任务
-- ============================================================

-- ==================== 1. 点位定义表（双层：平台级/组织私有） ====================
CREATE TABLE IF NOT EXISTS point_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '点位定义ID（主键）',
    point_code VARCHAR(50) NOT NULL COMMENT '点位编码（组织内唯一，如 voltage_a、temperature）',
    point_name VARCHAR(100) NOT NULL COMMENT '点位名称（展示用）',
    unit VARCHAR(20) DEFAULT NULL COMMENT '单位（如 V、A、℃、%）',
    data_type VARCHAR(20) NOT NULL DEFAULT 'NUMBER' COMMENT '数据类型：NUMBER/BOOLEAN/STRING',
    description VARCHAR(200) DEFAULT NULL COMMENT '点位描述',
    organization_id BIGINT NOT NULL DEFAULT 0 COMMENT '归属组织：0=平台级全局共享，>0=组织私有',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    UNIQUE KEY uk_org_point_code (organization_id, point_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点位定义表（唯一指标字典）';

-- ==================== 2. 设备类型点位模板表（双层：平台默认/组织自定义） ====================
CREATE TABLE IF NOT EXISTS device_type_point_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '模板ID（主键，一行即一个模板指标项）',
    type_id INT NOT NULL COMMENT '设备类型ID（关联 device_types）',
    point_id BIGINT NOT NULL COMMENT '点位定义ID（关联 point_definition）',
    organization_id BIGINT NOT NULL DEFAULT 0 COMMENT '归属组织：0=平台级默认模板，>0=组织自定义模板',
    is_required TINYINT NOT NULL DEFAULT 0 COMMENT '是否必选点位：0-可选 1-必选',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    CONSTRAINT fk_template_type FOREIGN KEY (type_id) REFERENCES device_types(id),
    CONSTRAINT fk_template_point FOREIGN KEY (point_id) REFERENCES point_definition(id),
    UNIQUE KEY uk_org_type_point (organization_id, type_id, point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备类型点位模板表（组织级优先，降级平台级）';

-- ==================== 3. 设备点位绑定表 ====================
CREATE TABLE IF NOT EXISTS device_point_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '绑定ID（主键）',
    device_id INT NOT NULL COMMENT '设备ID（关联 devices）',
    point_id BIGINT NOT NULL COMMENT '点位定义ID（关联 point_definition）',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '绑定状态：0-启用 1-停用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    CONSTRAINT fk_binding_device FOREIGN KEY (device_id) REFERENCES devices(id),
    CONSTRAINT fk_binding_point FOREIGN KEY (point_id) REFERENCES point_definition(id),
    UNIQUE KEY uk_device_point (device_id, point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备点位绑定表';

-- ==================== 4. 指标采样表（历史序列，双时间基准） ====================
CREATE TABLE IF NOT EXISTS metric_sample (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '采样ID（主键，海量数据，使用BIGINT）',
    device_id INT NOT NULL COMMENT '设备ID（关联 devices）',
    point_id BIGINT NOT NULL COMMENT '点位定义ID（关联 point_definition）',
    sample_value DECIMAL(18,4) COMMENT '采样值',
    sample_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '采样时间（设备侧时钟基准）',
    receive_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '平台接收存储时间（服务端时钟基准）',
    INDEX idx_metric_sample_device_point_time (device_id, point_id, sample_time),
    INDEX idx_metric_sample_time (sample_time),
    INDEX idx_metric_sample_point_time (point_id, sample_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标采样历史表';

-- ==================== 5. 指标最新值表（当前值快照，读侧缓存） ====================
CREATE TABLE IF NOT EXISTS metric_latest (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    device_id INT NOT NULL COMMENT '设备ID（关联 devices）',
    point_id BIGINT NOT NULL COMMENT '点位定义ID（关联 point_definition）',
    latest_value DECIMAL(18,4) COMMENT '最新值',
    latest_time DATETIME COMMENT '最新值采集时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    UNIQUE KEY uk_metric_latest_device_point (device_id, point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标最新值快照表';

-- ==================== 6. 指标保留策略表（平台级统一，D08） ====================
CREATE TABLE IF NOT EXISTS metric_retention_policy (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    point_id BIGINT DEFAULT NULL COMMENT '点位定义ID（为空表示全局默认策略）',
    retention_days INT NOT NULL DEFAULT 90 COMMENT '保留天数',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT fk_retention_point FOREIGN KEY (point_id) REFERENCES point_definition(id),
    UNIQUE KEY uk_retention_point (point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标数据保留策略表（平台级统一管理）';

-- ==================== 7. 五类设备类型种子数据 ====================
-- 使用 INSERT ... ON DUPLICATE KEY UPDATE 保证在空库或已存在同编码记录时均可幂等执行。
INSERT INTO device_types (type_name, type_code, description) VALUES
    ('不间断电源', 'UPS', 'UPS 不间断电源设备'),
    ('智能电表/PDU', 'PDU_METER', 'PDU 智能电表设备'),
    ('精密空调', 'PRECISION_AC', '机房精密空调设备'),
    ('温湿度传感器', 'TEMP_HUMIDITY', '温湿度环境监测设备'),
    ('漏水检测器', 'LEAK_DETECTOR', '机房漏水检测设备')
ON DUPLICATE KEY UPDATE
    type_name = VALUES(type_name),
    description = VALUES(description);
