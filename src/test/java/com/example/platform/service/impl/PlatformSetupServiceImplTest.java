package com.example.platform.service.impl;

import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.PlatformSetupDTO;
import com.example.platform.entity.PlatformRegistrationSettings;
import com.example.platform.mapper.PlatformRegistrationSettingsMapper;
import com.example.platform.mapper.SysUserMapper;
import com.example.platform.service.EmailVerificationService;
import com.example.platform.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformSetupServiceImplTest {
    @Mock PlatformRegistrationSettingsMapper settingsMapper;
    @Mock SysUserMapper userMapper;
    @Mock SysUserService userService;
    @Mock EmailVerificationService verificationService;

    @Test
    void setupFailsClosedWhenPlatformAdminAlreadyExists() {
        PlatformSetupServiceImpl service = new PlatformSetupServiceImpl(
                settingsMapper, userMapper, userService, verificationService, "bootstrap-token");
        when(settingsMapper.selectSingletonForUpdate()).thenReturn(new PlatformRegistrationSettings());
        when(userMapper.countPlatformAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> service.initialize(dto(), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已初始化");
    }

    private PlatformSetupDTO dto() {
        PlatformSetupDTO dto = new PlatformSetupDTO();
        dto.setEmail("admin@example.com");
        dto.setUsername("platform_admin");
        dto.setPassword("Password1");
        dto.setConfirmPassword("Password1");
        dto.setVerificationCode("123456");
        dto.setBootstrapToken("bootstrap-token");
        return dto;
    }
}
