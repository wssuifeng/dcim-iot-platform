package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.entity.Alert;
import com.example.platform.entity.Device;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/*
 * 类功能:告警服务接口，定义告警的查询、批量处理、统计及离线告警生成等业务方法
 * */
public interface AlertService extends IService<Alert> {

    List<Alert> findUnhandled();

    List<Alert> findByDeviceId(Integer deviceId);

    int batchHandle(List<Integer> ids, String handleUser);

    Map<String, Object> getStatsByDeviceId(Integer deviceId);

    Map<String, Object> getStatsByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    List<Map<String, Object>> listDeviceAlertCountByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    int createOfflineAlertsForDevices(List<Device> offlineDevices, int offlineThresholdMinutes);

    Page<Alert> pageByCondition(Integer current, Integer size, Integer deviceId,
                                 Integer isHandled, Integer alertLevel,
                                 LocalDateTime startTime, LocalDateTime endTime);
}
