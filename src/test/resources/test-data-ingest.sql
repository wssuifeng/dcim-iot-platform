-- ============================================================
-- 文件：src/test/resources/test-data-ingest.sql
-- 功能：数据接收门集成测试的事务内预置数据（负数专用 ID，测试结束回滚）
-- ============================================================

DELETE FROM ingest_credential WHERE id IN (-2001, -2002);
DELETE FROM device_point_binding WHERE device_id IN (-2001, -2002, -2003);
DELETE FROM devices WHERE id IN (-2001, -2002, -2003);
DELETE FROM point_definition WHERE id = -2001;
DELETE FROM sys_cloud_room WHERE id IN (-2001, -2002);

INSERT INTO sys_cloud_room (id, room_name, room_code, location) VALUES
    (-2001, '接收门测试机房A', 'IT-room-a', '测试位置A'),
    (-2002, '接收门测试机房B', 'IT-room-b', '测试位置B');

INSERT INTO point_definition (id, point_code, point_name, unit, data_type, description) VALUES
    (-2001, 'IT_TEST_TEMP', '接收门测试温度', '℃', 'NUMBER', '数据接收门集成测试点位');



INSERT INTO devices (id, device_name, device_code, type_id, cloud_room_id, status, location) VALUES
    (-2001, '接收门测试设备A', 'IT-DEV-A', 1, -2001, 1, '测试机柜A'),
    (-2002, '接收门测试设备B', 'IT-DEV-B', 1, -2002, 1, '测试机柜B'),
    (-2003, '接收门测试设备C', 'IT-DEV-C', 1, -2001, 1, '测试机柜C');

-- 自动注册用模板：type 1(UPS, V3 迁移自带) → point -2001（V9 种子不在测试 profile，须自备模板行）
DELETE FROM device_type_point_template WHERE type_id = 1 AND point_id = -2001;
INSERT INTO device_type_point_template (type_id, point_id, is_required, sort_order) VALUES
    (1, -2001, 1, 1);

-- 设备A 绑定启用（含 V9 种子点位 voltage_a 供幂等键范围用例）；
-- 设备B 跨机房（越权用例）；设备C 同机房但绑定停用（SAMPLE_POINT_NOT_BOUND 失败路径）
INSERT INTO device_point_binding (device_id, point_id, status) VALUES
    (-2001, -2001, 0),
    (-2001, 3, 0),
    (-2002, -2001, 1),
    (-2003, -2001, 1);
