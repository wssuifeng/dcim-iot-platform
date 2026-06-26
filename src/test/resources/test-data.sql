-- ============================================================
-- 文件：src/test/resources/test-data.sql
-- 功能：真实数据库集成测试（@SpringBootTest）的预置数据
-- 加载方式：在测试类上加 @Sql(scripts = "classpath:test-data.sql")
-- 设计原则：幂等执行（DELETE + INSERT 模式），避免主键/唯一键冲突
-- 适用测试：DeviceServiceImplTest / DeviceMapperTest / DeviceServiceTest
-- 不适用于：Mockito 纯单元测试（AlertServiceImplTest 等）
-- ============================================================

-- 关闭外键检查，保证清理顺序无关
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 设备类型：必须存在 id=1，作为 devices.type_id 外键依赖
-- ============================================================
DELETE FROM device_types WHERE id = 1;
INSERT INTO device_types (id, type_name, type_code, description) VALUES
    (1, '温度传感器', 'TEMP_SENSOR', '环境温度监测（测试数据）');

-- ============================================================
-- 2. 设备：必须存在 id=1 且 device_code='TEMP-001'
--    满足 DeviceServiceTest.testGetById 的硬编码断言
-- ============================================================
DELETE FROM devices WHERE id = 1;
INSERT INTO devices (id, device_name, device_code, type_id, status, location, ip_address) VALUES
    (1, '1号楼温度传感器', 'TEMP-001', 1, 1, '1号楼3层东侧', '192.168.1.101');

SET FOREIGN_KEY_CHECKS = 1;
