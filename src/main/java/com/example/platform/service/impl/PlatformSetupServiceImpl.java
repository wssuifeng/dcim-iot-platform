package com.example.platform.service.impl;

import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.PlatformSetupDTO;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.PlatformRegistrationSettingsMapper;
import com.example.platform.mapper.SysUserMapper;
import com.example.platform.service.EmailVerificationService;
import com.example.platform.service.PlatformSetupService;
import com.example.platform.service.SysUserService;
import com.example.platform.service.VerificationPurpose;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class PlatformSetupServiceImpl implements PlatformSetupService {
    private final PlatformRegistrationSettingsMapper settingsMapper;
    private final SysUserMapper userMapper;
    private final SysUserService userService;
    private final EmailVerificationService verificationService;
    private final byte[] bootstrapToken;

    public PlatformSetupServiceImpl(
            PlatformRegistrationSettingsMapper settingsMapper,
            SysUserMapper userMapper,
            SysUserService userService,
            EmailVerificationService verificationService,
            @Value("${platform.bootstrap-token:}") String bootstrapToken) {
        this.settingsMapper = settingsMapper;
        this.userMapper = userMapper;
        this.userService = userService;
        this.verificationService = verificationService;
        this.bootstrapToken = bootstrapToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean isSetupRequired() {
        return userMapper.countPlatformAdmins() == 0;
    }

    @Override
    public void sendVerificationCode(String email, String ip) {
        requireSetupAvailable();
        verificationService.sendCode(VerificationPurpose.PLATFORM_SETUP, email, ip);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initialize(PlatformSetupDTO dto, String ip) {
        settingsMapper.selectSingletonForUpdate();
        requireSetupAvailable();
        requireBootstrapToken(dto.getBootstrapToken());
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "两次输入的密码不一致");
        }
        String email = EmailVerificationServiceImpl.normalizeEmail(dto.getEmail());
        userService.requireIdentityAvailable(email, dto.getUsername().trim());
        verificationService.verifyAndConsume(
                VerificationPurpose.PLATFORM_SETUP, email, dto.getVerificationCode());

        SysUser user = new SysUser();
        user.setEmail(email);
        user.setUsername(dto.getUsername().trim());
        user.setNickname(dto.getNickname());
        user.setPassword(dto.getPassword());
        userService.createPlatformAdministrator(user);
    }

    private void requireSetupAvailable() {
        if (!isSetupRequired()) {
            throw new BusinessException(ResultCode.SETUP_ALREADY_COMPLETED,
                    "平台管理员已初始化");
        }
    }

    private void requireBootstrapToken(String provided) {
        byte[] candidate = provided == null
                ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        if (bootstrapToken.length == 0 || !MessageDigest.isEqual(bootstrapToken, candidate)) {
            throw new BusinessException(ResultCode.SETUP_TOKEN_INVALID, "初始化凭证无效");
        }
    }
}
