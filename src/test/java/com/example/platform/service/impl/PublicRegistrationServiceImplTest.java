package com.example.platform.service.impl;

import com.example.platform.dto.RegisterDTO;
import com.example.platform.entity.SysUser;
import com.example.platform.service.EmailVerificationService;
import com.example.platform.service.PlatformRegistrationSettingsService;
import com.example.platform.service.SysUserService;
import com.example.platform.service.VerificationPurpose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PublicRegistrationServiceImplTest {
    @Mock
    private PlatformRegistrationSettingsService settingsService;
    @Mock
    private EmailVerificationService verificationService;
    @Mock
    private SysUserService userService;
    @InjectMocks
    private PublicRegistrationServiceImpl service;

    @Test
    void registrationCreatesVerifiedGlobalAccountWithoutMembership() {
        RegisterDTO dto = new RegisterDTO();
        dto.setEmail(" USER@Example.com ");
        dto.setUsername("normal_user");
        dto.setNickname("普通用户");
        dto.setPassword("Password1");
        dto.setConfirmPassword("Password1");
        dto.setVerificationCode("123456");

        service.register(dto, "127.0.0.1");

        verify(settingsService).requireAccountRegistrationEnabled();
        verify(userService).requireIdentityAvailable("user@example.com", "normal_user");
        verify(verificationService).verifyAndConsume(
                VerificationPurpose.REGISTRATION, "user@example.com", "123456");
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).createRegisteredUser(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(captor.getValue().getEmailVerified()).isTrue();
        assertThat(captor.getValue().getPlatformRole()).isNull();
    }
}
