package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.example.platform.entity.Device;
import com.example.platform.mapper.DeviceMapper;
import com.example.platform.service.DeviceService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 设备服务实现类
 * 继承ServiceImpl后，自动拥有BaseMapper的所有方法
 */
@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

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
}