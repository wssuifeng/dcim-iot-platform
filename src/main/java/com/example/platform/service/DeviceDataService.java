package com.example.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.entity.DeviceData;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface DeviceDataService extends IService<DeviceData> {
    List<DeviceData> findByDeviceId(Integer deviceId);

    DeviceData findLatestByDeviceId(Integer deviceId);

    List<DeviceData> findByDeviceIdAndTimeRange(Integer deviceId,
                                                LocalDateTime startTime, LocalDateTime endTime);

    Map<String, Object> getStatistics(Integer deviceId,
                                      LocalDateTime startTime, LocalDateTime endTime);

    void saveWithAlertCheck(DeviceData deviceData);
}
