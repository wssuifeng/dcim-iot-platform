package com.example.platform.service.impl;

import com.example.platform.common.exception.BusinessException;
import com.example.platform.service.CacheService;
import com.example.platform.service.VerificationMailSender;
import com.example.platform.service.VerificationPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceImplTest {
    @Mock
    private CacheService cacheService;
    @Mock
    private VerificationMailSender mailSender;
    private EmailVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationServiceImpl(cacheService, mailSender,
                "test-hmac-secret-at-least-32-characters");
    }

    @Test
    void sameIpCooldownRejectsSecondSend() {
        when(cacheService.setIfAbsent("verify:cooldown:ip:127.0.0.1", 1,
                60, TimeUnit.SECONDS)).thenReturn(false);

        assertThatThrownBy(() -> service.sendCode(VerificationPurpose.REGISTRATION,
                "USER@example.com", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("60秒");

        verify(mailSender, never()).send(anyString(), anyString());
    }

    @Test
    void fifthInvalidAttemptConsumesCode() {
        when(cacheService.get(anyString(), eq(String.class))).thenReturn("different-digest");
        when(cacheService.increment(anyString(), eq(1L))).thenReturn(5L);

        assertThatThrownBy(() -> service.verifyAndConsume(VerificationPurpose.REGISTRATION,
                "user@example.com", "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("次数已耗尽");

        verify(cacheService).delete(anyString());
    }
}
