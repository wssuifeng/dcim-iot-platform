package com.example.platform.task;

import com.example.platform.common.ResultCode;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.mapper.IngestCredentialMapper;
import com.example.platform.service.AlarmEventService;
import com.example.platform.dto.AlarmTriggerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/*
 * 类功能:网关离线检测任务——启用凭证的 last_seen_at 超阈值即产出 GATEWAY_OFFLINE 告警
 *
 * 【判定口径】status=0（启用）凭证：
 *   ① 已上报过：last_seen_at 距今超阈值 → 离线；
 *   ② 从未上报：签发（create_time）超阈值仍无任何上报 → "从未上线"告警
 *     （网关侧配置错误/网络不通时同样能被发现，不留观测盲区）。
 * 【恢复口径】心跳/上报成功时由 IngestCredentialService.refreshLastSeen 联动
 *   autoRecover(null, null, GATEWAY_OFFLINE)；MQTT LWT 由监听器立即触发同一类型。
 * 【告警形态】设备级告警（device_id 为空），复用告警工作流与通知链路。
 * */
@Slf4j
@Component
public class IngestGatewayOfflineMonitorTask {

    private static final String ALARM_TYPE_GATEWAY_OFFLINE = "GATEWAY_OFFLINE";

    private final IngestCredentialMapper ingestCredentialMapper;
    private final AlarmEventService alarmEventService;
    private final com.example.platform.service.CloudRoomService cloudRoomService;

    // 网关离线判定阈值（秒），默认 5 分钟
    @Value("${task.gateway-offline-threshold-seconds:300}")
    private int offlineThresholdSeconds;

    public IngestGatewayOfflineMonitorTask(IngestCredentialMapper ingestCredentialMapper,
                                           AlarmEventService alarmEventService,
                                           com.example.platform.service.CloudRoomService cloudRoomService) {
        this.ingestCredentialMapper = ingestCredentialMapper;
        this.alarmEventService = alarmEventService;
        this.cloudRoomService = cloudRoomService;
    }

    /*
     * 方法功能:每 5 分钟扫描启用凭证，last_seen 超阈值产出网关离线告警
     * 参数:无
     * 返回数据:无
     * */
    @Scheduled(cron = "${task.check-gateway-offline-cron:0 1/5 * * * ?}")
    public void checkGatewayOffline() {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(offlineThresholdSeconds);
        List<IngestCredential> credentials = ingestCredentialMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IngestCredential>()
                        .eq(IngestCredential::getStatus, 0));
        if (credentials.isEmpty()) {
            return;
        }
        for (IngestCredential credential : credentials) {
            try {
                if (credential.getLastSeenAt() != null) {
                    // 已上线过：最近活跃超阈值判离线
                    if (credential.getLastSeenAt().isBefore(deadline)) {
                        triggerGatewayOffline(credential);
                    }
                } else if (credential.getCreateTime() != null
                        && credential.getCreateTime().isBefore(deadline)) {
                    // 从未上线：签发超阈值仍无任何上报（配置错误/网络不通同样可见）
                    triggerNeverOnline(credential);
                }
            } catch (Exception ex) {
                log.warn("网关离线告警触发失败 credentialId={} error={}",
                        credential.getId(), ex.getMessage());
            }
        }
    }

    private String roomNameOf(IngestCredential credential) {
        com.example.platform.entity.CloudRoom room = cloudRoomService.getById(credential.getCloudRoomId());
        return room != null ? room.getRoomName() : String.valueOf(credential.getCloudRoomId());
    }

    private void triggerNeverOnline(IngestCredential credential) {
        long minutes = java.time.Duration.between(credential.getCreateTime(), LocalDateTime.now()).toMinutes();
        AlarmTriggerContext context = AlarmTriggerContext.builder()
                .deviceId(null)
                .pointId(null)
                .alarmType(ALARM_TYPE_GATEWAY_OFFLINE)
                .alarmLevel(3)
                .alarmMessage(String.format("网关从未上线：机房[%s]签发超过%d分钟无任何心跳/上报，请检查网关配置",
                        roomNameOf(credential), Math.max(minutes, 1)))
                .cloudRoomId(credential.getCloudRoomId())
                .build();
        var result = alarmEventService.trigger(context);
        if (result.isNotifiable()) {
            log.warn("网关从未上线告警已触发 roomId={} credentialId={}",
                    credential.getCloudRoomId(), credential.getId());
        }
    }

    private void triggerGatewayOffline(IngestCredential credential) {
        long minutes = Duration.between(credential.getLastSeenAt(), LocalDateTime.now()).toMinutes();
        AlarmTriggerContext context = AlarmTriggerContext.builder()
                .deviceId(null)
                .pointId(null)
                .alarmType(ALARM_TYPE_GATEWAY_OFFLINE)
                .alarmLevel(2)
                .alarmMessage(String.format("网关离线：机房[%s]超过%d分钟无心跳/上报",
                        roomNameOf(credential), Math.max(minutes, 1)))
                .cloudRoomId(credential.getCloudRoomId())
                .build();
        var result = alarmEventService.trigger(context);
        if (result.isNotifiable()) {
            log.warn("网关离线告警已触发 roomId={} credentialId={}",
                    credential.getCloudRoomId(), credential.getId());
        }
    }
}
