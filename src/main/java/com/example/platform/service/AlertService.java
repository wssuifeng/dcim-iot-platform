package com.example.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.entity.Alert;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AlertService extends IService<Alert> {
    // 查询所有未处理的告警记录
    List<Alert> findUnhandled();

    // 根据设备ID查询该设备产生的所有告警记录
    List<Alert> findByDeviceId(Integer deviceId);

    // 批量处理指定ID的告警（例如标记为已处理），并记录操作人；返回成功处理的条数
    int batchHandle(List<Integer> ids, String handleUser);

    Map<String, Object> getStatsByDeviceId(Integer deviceId);

    Map<String, Object> getStatsByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    List<Map<String, Object>> listDeviceAlertCountByTimeRange(LocalDateTime startTime, LocalDateTime endTime);
}
