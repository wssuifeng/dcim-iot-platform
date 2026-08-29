-- V16: 指标字典扩容——补齐精密空调/漏水检测基础指标清单 + UPS 指标增强
--
-- 背景：设备类型目录共 5 类（UPS/PDU_METER/PRECISION_AC/TEMP_HUMIDITY/LEAK_DETECTOR），
-- 但指标字典此前只覆盖温湿度、UPS 相电压电流与电表功率——精密空调与漏水检测
-- 没有任何可绑定的指标，首包自动注册与类型基础指标清单对这两类设备断裂，
-- 仿真器预设设备目录（5 类）也无法端到端闭环。
--
-- 原则：全部按编码解析（type_code / point_code），不硬编码自增 ID；
-- NOT EXISTS 幂等保护，兼容已手工补录过部分指标的环境。

-- ==================== 1. 新增指标定义 ====================
INSERT INTO point_definition (point_code, point_name, unit, data_type, description)
SELECT 'supply_air_temp', '送风温度', '℃', 'NUMBER', '精密空调送风温度'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM point_definition WHERE point_code = 'supply_air_temp' AND deleted = 0);

INSERT INTO point_definition (point_code, point_name, unit, data_type, description)
SELECT 'return_air_temp', '回风温度', '℃', 'NUMBER', '精密空调回风温度'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM point_definition WHERE point_code = 'return_air_temp' AND deleted = 0);

INSERT INTO point_definition (point_code, point_name, unit, data_type, description)
SELECT 'leak_status', '漏水状态', '-', 'NUMBER', '漏水检测 0正常 1告警'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM point_definition WHERE point_code = 'leak_status' AND deleted = 0);

INSERT INTO point_definition (point_code, point_name, unit, data_type, description)
SELECT 'battery_level', '电池电量', '%', 'NUMBER', 'UPS电池电量'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM point_definition WHERE point_code = 'battery_level' AND deleted = 0);

INSERT INTO point_definition (point_code, point_name, unit, data_type, description)
SELECT 'output_load', '输出负载率', '%', 'NUMBER', 'UPS输出负载率'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM point_definition WHERE point_code = 'output_load' AND deleted = 0);

-- ==================== 2. 类型基础指标清单补齐 ====================
-- 精密空调：送风温度 + 回风温度
INSERT INTO device_type_point_template (type_id, point_id, is_required, sort_order)
SELECT t.id, p.id, 1, 1
FROM device_types t, point_definition p
WHERE t.type_code = 'PRECISION_AC' AND p.point_code = 'supply_air_temp'
  AND NOT EXISTS (
    SELECT 1 FROM device_type_point_template tpl
    WHERE tpl.type_id = t.id AND tpl.point_id = p.id AND tpl.deleted = 0);

INSERT INTO device_type_point_template (type_id, point_id, is_required, sort_order)
SELECT t.id, p.id, 1, 2
FROM device_types t, point_definition p
WHERE t.type_code = 'PRECISION_AC' AND p.point_code = 'return_air_temp'
  AND NOT EXISTS (
    SELECT 1 FROM device_type_point_template tpl
    WHERE tpl.type_id = t.id AND tpl.point_id = p.id AND tpl.deleted = 0);

-- 漏水检测：漏水状态
INSERT INTO device_type_point_template (type_id, point_id, is_required, sort_order)
SELECT t.id, p.id, 1, 1
FROM device_types t, point_definition p
WHERE t.type_code = 'LEAK_DETECTOR' AND p.point_code = 'leak_status'
  AND NOT EXISTS (
    SELECT 1 FROM device_type_point_template tpl
    WHERE tpl.type_id = t.id AND tpl.point_id = p.id AND tpl.deleted = 0);

-- UPS：追加电池电量 + 输出负载率（排在已有相电压/相电流之后）
INSERT INTO device_type_point_template (type_id, point_id, is_required, sort_order)
SELECT t.id, p.id, 1, 3
FROM device_types t, point_definition p
WHERE t.type_code = 'UPS' AND p.point_code = 'battery_level'
  AND NOT EXISTS (
    SELECT 1 FROM device_type_point_template tpl
    WHERE tpl.type_id = t.id AND tpl.point_id = p.id AND tpl.deleted = 0);

INSERT INTO device_type_point_template (type_id, point_id, is_required, sort_order)
SELECT t.id, p.id, 1, 4
FROM device_types t, point_definition p
WHERE t.type_code = 'UPS' AND p.point_code = 'output_load'
  AND NOT EXISTS (
    SELECT 1 FROM device_type_point_template tpl
    WHERE tpl.type_id = t.id AND tpl.point_id = p.id AND tpl.deleted = 0);
