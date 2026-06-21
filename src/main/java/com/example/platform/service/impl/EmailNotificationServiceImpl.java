package com.example.platform.service.impl;

import com.example.platform.entity.Alert;
import com.example.platform.entity.NotificationLog;
import com.example.platform.mapper.NotificationLogMapper;
import com.example.platform.service.DeviceService;
import com.example.platform.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/*
 * 类功能:邮件告警通知实现类，基于 Spring Mail 发送告警邮件
 *
 * 说明：生产环境需配置 spring.mail 与 notify.mail.to 后方可真正发送。
 * */
@Slf4j
@Service
public class EmailNotificationServiceImpl implements NotificationService {

    private final JavaMailSender mailSender;
    private final DeviceService deviceService;
    private final NotificationLogMapper notificationLogMapper;

    @Value("${spring.mail.username:}")
    private String from;

    @Value("${notify.mail.to:}")
    private String notifyTo;

    @Value("${notify.mail.enabled:false}")
    private boolean enabled;

    public EmailNotificationServiceImpl(JavaMailSender mailSender, DeviceService deviceService,
                                        NotificationLogMapper notificationLogMapper) {
        this.mailSender = mailSender;
        this.deviceService = deviceService;
        this.notificationLogMapper = notificationLogMapper;
    }

    @Override
    public boolean sendAlert(Alert alert) {
        String error = null;
        boolean success = false;
        try {
            if (!enabled || notifyTo == null || notifyTo.isBlank() || from == null || from.isBlank()) {
                error = "邮件通知未启用或配置不完整";
                log.info("邮件通知未启用或配置不完整，跳过发送: alertId={}", alert.getId());
            } else {
                try {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setFrom(from);
                    message.setTo(notifyTo.split(","));
                    message.setSubject(buildSubject(alert));
                    message.setText(buildText(alert));
                    mailSender.send(message);
                    success = true;
                    log.info("告警邮件发送成功: alertId={}, to={}", alert.getId(), notifyTo);
                } catch (MailException e) {
                    error = e.getMessage();
                    log.warn("告警邮件发送失败: alertId={}, error={}", alert.getId(), error);
                }
            }
        } finally {
            saveLog(alert, "EMAIL", notifyTo, buildText(alert), success ? 1 : 2, error);
        }
        return success;
    }

    private void saveLog(Alert alert, String type, String recipient, String content, int status, String error) {
        try {
            NotificationLog logEntry = new NotificationLog();
            logEntry.setAlertId(alert.getId());
            logEntry.setNotificationType(type);
            logEntry.setRecipient(recipient);
            logEntry.setContent(content);
            logEntry.setStatus(status);
            logEntry.setErrorMessage(error);
            logEntry.setSendTime(LocalDateTime.now());
            notificationLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("通知日志写入失败: alertId={}, type={}, error={}", alert.getId(), type, e.getMessage());
        }
    }

    private String buildSubject(Alert alert) {
        return String.format("【IoT告警】%s - 级别%d", alert.getAlertType(), alert.getAlertLevel());
    }

    private String buildText(Alert alert) {
        String deviceName = "未知设备";
        try {
            deviceName = deviceService.getDeviceOrThrow(alert.getDeviceId()).getDeviceName();
        } catch (Exception e) {
            log.warn("获取设备名称失败，使用默认值: deviceId={}, error={}", alert.getDeviceId(), e.getMessage());
        }
        return String.format(
                "设备名称: %s%n设备ID: %d%n告警类型: %s%n告警级别: %d%n告警时间: %s%n告警详情: %s",
                deviceName,
                alert.getDeviceId(),
                alert.getAlertType(),
                alert.getAlertLevel(),
                alert.getCreateTime(),
                alert.getAlertMessage()
        );
    }
}
