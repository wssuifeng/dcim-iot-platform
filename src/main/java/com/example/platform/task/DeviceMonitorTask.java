package com.example.platform.task;

import com.example.platform.entity.Device;
import com.example.platform.service.AlertService;
import com.example.platform.service.DeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/*
 * 类功能:设备在线状态定时监控任务，周期性检测离线设备并生成离线告警
 * */
@Slf4j
@Component
public class DeviceMonitorTask {

    private final DeviceService deviceService;
    private final AlertService alertService;

    @Value("${task.offline-threshold-seconds:300}")
    private int offlineThresholdSeconds;

    public DeviceMonitorTask(DeviceService deviceService, AlertService alertService) {
        this.deviceService = deviceService;
        this.alertService = alertService;
    }

    /*
     * 方法功能:定时检查设备在线状态，为离线超过阈值的设备生成离线告警
     * 参数:无
     * 返回数据:无
     * */
    @Scheduled(cron = "${task.check-device-cron}")
    public void checkDeviceOnlineStatus() {
        // 1. 计算判定阈值时间
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(offlineThresholdSeconds);

        // 2. 查询离线设备
        List<Device> offlineDevices = deviceService.getOnlineDetection(threshold);

        if (offlineDevices.isEmpty()) {
            return;
        }

        // 3. 委托AlertService生成离线告警（含去重逻辑）
        int offlineThresholdMinutes = offlineThresholdSeconds / 60;
        int count = alertService.createOfflineAlertsForDevices(offlineDevices, offlineThresholdMinutes);
        if (count > 0) {
            log.info("🚨 批量生成离线告警：设备数量={}", count);
        }
    }
}
