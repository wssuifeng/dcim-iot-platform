-- ============================================================
-- V13: 设备接入打通——首包自动注册、接入视角、网关离线告警、用电量点位
-- 对应迭代计划: A1（详见 docs 计划书与本迁移各段注释）
-- ============================================================

-- 1. 网关凭证：首包自动注册开关与默认设备类型
ALTER TABLE ingest_credential
    ADD COLUMN auto_register TINYINT NOT NULL DEFAULT 0 COMMENT '首包自动注册：1=收到未知 deviceCode 自动建档' AFTER simulated,
    ADD COLUMN default_type_id INT NULL COMMENT '自动注册设备默认类型（auto_register=1 时必填）' AFTER auto_register;

-- 2. 设备：接入视角（最后经哪个网关上报）
ALTER TABLE devices
    ADD COLUMN last_ingest_credential_id BIGINT NULL COMMENT '最后上报所经网关凭证ID' AFTER last_online_time;

-- 3. 告警事件：device_id 改可空——网关级告警（GATEWAY_OFFLINE）不挂设备
ALTER TABLE alarm_event MODIFY device_id INT NULL COMMENT '设备ID（网关级告警为空）';
ALTER TABLE alarm_event ADD INDEX idx_alarm_event_type_state (alarm_type, state);

-- 4. 用电量点位目录 + 类型模板补齐
INSERT INTO point_definition (point_code, point_name, unit, data_type, description) VALUES
    ('power_kw',  '有功功率', 'kW', 'NUMBER', '机房/设备有功功率'),
    ('power_kwh', '累计电量', 'kWh', 'NUMBER', '累计耗电量（单调递增）');

-- device_type id 依据 V3 自增顺序：1=UPS, 2=PDU_METER, 3=PRECISION_AC, 4=TEMP_HUMIDITY, 5=LEAK_DETECTOR
INSERT INTO device_type_point_template (type_id, point_id, is_required, sort_order)
SELECT 2, id, 0, 10 FROM point_definition WHERE point_code = 'power_kw'
UNION ALL
SELECT 2, id, 0, 11 FROM point_definition WHERE point_code = 'power_kwh'
UNION ALL
SELECT 1, id, 0, 10 FROM point_definition WHERE point_code = 'power_kwh';
