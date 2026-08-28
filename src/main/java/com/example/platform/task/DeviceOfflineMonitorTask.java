package com.example.platform.task;

import com.example.platform.dto.AlarmTriggerContext;
import com.example.platform.dto.PointAlarmRuleVO;
import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.AlarmTemplateItem;
import com.example.platform.entity.Device;
import com.example.platform.mapper.AlarmTemplateItemMapper;
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.AlarmTemplateService;
import com.example.platform.service.DeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/*
 * 类功能:设备离线检测任务，双向扫描（触发离线告警/恢复离线告警），阈值取 OFFLINE 规则降级解析
 *
 * 【方向①该告警的】
 *   阈值解析: findEffectiveRules 的 OFFLINE 规则（设备改绑模版→类型基础模版，兜底 task.offline-threshold-seconds）
 *   扫描: getOnlineDetection(now - 最小生效阈值) 粗筛 → 逐台按各自生效阈值精判
 *   动作: AlarmEventService.trigger（pointId=NULL, alarmType=OFFLINE，活动告警去重）+
 *         DeviceService.updateStatus(id, 0)（D20 设备状态闭环）
 *
 * 【方向②该恢复的】（D21，延迟≤一个检测周期）
 *   取活动离线告警 → 设备 last_online_time 已新于各自生效阈值 → autoRecover → T3 推 ALARM_RECOVERED 摘要
 * */
@Slf4j
@Component
public class DeviceOfflineMonitorTask {

    public static final String ALARM_TYPE_OFFLINE = "OFFLINE";

    private final DeviceService deviceService;
    private final AlarmTemplateService templateService;
    private final AlarmEventService alarmEventService;
    private final AlarmTemplateItemMapper templateItemMapper;

    @Value("${task.offline-threshold-seconds:300}")
    private int offlineThresholdSeconds;

    public DeviceOfflineMonitorTask(DeviceService deviceService,
                                    AlarmTemplateService templateService,
                                    AlarmEventService alarmEventService,
                                    AlarmTemplateItemMapper templateItemMapper) {
        this.deviceService = deviceService;
        this.templateService = templateService;
        this.alarmEventService = alarmEventService;
        this.templateItemMapper = templateItemMapper;
    }

    /*
     * 方法功能:定时双向扫描（触发离线告警 + 恢复已在线的离线告警）
     * 参数:无
     * 返回数据:无
     * */
    @Scheduled(cron = "${task.check-device-cron}")
    public void checkOffline() {
        try {
            triggerOfflineAlarms();
            recoverOnlineAlarms();
        } catch (Exception ex) {
            // 任务级容错：本轮失败不影响下一轮调度
            log.error("离线检测任务执行异常", ex);
        }
    }

    private void triggerOfflineAlarms() {
        // 1. 全部启用模版下 OFFLINE 规则的最小阈值用于粗筛（避免 N 次全表扫描；精判按各设备生效模版阈值）
        int minRuleMinutes = templateItemMapper.selectEnabledOfflineItems().stream()
                .map(AlarmTemplateItem::getThreshold)
                .filter(t -> t != null)
                .map(BigDecimal::intValue)
                .min(Integer::compareTo)
                .orElse(Integer.MAX_VALUE);
        int fallbackMinutes = Math.max(1, offlineThresholdSeconds / 60);
        int coarseMinutes = Math.min(minRuleMinutes, fallbackMinutes);

        LocalDateTime coarseThreshold = LocalDateTime.now().minusMinutes(coarseMinutes);
        List<Device> candidates = deviceService.getOnlineDetection(coarseThreshold);
        if (candidates.isEmpty()) {
            return;
        }

        int triggered = 0;
        for (Device device : candidates) {
            try {
                int minutes = resolveOfflineMinutes(device.getId(), fallbackMinutes);
                LocalDateTime deadline = LocalDateTime.now().minusMinutes(minutes);
                if (device.getLastOnlineTime() == null || device.getLastOnlineTime().isBefore(deadline)) {
                    AlarmTriggerContext context = AlarmTriggerContext.builder()
                            .deviceId(device.getId())
                            .pointId(null)
                            .alarmType(ALARM_TYPE_OFFLINE)
                            .alarmLevel(resolveOfflineLevel(device.getId()))
                            .alarmMessage(String.format("设备离线：超过%d分钟未上报数据", minutes))
                            .cloudRoomId(device.getCloudRoomId())
                            .build();
                    var result = alarmEventService.trigger(context);
                    if (result.isNotifiable()) {
                        deviceService.updateStatus(device.getId(), 0);
                        triggered++;
                    }
                }
            } catch (Exception ex) {
                log.warn("离线告警触发失败 deviceId={} error={}", device.getId(), ex.getMessage());
            }
        }
        if (triggered > 0) {
            log.info("离线检测：本轮触发离线告警设备数={}", triggered);
        }
    }

    private void recoverOnlineAlarms() {
        List<AlarmEvent> activeOfflineAlarms = alarmEventService.listActiveByAlarmType(ALARM_TYPE_OFFLINE);
        int recovered = 0;
        for (AlarmEvent alarm : activeOfflineAlarms) {
            try {
                Device device = deviceService.getById(alarm.getDeviceId());
                if (device == null || device.getLastOnlineTime() == null) {
                    continue;
                }
                int minutes = resolveOfflineMinutes(alarm.getDeviceId(),
                        Math.max(1, offlineThresholdSeconds / 60));
                LocalDateTime deadline = LocalDateTime.now().minusMinutes(minutes);
                if (device.getLastOnlineTime().isAfter(deadline)) {
                    if (alarmEventService.autoRecover(alarm.getDeviceId(), null, ALARM_TYPE_OFFLINE)) {
                        recovered++;
                    }
                }
            } catch (Exception ex) {
                log.warn("离线恢复检查失败 alarmEventId={} error={}", alarm.getId(), ex.getMessage());
            }
        }
        if (recovered > 0) {
            log.info("离线检测：本轮自动恢复离线告警数={}", recovered);
        }
    }

    private int resolveOfflineMinutes(Integer deviceId, int fallbackMinutes) {
        List<PointAlarmRuleVO> rules = templateService.findEffectiveRules(deviceId, null);
        return rules.stream()
                .filter(r -> ALARM_TYPE_OFFLINE.equals(r.getRuleType()))
                .filter(r -> r.getThreshold() != null)
                .map(r -> r.getThreshold().intValue())
                .min(Integer::compareTo)
                .orElse(fallbackMinutes);
    }

    private int resolveOfflineLevel(Integer deviceId) {
        List<PointAlarmRuleVO> rules = templateService.findEffectiveRules(deviceId, null);
        return rules.stream()
                .filter(r -> ALARM_TYPE_OFFLINE.equals(r.getRuleType()))
                .map(PointAlarmRuleVO::getAlarmLevel)
                .filter(l -> l != null)
                .min(Integer::compareTo)
                .orElse(2);
    }
}
