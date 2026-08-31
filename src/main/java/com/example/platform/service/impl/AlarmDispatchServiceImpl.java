package com.example.platform.service.impl;

import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.AlarmNotificationLog;
import com.example.platform.entity.SysUser;
import com.example.platform.event.AlarmEventRecoveredEvent;
import com.example.platform.event.AlarmEventTriggeredEvent;
import com.example.platform.mapper.AlarmNotificationLogMapper;
import com.example.platform.mapper.SysUserMapper;
import com.example.platform.security.UserScopedWebSocketPublisher;
import com.example.platform.security.WebSocketSummary;
import com.example.platform.service.AlarmDispatchService;
import com.example.platform.service.AlarmEmailSender;
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.AlarmRecipientResolver;
import com.example.platform.service.AlarmWebhookDispatcher;
import com.example.platform.service.InAppNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/*
 * 类功能:告警分发服务实现类（T3 分发所有者，无事务，逐通道容错）
 *
 * 【分发流程（§8.1）】
 *   1. AlarmRecipientResolver 解析收件人（组织+机房权限）；空集→恒写站内+一条无收件人日志
 *   2. SimpUserRegistry 在线分组（principal name = userId 字符串）
 *   3. 在线组：逐人 UserScopedWebSocketPublisher 定向摘要 + 每人一行 WEBSOCKET 日志
 *      （status=1 表示已投递到用户队列，非已读，D28）
 *   4. 离线组邮件（D23 逐人兜底）：查 sys_user.email 过滤空；notify.mail.enabled=false
 *      时记 status=2 日志（不静默）；发送由 AlarmEmailSender 承担并逐人写日志
 *   5. 恒写 in_app_notification（全部收件人）
 *   6. 全程逐通道 try-catch：任何失败不影响告警与其他通道
 *
 * 【事件类型（D13）】ALARM_CREATED / ALARM_UPGRADED / ALARM_RECOVERED，
 *   resourceType=ALARM_EVENT，前端收到摘要后 REST 回查详情。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmDispatchServiceImpl implements AlarmDispatchService {

    public static final String RESOURCE_TYPE_ALARM_EVENT = "ALARM_EVENT";

    private static final int LOG_STATUS_DELIVERED = 1;
    private static final int LOG_STATUS_FAILED = 2;

    private final AlarmEventService alarmEventService;
    private final AlarmRecipientResolver alarmRecipientResolver;
    private final SimpUserRegistry simpUserRegistry;
    private final UserScopedWebSocketPublisher webSocketPublisher;
    private final AlarmEmailSender alarmEmailSender;
    private final InAppNotificationService inAppNotificationService;
    private final AlarmNotificationLogMapper notificationLogMapper;
    private final SysUserMapper sysUserMapper;
    private final AlarmWebhookDispatcher webhookDispatcher;
    private final com.example.platform.service.AlarmActionWebhookService actionWebhookService;

    @Value("${notify.mail.enabled:false}")
    private boolean mailEnabled;

    @Override
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(AlarmEventTriggeredEvent event) {
        try {
            AlarmEvent alarmEvent = alarmEventService.getById(event.alarmEventId());
            if (alarmEvent == null) {
                log.warn("分发目标告警不存在 alarmEventId={}", event.alarmEventId());
                return;
            }
            Set<Long> recipients = resolveRecipients(alarmEvent);
            if (recipients.isEmpty()) {
                saveLog(alarmEvent, "WEBSOCKET", "NONE", LOG_STATUS_FAILED, "无符合组织/机房权限的收件人",
                        "告警产生但无可通知用户");
                notifyInApp(alarmEvent, recipients, event.kind());
                return;
            }

            // 在线分组：SimpUserRegistry principal name = userId 字符串
            List<Long> onlineUsers = new ArrayList<>();
            List<Long> offlineUsers = new ArrayList<>();
            for (Long userId : recipients) {
                if (simpUserRegistry.getUser(String.valueOf(userId)) != null) {
                    onlineUsers.add(userId);
                } else {
                    offlineUsers.add(userId);
                }
            }

            WebSocketSummary summary = buildSummary(alarmEvent, resolveTriggeredEventType(event.kind()));
            for (Long userId : onlineUsers) {
                pushWebSocket(alarmEvent, userId, summary);
            }
            sendFallbackEmail(alarmEvent, offlineUsers);
            notifyInApp(alarmEvent, recipients, event.kind());
            // S8-2：IM webhook 渠道并行分发（逐渠道容错+退避重试在分发器内完成）
            webhookDispatcher.dispatch(alarmEvent, summary.eventType());
            // S8-9：下行动作回调（规则级开关；触发/升级即动作，RESEND 通知不重复触发动作）
            if (!AlarmEventTriggeredEvent.KIND_RESEND.equals(event.kind())) {
                actionWebhookService.invokeAction(alarmEvent, event.templateItemId());
            }
            log.info("告警分发完成 alarmEventId={} kind={} recipients={} online={} offline={}",
                    alarmEvent.getId(), event.kind(), recipients.size(),
                    onlineUsers.size(), offlineUsers.size());
        } catch (Exception ex) {
            // T3 容错：分发失败不影响已提交的告警
            log.error("告警分发异常 alarmEventId={}", event.alarmEventId(), ex);
        }
    }

    @Override
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecovered(AlarmEventRecoveredEvent event) {
        try {
            AlarmEvent alarmEvent = alarmEventService.getById(event.alarmEventId());
            if (alarmEvent == null) {
                return;
            }
            Set<Long> recipients = resolveRecipients(alarmEvent);
            if (recipients.isEmpty()) {
                return;
            }
            WebSocketSummary summary = buildSummary(alarmEvent, "ALARM_RECOVERED");
            for (Long userId : recipients) {
                if (simpUserRegistry.getUser(String.valueOf(userId)) != null) {
                    pushWebSocket(alarmEvent, userId, summary);
                }
            }
            // D24 恢复不打扰：仅 WS 摘要 + 站内通知，不发邮件
            notifyInApp(alarmEvent, recipients, "RECOVERED");
            // S8-2：恢复事件也推 IM webhook（恢复在 IM 渠道是关键信息，邮件策略不适用）
            webhookDispatcher.dispatch(alarmEvent, "ALARM_RECOVERED");
        } catch (Exception ex) {
            log.error("告警恢复分发异常 alarmEventId={}", event.alarmEventId(), ex);
        }
    }

    // ===================== 内部辅助 =====================

    private Set<Long> resolveRecipients(AlarmEvent alarmEvent) {
        try {
            return alarmRecipientResolver.resolveRecipientUserIds();
        } catch (Exception ex) {
            log.warn("解析告警收件人失败 alarmEventId={} error={}",
                    alarmEvent.getId(), ex.getMessage());
            return Set.of();
        }
    }

    // 自动链路事件 → WS 摘要事件类型（ALARM_CREATED/UPGRADED/RESEND）
    private String resolveTriggeredEventType(String kind) {
        if (AlarmEventTriggeredEvent.KIND_CREATED.equals(kind)) {
            return "ALARM_CREATED";
        }
        return AlarmEventTriggeredEvent.KIND_RESEND.equals(kind) ? "ALARM_RESEND" : "ALARM_UPGRADED";
    }

    private WebSocketSummary buildSummary(AlarmEvent alarmEvent, String eventType) {
        return new WebSocketSummary(
                
                eventType,
                RESOURCE_TYPE_ALARM_EVENT,
                String.valueOf(alarmEvent.getId()),
                alarmEvent.getState(),
                Instant.now(),
                alarmEvent.getAlarmMessage() != null ? alarmEvent.getAlarmMessage() : eventType,
                alarmEvent.getAlarmLevel());
    }

    private void pushWebSocket(AlarmEvent alarmEvent, Long userId, WebSocketSummary summary) {
        try {
            webSocketPublisher.sendSummary(userId, summary);
            saveLog(alarmEvent, "WEBSOCKET", String.valueOf(userId), LOG_STATUS_DELIVERED, null,
                    summary.eventType());
        } catch (Exception ex) {
            saveLog(alarmEvent, "WEBSOCKET", String.valueOf(userId), LOG_STATUS_FAILED,
                    "摘要推送失败: " + ex.getMessage(), summary.eventType());
            log.warn("告警 WS 摘要推送失败 alarmEventId={} userId={}", alarmEvent.getId(), userId);
        }
    }

    private void sendFallbackEmail(AlarmEvent alarmEvent, List<Long> offlineUsers) {
        if (offlineUsers.isEmpty()) {
            return;
        }
        if (!mailEnabled) {
            saveLog(alarmEvent, "EMAIL", "offline:" + offlineUsers.size(), LOG_STATUS_FAILED,
                    "邮件通道未启用（notify.mail.enabled=false）", "离线用户无即时通道触达");
            return;
        }
        List<SysUser> users = sysUserMapper.selectBatchIds(offlineUsers);
        List<String> emails = users.stream()
                .map(SysUser::getEmail)
                .filter(StringUtils::hasText)
                .toList();
        if (emails.isEmpty()) {
            saveLog(alarmEvent, "EMAIL", "offline:" + offlineUsers.size(), LOG_STATUS_FAILED,
                    "离线收件人均未配置邮箱", "离线用户无即时通道触达");
            return;
        }
        alarmEmailSender.send(alarmEvent, emails);
    }

    private void notifyInApp(AlarmEvent alarmEvent, Set<Long> recipients, String kind) {
        if (recipients.isEmpty()) {
            return;
        }
        try {
            String title = switch (kind) {
                case AlarmEventTriggeredEvent.KIND_CREATED -> "新增告警";
                case AlarmEventTriggeredEvent.KIND_UPGRADED -> "告警升级";
                case AlarmEventTriggeredEvent.KIND_RESEND -> "告警持续未恢复";
                case "RECOVERED" -> "告警恢复";
                default -> "告警更新";
            };
            inAppNotificationService.notifyAlarmEvent(
                    alarmEvent.getId(),
                    recipients,
                    title,
                    alarmEvent.getAlarmMessage() != null ? alarmEvent.getAlarmMessage() : alarmEvent.getAlarmType());
        } catch (Exception ex) {
            log.warn("告警站内通知失败 alarmEventId={} error={}", alarmEvent.getId(), ex.getMessage());
        }
    }

    private void saveLog(AlarmEvent alarmEvent, String channel, String recipient,
                         int status, String error, String content) {
        try {
            AlarmNotificationLog logEntry = new AlarmNotificationLog();
            logEntry.setAlarmEventId(alarmEvent.getId());
            logEntry.setChannel(channel);
            logEntry.setRecipient(recipient);
            logEntry.setContent(content != null && content.length() > 500 ? content.substring(0, 500) : content);
            logEntry.setStatus(status);
            logEntry.setErrorMessage(error);
            notificationLogMapper.insert(logEntry);
        } catch (Exception ex) {
            log.warn("告警发送日志写入失败 alarmEventId={} channel={}", alarmEvent.getId(), channel);
        }
    }
}
