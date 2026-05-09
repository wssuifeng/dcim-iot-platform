package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.entity.DeviceData;
import com.example.platform.mapper.DeviceDataMapper;
import com.example.platform.service.DeviceDataService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeviceDataServiceImpl extends ServiceImpl<DeviceDataMapper, DeviceData> implements DeviceDataService {
    public List<DeviceData> findByDeviceId(Integer deviceId) {
        LambdaQueryWrapper<DeviceData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceData::getDeviceId, deviceId)
                .orderByDesc(DeviceData::getCollectTime);
        return list(wrapper);
    }

    public DeviceData findLatestByDeviceId(Integer deviceId) {
        LambdaQueryWrapper<DeviceData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceData::getDeviceId, deviceId)
                .orderByDesc(DeviceData::getCollectTime)
                .last("LIMIT 1");
        return getOne(wrapper);
    }
}
