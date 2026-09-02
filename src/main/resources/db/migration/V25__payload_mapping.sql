-- ============================================================
-- V25: 自定义报文映射接入（阶段九·S9-1，ADR-009）
--   零散直连设备的报文方言适配：使用者把设备原始报文里的字段路径
--   手动映射到平台指标字典（pointCode），平台按模板自动转译后走
--   传输无关核心（归属/自动注册/幂等同口径）。
--   模板挂机房（凭证→机房→模板池），机内多设备类型用特征匹配路由；
--   原始报文每凭证仅留存最新一条（UI 配置参照物，新报文覆盖）。
-- ============================================================

CREATE TABLE ingest_payload_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY
        COMMENT '映射模板ID',
    name VARCHAR(100) NOT NULL
        COMMENT '模板名称',
    cloud_room_id BIGINT NOT NULL
        COMMENT '所属机房（凭证→机房→模板池，模板对机房内全部开放通道凭证生效）',
    enabled TINYINT NOT NULL DEFAULT 1
        COMMENT '1=启用 0=停用（停用不参与匹配）',
    feature_json TEXT NOT NULL
        COMMENT '特征匹配规则 JSON 数组：[{"path":"model","op":"eq|contains|exists","value":"..."}]，全部命中才路由到本模板',
    device_code_path VARCHAR(200) DEFAULT NULL
        COMMENT '设备编码提取路径（报文 JSON 内部点路径+数组下标）；与 device_code_fixed 二选一',
    device_code_fixed VARCHAR(100) DEFAULT NULL
        COMMENT '固定设备编码（device_code_path 为空时使用）',
    sample_time_path VARCHAR(200) DEFAULT NULL
        COMMENT '采样时间提取路径（毫秒/秒级时间戳）；空=服务器接收时刻',
    mappings_json TEXT NOT NULL
        COMMENT '字段映射 JSON 数组：[{"path":"data.temp","pointCode":"temperature","scale":0.1}]，scale 可空（原值直取）',
    deleted TINYINT NOT NULL DEFAULT 0
        COMMENT '逻辑删除',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT '更新时间',
    KEY idx_payload_mapping_room (cloud_room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='自定义报文映射模板（零散设备接入，ADR-009）';

CREATE TABLE ingest_raw_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY
        COMMENT '原始报文留存ID',
    credential_id BIGINT NOT NULL
        COMMENT '凭证ID（每凭证仅最新一条，新报文覆盖）',
    content MEDIUMTEXT NOT NULL
        COMMENT '原始报文内容（未匹配模板时的报文原文，供映射配置参照）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '收到时间',
    UNIQUE KEY uk_raw_message_credential (credential_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='开放通道未匹配原始报文留存（每凭证一条，ADR-009）';
