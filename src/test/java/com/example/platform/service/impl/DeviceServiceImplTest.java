package com.example.platform.service.impl;

import com.example.platform.entity.Device;
import com.example.platform.mapper.DeviceMapper;
import com.example.platform.service.DeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class DeviceServiceImplTest {

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private DeviceService deviceService;

    @Test
    void testConnection() {
        // 测试数据库连接
        assertNotNull(deviceMapper, "DeviceMapper注入失败");
    }

    @Test
    void testSelectById() {
        // 测试根据ID查询
        Device device = deviceMapper.selectById(1);
        assertNotNull(device, "查询结果为空");
        System.out.println("查询结果：" + device);
    }

    @Test
    void testSelectAll() {
        // 测试查询所有
        List<Device> devices = deviceMapper.selectList(null);
        assertNotNull(devices, "查询结果为空");
        assertFalse(devices.isEmpty(), "查询结果数量为0");
        System.out.println("设备数量：" + devices.size());
        devices.forEach(System.out::println);
    }

    @Test
    void testInsert() {
        // 测试插入
        Device device = new Device();
        device.setDeviceName("测试设备");
        device.setDeviceCode("TEST-001");
        device.setStatus(0);
        device.setLocation("测试位置");

        int result = deviceMapper.insert(device);
        assertEquals(1, result, "插入失败");
        assertNotNull(device.getId(), "ID未自动填充");
        System.out.println("插入成功，ID：" + device.getId());

        // 清理测试数据
        deviceMapper.deleteById(device.getId());
    }

    @Test
    void testUpdate() {
        // 测试更新
        Device device = deviceMapper.selectById(1);
        assertNotNull(device, "查询结果为空");

        String originalLocation = device.getLocation();
        device.setLocation("更新后的位置");

        int result = deviceMapper.updateById(device);
        assertEquals(1, result, "更新失败");

        // 恢复原数据
        device.setLocation(originalLocation);
        deviceMapper.updateById(device);

        System.out.println("更新成功");
    }

    @Test
    void testFindByNameLike() {
        // 测试自定义查询
        List<Device> devices = deviceMapper.findByNameLike("温度");
        assertNotNull(devices, "查询结果为空");
        System.out.println("名称包含'温度'的设备数量：" + devices.size());
    }

    @Test
    void testFindOnlineDevices() {
        // 测试Service层查询在线设备
        List<Device> onlineDevices = deviceService.findOnlineDevices();
        assertNotNull(onlineDevices, "在线设备列表不应为null");
        System.out.println("在线设备数量：" + onlineDevices.size());
    }
}