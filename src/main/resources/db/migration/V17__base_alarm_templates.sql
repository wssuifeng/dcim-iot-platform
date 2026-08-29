-- ============================================================
-- V17: 四类设备基础告警模版补齐 + 温湿度模版湿度规则补齐
--
-- 背景：V15 迁移只为"旧表存在模板级规则"的类型生成基础模版，
--   实际只有 TEMP_HUMIDITY 命中——UPS / PDU_METER / PRECISION_AC /
--   LEAK_DETECTOR 四类 alarm_template_id 为 NULL：
--   这四类设备的 METRIC 规则完全不评估（离线检测走全局兜底阈值）。
--
-- 原则：与 V16 一致，全部按编码解析（type_code / point_code），
--   NOT EXISTS 幂等保护；阈值取行业常规默认值，具体设备/机房
--   可复制模版自定义或改绑，不追求一劳永逸的精确值。
-- ============================================================

-- ==================== 1. UPS 基础告警模版 ====================
INSERT INTO alarm_template (template_code, template_name, description, status)
SELECT 'BASE_UPS', 'UPS-基础告警模版',
       'UPS 通用监控默认值：相电压越限（220V±10%）、电池电量两级、输出重载、离线',
       0
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM alarm_template WHERE template_code = 'BASE_UPS');

-- 离线规则（指标为空）
INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, NULL, 'OFFLINE', '设备离线', NULL, 15, 2, 1, 1
FROM alarm_template t
WHERE t.template_code = 'BASE_UPS'
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id IS NULL AND i.rule_name = '设备离线');

-- 电压规则：220V ±10%
INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, p.id, 'METRIC', 'A相电压欠压', 'LT', 198, 2, 1, 2
FROM alarm_template t, point_definition p
WHERE t.template_code = 'BASE_UPS' AND p.point_code = 'voltage_a' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id = p.id AND i.rule_name = 'A相电压欠压');

INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, p.id, 'METRIC', 'A相电压过压', 'GT', 242, 2, 1, 3
FROM alarm_template t, point_definition p
WHERE t.template_code = 'BASE_UPS' AND p.point_code = 'voltage_a' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id = p.id AND i.rule_name = 'A相电压过压');

-- 电池电量两级：偏低提示、危急紧急
INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, p.id, 'METRIC', '电池电量偏低', 'LT', 30, 3, 1, 4
FROM alarm_template t, point_definition p
WHERE t.template_code = 'BASE_UPS' AND p.point_code = 'battery_level' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id = p.id AND i.rule_name = '电池电量偏低');

INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, p.id, 'METRIC', '电池电量危急', 'LT', 10, 1, 1, 5
FROM alarm_template t, point_definition p
WHERE t.template_code = 'BASE_UPS' AND p.point_code = 'battery_level' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id = p.id AND i.rule_name = '电池电量危急');

-- 输出负载率：重载提示
INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, p.id, 'METRIC', '输出负载重载', 'GT', 90, 3, 1, 6
FROM alarm_template t, point_definition p
WHERE t.template_code = 'BASE_UPS' AND p.point_code = 'output_load' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id = p.id AND i.rule_name = '输出负载重载');

-- ==================== 2. PDU_METER 基础告警模版 ====================
INSERT INTO alarm_template (template_code, template_name, description, status)
SELECT 'BASE_PDU_METER', '智能电表-基础告警模版',
       '电表/PDU 通用监控默认值：离线 + 有功功率越限提示（阈值按机房额定容量自行调整）',
       0
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM alarm_template WHERE template_code = 'BASE_PDU_METER');

INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, NULL, 'OFFLINE', '设备离线', NULL, 15, 2, 1, 1
FROM alarm_template t
WHERE t.template_code = 'BASE_PDU_METER'
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id IS NULL AND i.rule_name = '设备离线');

-- 功率越限：默认 200kW 提示级，覆盖中型机房常态负载，按需调整
INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, p.id, 'METRIC', '有功功率越限', 'GT', 200, 3, 1, 2
FROM alarm_template t, point_definition p
WHERE t.template_code = 'BASE_PDU_METER' AND p.point_code = 'power_kw' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id = p.id AND i.rule_name = '有功功率越限');

-- ==================== 3. PRECISION_AC 基础告警模版 ====================
INSERT INTO alarm_template (template_code, template_name, description, status)
SELECT 'BASE_PRECISION_AC', '精密空调-基础告警模版',
       '精密空调通用监控默认值：回风温度两级越限、送风温度异常（制冷劣化）、离线',
       0
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM alarm_template WHERE template_code = 'BASE_PRECISION_AC');

INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, NULL, 'OFFLINE', '设备离线', NULL, 15, 2, 1, 1
FROM alarm_template t
WHERE t.template_code = 'BASE_PRECISION_AC'
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id IS NULL AND i.rule_name = '设备离线');

-- 回风温度：高温警告/紧急两级（回风≈室温+1.5℃，先于机房环境告警响应）
INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, p.id, 'METRIC', '回风高温警告', 'GT', 32, 2, 1, 2
FROM alarm_template t, point_definition p
WHERE t.template_code = 'BASE_PRECISION_AC' AND p.point_code = 'return_air_temp' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id = p.id AND i.rule_name = '回风高温警告');

INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, p.id, 'METRIC', '回风高温紧急', 'GT', 38, 1, 1, 3
FROM alarm_template t, point_definition p
WHERE t.template_code = 'BASE_PRECISION_AC' AND p.point_code = 'return_air_temp' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id = p.id AND i.rule_name = '回风高温紧急');

INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, p.id, 'METRIC', '回风低温警告', 'LT', 16, 4, 1, 4
FROM alarm_template t, point_definition p
WHERE t.template_code = 'BASE_PRECISION_AC' AND p.point_code = 'return_air_temp' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id = p.id AND i.rule_name = '回风低温警告');

-- 送风温度：制冷正常时≈室温−8℃，偏高说明制冷劣化
INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, p.id, 'METRIC', '送风温度异常偏高', 'GT', 25, 3, 1, 5
FROM alarm_template t, point_definition p
WHERE t.template_code = 'BASE_PRECISION_AC' AND p.point_code = 'supply_air_temp' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id = p.id AND i.rule_name = '送风温度异常偏高');

-- ==================== 4. LEAK_DETECTOR 基础告警模版 ====================
INSERT INTO alarm_template (template_code, template_name, description, status)
SELECT 'BASE_LEAK_DETECTOR', '漏水检测-基础告警模版',
       '漏水检测通用监控默认值：漏水即紧急告警 + 离线',
       0
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM alarm_template WHERE template_code = 'BASE_LEAK_DETECTOR');

INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, NULL, 'OFFLINE', '设备离线', NULL, 15, 2, 1, 1
FROM alarm_template t
WHERE t.template_code = 'BASE_LEAK_DETECTOR'
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id IS NULL AND i.rule_name = '设备离线');

-- 漏水状态：0正常 1告警，EQ 精确命中
INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, p.id, 'METRIC', '漏水告警', 'EQ', 1, 1, 1, 2
FROM alarm_template t, point_definition p
WHERE t.template_code = 'BASE_LEAK_DETECTOR' AND p.point_code = 'leak_status' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id = p.id AND i.rule_name = '漏水告警');

-- ==================== 5. 温湿度基础模版补湿度规则 ====================
-- BASE_TEMP_HUMIDITY（V15 迁移生成）只有温度三级规则，湿度指标无规则可评
INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, p.id, 'METRIC', '湿度偏高', 'GT', 80, 3, 1, 5
FROM alarm_template t, point_definition p
WHERE t.template_code = 'BASE_TEMP_HUMIDITY' AND p.point_code = 'humidity' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id = p.id AND i.rule_name = '湿度偏高');

INSERT INTO alarm_template_item (template_id, point_id, rule_type, rule_name, operator, threshold, alarm_level, enabled, sort_order)
SELECT t.id, p.id, 'METRIC', '湿度过低', 'LT', 20, 4, 1, 6
FROM alarm_template t, point_definition p
WHERE t.template_code = 'BASE_TEMP_HUMIDITY' AND p.point_code = 'humidity' AND p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM alarm_template_item i
                  WHERE i.template_id = t.id AND i.point_id = p.id AND i.rule_name = '湿度过低');

-- ==================== 6. 类型绑定基础模版 ====================
UPDATE device_types t
JOIN alarm_template at2 ON at2.template_code = CONCAT('BASE_', t.type_code)
SET t.alarm_template_id = at2.id
WHERE t.deleted = 0
  AND (t.alarm_template_id IS NULL OR t.alarm_template_id <> at2.id);
