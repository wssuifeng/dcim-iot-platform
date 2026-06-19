package com.example.platform.task;

import com.example.platform.service.DeviceDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/*
 * 类功能:数据清理定时任务，按 TTL 策略清理过期的 device_data 历史数据
 * */
@Slf4j
@Component
public class DataCleanupTask {

    private final DeviceDataService deviceDataService;

    @Value("${task.device-data-ttl-days:90}")
    private int ttlDays;

    public DataCleanupTask(DeviceDataService deviceDataService) {
        this.deviceDataService = deviceDataService;
    }

    /*
     * 方法功能:每天凌晨 2 点清理超过 TTL 的设备数据
     * 参数:无
     * 返回数据:无
     * */
    @Scheduled(cron = "${task.cleanup-device-data-cron:0 0 2 * * ?}")
    public void cleanupExpiredDeviceData() {
        LocalDateTime beforeTime = LocalDateTime.now().minusDays(ttlDays);
        log.info("开始清理 {} 之前的过期设备数据，保留策略={}天", beforeTime, ttlDays);
        int deleted = deviceDataService.cleanDataBefore(beforeTime);
        log.info("过期设备数据清理完成，删除记录数={}", deleted);
    }
}
