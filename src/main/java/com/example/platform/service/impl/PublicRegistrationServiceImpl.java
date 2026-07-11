package com.example.platform.service.impl;

import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.RegisterDTO;
import com.example.platform.entity.SysUser;
import com.example.platform.service.EmailVerificationService;
import com.example.platform.service.PlatformRegistrationSettingsService;
import com.example.platform.service.PublicRegistrationService;
import com.example.platform.service.SysUserService;
import com.example.platform.service.VerificationPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicRegistrationServiceImpl implements PublicRegistrationService {
    private final PlatformRegistrationSettingsService settingsService;
    private final EmailVerificationService verificationService;
    private final SysUserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto, String ip) {
        settingsService.requireAccountRegistrationEnabled();
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "两次输入的密码不一致");
        }
        String email = EmailVerificationServiceImpl.normalizeEmail(dto.getEmail());
        userService.requireIdentityAvailable(email, dto.getUsername().trim());
        verificationService.verifyAndConsume(
                VerificationPurpose.REGISTRATION, email, dto.getVerificationCode());

        SysUser user = new SysUser();
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setUsername(dto.getUsername().trim());
        user.setNickname(dto.getNickname() == null ? null : dto.getNickname().trim());
        user.setPassword(dto.getPassword());
        userService.createRegisteredUser(user);
    }
}
