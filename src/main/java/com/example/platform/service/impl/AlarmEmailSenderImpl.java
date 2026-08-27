package com.example.platform.service.impl;

import com.example.platform.entity.AlarmEvent;
import com.example.platform.mapper.AlarmNotificationLogMapper;
import com.example.platform.entity.AlarmNotificationLog;
import com.example.platform.service.AlarmEmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;

/*
 * 类功能:告警邮件兜底发送器实现类（纯文本模板 D25，逐人写发送日志）
 *
 * 【开关】notify.mail.enabled 由分发服务判定（关闭时分发直接记日志跳过本发送器）；
 *   发送地址为发件账号（spring.mail.username）。
 * */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmEmailSenderImpl implements AlarmEmailSender {

    private static final String CHANNEL = "EMAIL";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaMailSender mailSender;
    private final AlarmNotificationLogMapper notificationLogMapper;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Override
    public void send(AlarmEvent alarmEvent, List<String> emails) {
        String subject = buildSubject(alarmEvent);
        String content = buildContent(alarmEvent);
        for (String email : emails) {
            if (!StringUtils.hasText(email)) {
                continue;
            }
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(email);
                message.setSubject(subject);
                message.setText(content);
                mailSender.send(message);
                saveLog(alarmEvent, email, 1, null, content);
            } catch (Exception ex) {
                // 逐人容错：单人失败仅记日志，继续其余收件人（T3 无事务）
                saveLog(alarmEvent, email, 2, "邮件发送失败: " + ex.getMessage(), content);
                log.warn("告警邮件发送失败 alarmEventId={} to={} error={}",
                        alarmEvent.getId(), email, ex.getMessage());
            }
        }
    }

    private String buildSubject(AlarmEvent event) {
        return String.format("【动环监控告警】%s %s",
                levelText(event.getAlarmLevel()), event.getAlarmMessage() != null
                        ? event.getAlarmMessage().split("：")[0] : event.getAlarmType());
    }

    private String buildContent(AlarmEvent event) {
        return String.format("""
                【动环监控告警】级别:%s
                详情:%s
                时间:%s
                事件ID:%d  状态:%s
                —— 登录平台查看与处理""",
                levelText(event.getAlarmLevel()),
                event.getAlarmMessage() != null ? event.getAlarmMessage() : event.getAlarmType(),
                event.getTriggeredTime() != null ? TIME_FORMAT.format(event.getTriggeredTime()) : "-",
                event.getId(), event.getState());
    }

    private String levelText(Integer level) {
        if (level == null) {
            return "未知";
        }
        return switch (level) {
            case 1 -> "1-紧急";
            case 2 -> "2-严重";
            case 3 -> "3-一般";
            case 4 -> "4-警告";
            case 5 -> "5-提示";
            default -> "未知";
        };
    }

    private void saveLog(AlarmEvent event, String recipient, int status, String error, String content) {
        try {
            AlarmNotificationLog logEntry = new AlarmNotificationLog();
            logEntry.setAlarmEventId(event.getId());
            logEntry.setChannel(CHANNEL);
            logEntry.setRecipient(recipient);
            logEntry.setContent(content != null && content.length() > 500 ? content.substring(0, 500) : content);
            logEntry.setStatus(status);
            logEntry.setErrorMessage(error);
            notificationLogMapper.insert(logEntry);
        } catch (Exception ex) {
            log.warn("告警邮件发送日志写入失败 alarmEventId={} error={}", event.getId(), ex.getMessage());
        }
    }
}
