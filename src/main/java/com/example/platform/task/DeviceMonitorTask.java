package com.example.platform.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.entity.Alert;
import com.example.platform.entity.Device;
import com.example.platform.service.AlertService;
import com.example.platform.service.DeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DeviceMonitorTask {

    private final DeviceService deviceService;
    private final AlertService alertService;

    public DeviceMonitorTask(DeviceService deviceService, AlertService alertService) {
        this.deviceService = deviceService;
        this.alertService = alertService;
    }

    /*
     * 方法功能: 定时检查设备在线状态，为离线超过阈值的设备生成离线告警
     * 触发时机: 根据配置 ${task.check-device-cron} 周期性执行
     * 处理逻辑:
     *   1. 计算时间阈值（当前时间减去允许的最大无心跳间隔）
     *   2. 查询 last_online_time 为空或早于阈值的设备
     *   3. 对每个离线设备，若不存在未处理的离线告警，则生成一条新告警
     *   4. 批量保存告警
     * 参数: 无
     * 返回数据: 无
     */
    @Scheduled(cron = "${task.check-device-cron}")
    public void checkDeviceOnlineStatus() {
        // 1. 计算判定阈值（例如当前时间前300秒）
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(300);

        // 2. 查询离线设备（含从未上线）
        List<Device> offlineDevices = deviceService.getOnlineDetection(threshold);

        if (offlineDevices.isEmpty()) {
            return;
        }

        // 3. 查询这些设备中哪些已有未处理的“离线告警”，避免重复生成
        List<Integer> deviceIds = offlineDevices.stream()
                .map(Device::getId)
                .toList();

        // 若无，可循环查询或通过 SQL IN 实现
        List<Alert> existingOfflineAlerts = alertService.list(
                new LambdaQueryWrapper<Alert>()
                        .in(Alert::getDeviceId, deviceIds)
                        .eq(Alert::getAlertType, "离线告警")
                        .eq(Alert::getIsHandled, 0)
        );
        // 构建设备ID到现有告警的映射（存在任一未处理告警即视为已报）
        Set<Integer> alreadyAlertedDeviceIds = existingOfflineAlerts.stream()
                .map(Alert::getDeviceId)
                .collect(Collectors.toSet());

        // 4. 为未报过的离线设备生成告警实体
        List<Alert> newAlerts = new ArrayList<>();
        for (Device device : offlineDevices) {
            if (alreadyAlertedDeviceIds.contains(device.getId())) {
                continue;   // 已有未处理告警，跳过
            }
            Alert alert = new Alert();
            alert.setDeviceId(device.getId());
            alert.setAlertType("离线告警");
            alert.setAlertLevel(1);                     // 可根据需要从配置读取
            alert.setAlertMessage("设备已离线超过5分钟");
            alert.setIsHandled(0);
            newAlerts.add(alert);
        }

        // 5. 批量保存新告警
        if (!newAlerts.isEmpty()) {
            alertService.saveBatch(newAlerts);
            log.info("🚨 批量生成离线告警：设备数量={}", newAlerts.size());
        }
    }
}