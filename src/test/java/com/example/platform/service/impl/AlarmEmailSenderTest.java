package com.example.platform.service.impl;

import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.AlarmNotificationLog;
import com.example.platform.mapper.AlarmNotificationLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:AlarmEmailSenderImpl 单元测试
 * 覆盖：群发逐条日志、单人失败降级继续其余（D17/T3 容错）
 * */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AlarmEmailSender 单元测试")
class AlarmEmailSenderTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private AlarmNotificationLogMapper notificationLogMapper;

    @InjectMocks
    private AlarmEmailSenderImpl emailSender;

    private AlarmEvent event() {
        AlarmEvent event = new AlarmEvent();
        event.setId(1L);
        event.setAlarmType("METRIC");
        event.setAlarmMessage("高温警告：温度=41℃（规则 GT 40，级别1）");
        event.setState("TRIGGERED");
        event.setOrganizationId(1L);
        return event;
    }

    @Test
    @DisplayName("群发两人各写一条成功日志")
    void should_sendAll_andLogEachRecipient() {
        ReflectionTestUtils.setField(emailSender, "fromAddress", "alert@demo.local");
        when(notificationLogMapper.insert(any(AlarmNotificationLog.class))).thenReturn(1);

        emailSender.send(event(), List.of("a@demo.local", "b@demo.local"));

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
        ArgumentCaptor<AlarmNotificationLog> captor = ArgumentCaptor.forClass(AlarmNotificationLog.class);
        verify(notificationLogMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(log -> assertThat(log.getStatus()).isEqualTo(1))
                .extracting(AlarmNotificationLog::getRecipient)
                .containsExactly("a@demo.local", "b@demo.local");
    }

    @Test
    @DisplayName("首个收件人发送失败仅记失败日志，继续其余收件人")
    void should_continueAfterFailure() {
        ReflectionTestUtils.setField(emailSender, "fromAddress", "alert@demo.local");
        when(notificationLogMapper.insert(any(AlarmNotificationLog.class))).thenReturn(1);
        doThrow(new MailException("smtp down") {}).when(mailSender).send(any(SimpleMailMessage.class));

        emailSender.send(event(), List.of("a@demo.local", "b@demo.local"));

        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
        verify(notificationLogMapper, times(2)).insert(any(AlarmNotificationLog.class));
    }
}
