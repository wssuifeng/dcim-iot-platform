package com.example.platform.service.impl;

import com.example.platform.service.VerificationMailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QqVerificationMailSender implements VerificationMailSender {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    @Override
    public void send(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("数据中心动环监控平台验证码");
        message.setText("您的验证码是 " + code + "，5分钟内有效。请勿向他人泄露。");
        mailSender.send(message);
    }
}
