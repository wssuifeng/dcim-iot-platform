package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.example.platform.common.exception.DeviceCodeAlreadyExistsException;
import com.example.platform.entity.Device;
import com.example.platform.mapper.DeviceMapper;
import com.example.platform.service.DeviceService;
import com.example.platform.service.DeviceTypeService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 设备服务实现类
 * 继承ServiceImpl后，自动拥有BaseMapper的所有方法
 */
@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {
    private final DeviceTypeService deviceTypeService;

    // 构造器注入 DeviceTypeService（不是自己注入自己！）
    public DeviceServiceImpl(DeviceTypeService deviceTypeService) {
        this.deviceTypeService = deviceTypeService;
    }

    @Override
    public List<Device> findByNameLike(String name) {
        return baseMapper.findByNameLike(name);
    }

    @Override
    public List<Device> findOnlineDevices() {
        // 使用LambdaQueryWrapper构建查询条件
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getStatus, 1);  // status = 1 表示在线
        return list(wrapper);
    }

    @Override
    public boolean updateStatus(Integer id, Integer status) {
        // 使用LambdaUpdateWrapper构建更新条件
        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Device::getId, id)
                .set(Device::getStatus, status);
        return update(wrapper);
    }

    // ========== 重写的 save 方法 ==========
    @Override
    public boolean save(Device device) {
        // 1. 校验设备编码唯一性（内部抛异常）
        deviceCodeExists(device.getDeviceCode());

        // 2. 校验设备类型是否存在（内部抛异常）
        deviceTypeService.typeIdExists(device.getTypeId());

        // 3. 通过后执行保存
        return super.save(device);
    }

    public void deviceCodeExists(String deviceCode){
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceCode,deviceCode);
        if(count(wrapper) > 0){
            throw new DeviceCodeAlreadyExistsException(deviceCode);
        }
    }

}