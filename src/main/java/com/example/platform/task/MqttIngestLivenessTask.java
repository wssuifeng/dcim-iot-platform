package com.example.platform.task;

import com.example.platform.dto.AlarmTriggerContext;
import com.example.platform.ingest.mqtt.IngestMqttListener;
import com.example.platform.service.AlarmEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
 * 类功能:MQTT 数据接收门假死巡检任务（FR-B-001 配套自监控，W-001）——
 *   Paho 显示"已连接"但持续收不到任何订阅消息（重订阅失败/订阅静默失效/broker
 *   路由异常）时产出自指告警，复用告警工作流与通知链路（与 PARTITION_SUPPLY_FAILED
 *   同模式）。连接断开不算假死（重连机制负责），只有"已连接 + 长时间零消息"才告警。
 * */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ingest.mqtt.enabled", havingValue = "true")
public class MqttIngestLivenessTask {

    public static final String ALARM_TYPE_MQTT_STALL = "MQTT_INGEST_STALL";

    private final IngestMqttListener ingestMqttListener;
    private final AlarmEventService alarmEventService;

    // 连接正常但无任何上报消息的静默阈值（秒）：超过即判疑似假死
    @Value("${task.mqtt-liveness-silent-seconds:600}")
    private long silentThresholdSeconds;

    /*
     * 方法功能:定时巡检——已连接且超过静默阈值无任何上报消息时产出自指告警
     * 参数:无
     * 返回数据:无
     * 说明:trigger() 对同型活动告警去重，持续假死不会重复刷告警；
     *   恢复无需显式闭环动作（通道恢复消息到达即说明假死解除）
     * */
    @Scheduled(cron = "${task.mqtt-liveness-cron:0 2/5 * * * ?}")
    public void check() {
        try {
            if (!ingestMqttListener.isMqttConnected()) {
                return;
            }
            long silentMillis = ingestMqttListener.millisSinceLastIngestMessage();
            if (silentMillis <= silentThresholdSeconds * 1000) {
                return;
            }
            triggerStallAlarm(silentMillis);
        } catch (Exception ex) {
            log.error("MQTT 假死巡检异常 error={}", ex.getMessage(), ex);
        }
    }

    private void triggerStallAlarm(long silentMillis) {
        String message = "MQTT 数据接收门疑似假死：连接正常但已 " + (silentMillis / 1000)
                + " 秒未收到任何上报消息（订阅可能静默失效），请检查 broker 与订阅状态";
        log.error(message);
        try {
            AlarmTriggerContext context = AlarmTriggerContext.builder()
                    .deviceId(null)
                    .pointId(null)
                    .alarmType(ALARM_TYPE_MQTT_STALL)
                    .alarmLevel(2)
                    .alarmMessage(message)
                    .cloudRoomId(null)
                    .build();
            alarmEventService.trigger(context);
        } catch (Exception ex) {
            // 自指告警写库失败（常见根因=DB 故障）：降级直打 stderr 留痕（与 W-017 同口径）
            System.err.println("[MQTT-LIVENESS-DEGRADED] " + message
                    + " | alarm write failed: " + ex.getMessage());
        }
    }
}
