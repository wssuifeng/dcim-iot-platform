-- ============================================================
-- Flyway V10: 修复设备集成测试污染的产品基础数据
--
-- 历史测试夹具曾固定删除 device_types.id=1，并在中文 Windows 环境下
-- 以错误字符集写入 TEMP_SENSOR / TEMP-001，覆盖 V3 创建的 UPS 基础类型。
-- 本迁移仅清理可精确识别的测试记录，并恢复五类产品设备类型中的 UPS。
-- ============================================================

-- 清理测试产生且已经逻辑删除的临时设备，不触碰组织内的正式设备。
DELETE FROM devices
WHERE device_code LIKE 'TEST-INSERT-%'
  AND deleted = 1
  AND organization_id IS NULL
  AND cloud_room_id IS NULL;

-- TEMP-001 是旧 test-data.sql 固定写入的无组织测试设备。
DELETE FROM devices
WHERE id = 1
  AND device_code = 'TEMP-001'
  AND type_id = 1
  AND organization_id IS NULL
  AND cloud_room_id IS NULL;

-- 当 UPS 已被测试夹具覆盖时，原位恢复 V3 的基础类型。
UPDATE device_types
SET type_name = '不间断电源',
    type_code = 'UPS',
    description = 'UPS 不间断电源设备',
    deleted = 0
WHERE id = 1
  AND type_code = 'TEMP_SENSOR'
  AND NOT EXISTS (
      SELECT 1
      FROM (SELECT id FROM device_types WHERE type_code = 'UPS') AS existing_ups
  );

-- 对非标准历史状态兜底：若仍不存在 UPS，则补建该基础类型。
INSERT INTO device_types (type_name, type_code, description)
SELECT '不间断电源', 'UPS', 'UPS 不间断电源设备'
WHERE NOT EXISTS (
    SELECT 1 FROM device_types WHERE type_code = 'UPS'
);
