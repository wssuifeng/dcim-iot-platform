package com.example.platform.mapper;

import com.example.platform.entity.Device;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实数据库集成测试：testCountByStatus 需要表非空，由 test-data.sql 预置数据。
 * @Transactional 保证每个测试方法的数据修改自动回滚。
 */
@SpringBootTest
@Transactional
@Sql(scripts = "classpath:test-data.sql", config = @SqlConfig(encoding = "UTF-8"))
class DeviceMapperTest {

    @Autowired
    private DeviceMapper deviceMapper;

    @Test
    void testFindByNameLike() {
        List<Device> devices = deviceMapper.findByNameLike("温度");
        assertNotNull(devices, "查询结果不应为null");
        devices.forEach(d -> assertTrue(d.getDeviceName().contains("温度"),
                "结果中包含不匹配的设备: " + d.getDeviceName()));
    }

    @Test
    void testFindByNameLikeNoMatch() {
        List<Device> devices = deviceMapper.findByNameLike("不存在的设备名XYZ999");
        assertNotNull(devices, "查询结果不应为null");
        assertTrue(devices.isEmpty(), "无匹配时应返回空列表");
    }

    @Test
    void testCountByStatus() {
        List<Map<String, Object>> stats = deviceMapper.countByStatus();
        assertNotNull(stats, "统计结果不应为null");
        assertFalse(stats.isEmpty(), "统计结果不应为空");

        for (Map<String, Object> stat : stats) {
            assertTrue(stat.containsKey("status"), "每条记录应包含status字段");
            assertTrue(stat.containsKey("count"), "每条记录应包含count字段");
            Object count = stat.get("count");
            assertTrue(count instanceof Number, "count应为数字类型");
            assertTrue(((Number) count).longValue() >= 0, "count应大于等于0");
        }
    }

    @Test
    void testGetOnlineDetection() {
        LocalDateTime checkTime = LocalDateTime.now().minusSeconds(300);
        List<Device> devices = deviceMapper.getOnlineDetection(checkTime);
        assertNotNull(devices, "查询结果不应为null");

        for (Device device : devices) {
            assertNotNull(device.getLastOnlineTime(),
                    "离线检测结果的设备应有last_online_time");
            assertTrue(device.getLastOnlineTime().isBefore(checkTime),
                    "离线检测结果的last_online_time应早于阈值时间");
            assertNotEquals(0, device.getStatus(),
                    "离线检测结果不应包含已离线(status=0)的设备");
        }
    }

    @Test
    void testGetOnlineDetectionWithFutureTime() {
        LocalDateTime futureTime = LocalDateTime.now().plusHours(1);
        List<Device> devices = deviceMapper.getOnlineDetection(futureTime);
        assertNotNull(devices, "查询结果不应为null");
    }

    @Test
    void testInsertAndFindByNameLike() {
        Device device = new Device();
        device.setDeviceName("MapperTest专用设备");
        device.setDeviceCode("MAPPER-TEST-001");
        device.setTypeId(-1001);
        device.setStatus(1);
        device.setLocation("测试位置");

        deviceMapper.insert(device);
        assertNotNull(device.getId(), "插入后ID应自动填充");

        List<Device> found = deviceMapper.findByNameLike("MapperTest专用");
        assertFalse(found.isEmpty(), "应能查到刚插入的设备");
        assertTrue(found.stream().anyMatch(d -> d.getDeviceCode().equals("MAPPER-TEST-001")),
                "查询结果应包含刚插入的设备");
    }
}
