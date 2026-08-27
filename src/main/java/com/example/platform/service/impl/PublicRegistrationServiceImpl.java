package com.example.platform.service.impl;

import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.RegisterDTO;
import com.example.platform.entity.SysUser;
import com.example.platform.service.EmailVerificationService;
import com.example.platform.service.PublicRegistrationService;
import com.example.platform.service.SysUserService;
import com.example.platform.service.VerificationPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * 类功能:公开注册服务实现类，通过邮箱验证码完成自助注册（开关由 auth.registration-enabled 控制）
 * */
@Service
@RequiredArgsConstructor
public class PublicRegistrationServiceImpl implements PublicRegistrationService {
    private final EmailVerificationService verificationService;
    private final SysUserService userService;

    @Value("${auth.registration-enabled:true}")
    private boolean registrationEnabled;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto, String ip) {
        if (!registrationEnabled) {
            throw new BusinessException(ResultCode.FORBIDDEN, "注册通道已关闭，请联系管理员");
        }
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
