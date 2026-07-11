-- ============================================================
-- 文件：src/test/resources/test-data.sql
-- 功能：真实数据库集成测试（@SpringBootTest）的事务内预置数据
-- 加载方式：@Sql(..., config = @SqlConfig(encoding = "UTF-8"))
-- 设计原则：使用负数专用 ID，避免覆盖 Flyway 创建的产品基础数据；测试结束统一回滚
-- ============================================================

-- 先删除设备，再删除类型，遵守外键依赖顺序。
DELETE FROM devices WHERE id = -1001;
DELETE FROM device_types WHERE id = -1001;

INSERT INTO device_types (id, type_name, type_code, description) VALUES
    (-1001, '温度传感器', 'TEST_TEMP_SENSOR', '环境温度监测（测试数据）');

INSERT INTO devices (id, device_name, device_code, type_id, status, location, ip_address) VALUES
    (-1001, '1号楼温度传感器', 'TEST-TEMP-001', -1001, 1, '1号楼3层东侧', '192.168.1.101');
