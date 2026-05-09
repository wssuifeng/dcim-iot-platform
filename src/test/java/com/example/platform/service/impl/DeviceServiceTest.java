package com.example.platform.service.impl;

import com.example.platform.entity.Device;
import com.example.platform.common.exception.DeviceNotFoundException;
import com.example.platform.service.DeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class DeviceServiceTest {

    @Autowired
    private DeviceService deviceService;

    @Test
    void testListAllDevices() {
        List<Device> devices = deviceService.list();
        assertNotNull(devices);
        assertFalse(devices.isEmpty());
    }

    @Test
    void testGetById() {
        Device device = deviceService.getById(1);
        assertNotNull(device);
        assertEquals("TEMP-001", device.getDeviceCode());
    }

    @Test
    void testGetByIdNotFound() {
        Device device = deviceService.getById(9999);
        assertNull(device);
    }

    @Test
    void testGetDeviceOrThrow() {
        assertThrows(DeviceNotFoundException.class, () -> deviceService.getDeviceOrThrow(9999));
    }

    @Test
    void testSaveDevice() {
        Device device = new Device();
        device.setDeviceName("测试设备");
        device.setDeviceCode("TEST-UNIT-001");
        device.setStatus(0);
        device.setTypeId(1);
        boolean saved = deviceService.save(device);
        assertTrue(saved);
        assertNotNull(device.getId());
        assertNotNull(device.getCreateTime());
    }

    @Test
    void testUpdateDevice() {
        Device device = deviceService.getById(1);
        device.setLocation("更新后的位置");
        boolean updated = deviceService.updateById(device);
        assertTrue(updated);
        Device updatedDevice = deviceService.getById(1);
        assertEquals("更新后的位置", updatedDevice.getLocation());
    }

    @Test
    void testDeleteDevice() {
        Device device = new Device();
        device.setDeviceName("待删除设备");
        device.setDeviceCode("TEST-DEL-001");
        device.setTypeId(1);
        deviceService.save(device);
        Integer id = device.getId();
        boolean removed = deviceService.removeById(id);
        assertTrue(removed);
        assertNull(deviceService.getById(id));
    }

    @Test
    void testFindOnlineDevices() {
        List<Device> onlineDevices = deviceService.findOnlineDevices();
        assertNotNull(onlineDevices);
        onlineDevices.forEach(d -> assertEquals(1, d.getStatus()));
    }

    @Test
    void testFindByNameLike() {
        List<Device> devices = deviceService.findByNameLike("温度");
        assertNotNull(devices);
        devices.forEach(d -> assertTrue(d.getDeviceName().contains("温度")));
    }

    @Test
    void testUpdateStatus() {
        boolean updated = deviceService.updateStatus(1, 2);
        assertTrue(updated);
        Device device = deviceService.getById(1);
        assertEquals(2, device.getStatus());
        deviceService.updateStatus(1, 1);
    }

    @Test
    void testPagination() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Device> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Device> result =
                deviceService.page(page);
        assertTrue(result.getTotal() >= 0);
        assertNotNull(result.getRecords());
    }
}