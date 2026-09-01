package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.AlarmNotificationLog;
import com.example.platform.entity.AlarmWebhookChannel;
import com.example.platform.mapper.AlarmNotificationLogMapper;
import com.example.platform.mapper.AlarmWebhookChannelMapper;
import com.example.platform.service.AlarmWebhookDispatcher;
import com.example.platform.service.WebhookSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/*
 * 类功能:告警 webhook 分发器实现——逐渠道容错分发 + 失败退避重试 + 逐次尝试写通知日志
 *
 * 【分发口径（S8-2 定案）】
 *   - 事件范围：ALARM_CREATED / ALARM_UPGRADED / ALARM_RESEND / ALARM_RECOVERED 全推
 *     （IM 渠道里恢复也是关键信息，邮件 D24"恢复不打扰"策略不适用于 webhook）；
 *   - 级别过滤：推送条件 = event.alarmLevel ≤ channel.maxAlarmLevel（数字小=严重）；
 *   - 重试：@Async 分发线程内同步退避，失败后 1s/5s/30s 重试共 4 次尝试；
 *   - 日志：每次尝试各写一行 alarm_notification_log（channel=WEBHOOK_<TYPE>，
 *     recipient=渠道名，status 1=成功 2=失败），失败重试对用户可见。
 *
 * 【响应校验】各渠道 HTTP 200 不代表成功（如钉钉关键词不匹配返回 errcode≠0）：
 *   按渠道校验业务码——企微/钉钉 errcode=0、飞书 code=0（旧版 StatusCode=0）、
 *   Telegram ok=true、ntfy/Generic 以 HTTP 2xx 为准。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmWebhookDispatcherImpl implements AlarmWebhookDispatcher {

    private static final int MAX_ATTEMPTS = 4;

    // 包内可变：单元测试注入 0ms 退避，避免真实等待
    long[] retryBackoffMs = {1000L, 5000L, 30000L};

    private static final int LOG_STATUS_DELIVERED = 1;
    private static final int LOG_STATUS_FAILED = 2;

    private final AlarmWebhookChannelMapper channelMapper;
    private final WebhookMessageRenderer renderer;
    private final WebhookSender webhookSender;
    private final AlarmNotificationLogMapper notificationLogMapper;
    private final com.example.platform.ingest.wecom.WecomAibotClient wecomAibotClient;

    @Override
    public void dispatch(AlarmEvent event, String eventName) {
        List<AlarmWebhookChannel> channels = channelMapper.selectList(
                new LambdaQueryWrapper<AlarmWebhookChannel>()
                        .eq(AlarmWebhookChannel::getEnabled, 1));
        if (channels.isEmpty()) {
            return;
        }
        String text = buildText(event, eventName);
        for (AlarmWebhookChannel channel : channels) {
            try {
                if (event.getAlarmLevel() == null || channel.getMaxAlarmLevel() == null
                        || event.getAlarmLevel() > channel.getMaxAlarmLevel()) {
                    continue;
                }
                dispatchOne(channel, event, eventName, text);
            } catch (Exception ex) {
                // 逐渠道容错：任何渠道异常不影响其余渠道与告警本身
                log.warn("webhook 渠道分发异常 channelId={} name={} error={}",
                        channel.getId(), channel.getName(), ex.getMessage());
            }
        }
    }

    private void dispatchOne(AlarmWebhookChannel channel, AlarmEvent event,
                             String eventName, String text) {
        // 企微智能机器人（长连接模式）：不走 HTTP，直接经 wss 推送 markdown；失败仅记日志不重试（连接层自带重连）
        if (com.example.platform.entity.AlarmWebhookChannel.TYPE_WECOM_AIBOT.equals(channel.getChannelType())) {
            dispatchWecomAibot(channel, event, eventName, text);
            return;
        }
        WebhookMessageRenderer.WebhookRequest request =
                renderer.render(channel, event, eventName, text);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String response = webhookSender.send(request.url(), request.headers(), request.body());
                WebhookMessageRenderer.validateResponse(channel.getChannelType(), response);
                saveAttemptLog(channel, event, eventName, attempt, LOG_STATUS_DELIVERED, null);
                log.info("webhook 渠道分发成功 channelId={} name={} eventName={} attempt={}",
                        channel.getId(), channel.getName(), eventName, attempt);
                return;
            } catch (Exception ex) {
                boolean lastAttempt = attempt == MAX_ATTEMPTS;
                saveAttemptLog(channel, event, eventName, attempt, LOG_STATUS_FAILED,
                        (lastAttempt ? "最终失败" : "重试失败") + "（第" + attempt + "次）: " + ex.getMessage());
                if (!lastAttempt) {
                    sleep(retryBackoffMs[attempt - 1]);
                }
            }
        }
    }

    // 企微智能机器人长连接分发：markdown 渲染 + aibot_send_msg，一次尝试（无 HTTP 重试语义）
    private void dispatchWecomAibot(AlarmWebhookChannel channel, AlarmEvent event,
                                    String eventName, String text) {
        try {
            com.example.platform.ingest.wecom.WecomAibotCredential cred =
                    com.example.platform.ingest.wecom.WecomAibotCredential.parse(
                            channel.getCredentialJson());
            wecomAibotClient.sendMarkdown(cred.botId(), cred.secret(), cred.chatid(),
                    renderAibotMarkdown(event, eventName, text));
            saveAttemptLog(channel, event, eventName, 1, LOG_STATUS_DELIVERED, null);
            log.info("企微智能机器人分发成功 channelId={} name={} eventName={}",
                    channel.getId(), channel.getName(), eventName);
        } catch (Exception ex) {
            saveAttemptLog(channel, event, eventName, 1, LOG_STATUS_FAILED,
                    "长连接推送失败: " + ex.getMessage());
            log.warn("企微智能机器人分发失败 channelId={} error={}", channel.getId(), ex.getMessage());
        }
    }

    // aibot 消息体：markdown（官方告警推送推荐 msgtype）
    private String renderAibotMarkdown(AlarmEvent event, String eventName, String text) {
        String level = event.getAlarmLevel() != null ? "**L" + event.getAlarmLevel() + "**" : "-";
        return "> **dcim 告警 " + level + " · " + eventName + "**" + "\n\n" + text;
    }

    // 通知文本：级别标签 + 事件类型 + 告警消息（评估链消息已含设备/点位/规则明细）
    private String buildText(AlarmEvent event, String eventName) {
        String level = event.getAlarmLevel() != null ? "L" + event.getAlarmLevel() : "-";
        StringBuilder builder = new StringBuilder();
        builder.append("【dcim 告警 ").append(level).append(" · ").append(eventName).append("】\n");
        builder.append(event.getAlarmMessage() != null ? event.getAlarmMessage() : event.getAlarmType());
        if ("ALARM_RECOVERED".equals(eventName)) {
            builder.append("\n已恢复，请关注");
        }
        return builder.toString();
    }

    private void saveAttemptLog(AlarmWebhookChannel channel, AlarmEvent event,
                                String eventName, int attempt, int status, String error) {
        try {
            AlarmNotificationLog logEntry = new AlarmNotificationLog();
            logEntry.setAlarmEventId(event.getId());
            logEntry.setChannel("WEBHOOK_" + channel.getChannelType());
            logEntry.setRecipient(channel.getName());
            logEntry.setContent(eventName + " 第" + attempt + "次尝试");
            logEntry.setStatus(status);
            logEntry.setErrorMessage(error);
            logEntry.setSentTime(LocalDateTime.now());
            notificationLogMapper.insert(logEntry);
        } catch (Exception ex) {
            log.warn("webhook 通知日志写入失败 channelId={}", channel.getId());
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("重试等待被中断", ie);
        }
    }

    private String shorten(String body) {
        return body.length() > 200 ? body.substring(0, 200) : body;
    }
}
