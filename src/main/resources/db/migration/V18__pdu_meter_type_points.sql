-- ============================================================
-- V18: PDU_METER 类型指标清单补齐——电压/相电流
--
-- 背景：仿真器 PDU 预设含 voltage_a/current_a 寄存器（真实 PDU 测量项，
--   指标字典 V3 已有定义），但 PDU_METER 类型基础指标清单从未声明这两项——
--   自动注册的 PDU 设备缺绑定，上报电压/电流样本被接收门拒绝
--   （"设备 X 未启用指标 Y 的绑定"）。
-- 原则：与 V16 一致，按编码解析 + NOT EXISTS 幂等，兼容存量环境回填设备绑定。
-- ============================================================

-- ==================== 1. 类型基础指标清单补齐 ====================
INSERT INTO device_type_point_template (type_id, point_id, is_required, sort_order)
SELECT t.id, p.id, 1, 3
FROM device_types t, point_definition p
WHERE t.type_code = 'PDU_METER' AND p.point_code = 'voltage_a'
  AND NOT EXISTS (
    SELECT 1 FROM device_type_point_template tpl
    WHERE tpl.type_id = t.id AND tpl.point_id = p.id AND tpl.deleted = 0);

INSERT INTO device_type_point_template (type_id, point_id, is_required, sort_order)
SELECT t.id, p.id, 1, 4
FROM device_types t, point_definition p
WHERE t.type_code = 'PDU_METER' AND p.point_code = 'current_a'
  AND NOT EXISTS (
    SELECT 1 FROM device_type_point_template tpl
    WHERE tpl.type_id = t.id AND tpl.point_id = p.id AND tpl.deleted = 0);

-- ==================== 2. 存量 PDU 设备绑定回填 ====================
-- 已注册的 PDU_METER 设备逐台补建缺失绑定（清单同步仅覆盖注册时刻，存量需回填）
INSERT INTO device_point_binding (device_id, point_id, status, deleted)
SELECT d.id, p.id, 0, 0
FROM devices d
JOIN device_types t ON t.id = d.type_id AND t.type_code = 'PDU_METER' AND t.deleted = 0
JOIN point_definition p ON p.point_code IN ('voltage_a', 'current_a') AND p.deleted = 0
WHERE d.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM device_point_binding b
    WHERE b.device_id = d.id AND b.point_id = p.id AND b.deleted = 0);
