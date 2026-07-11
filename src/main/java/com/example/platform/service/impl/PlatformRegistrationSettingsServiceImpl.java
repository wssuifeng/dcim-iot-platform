package com.example.platform.service.impl;

import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.entity.PlatformRegistrationSettings;
import com.example.platform.mapper.PlatformRegistrationSettingsMapper;
import com.example.platform.service.PlatformRegistrationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlatformRegistrationSettingsServiceImpl implements PlatformRegistrationSettingsService {
    private static final Set<String> MODES = Set.of("CLOSED", "REVIEW", "OPEN");
    private final PlatformRegistrationSettingsMapper mapper;

    @Override
    public PlatformRegistrationSettings getSettings() {
        PlatformRegistrationSettings settings = mapper.selectById(1);
        if (settings == null) {
            throw new BusinessException(ResultCode.SYSTEM_CONFIG_INVALID, "平台注册设置不存在");
        }
        return settings;
    }

    @Override
    public void requireAccountRegistrationEnabled() {
        if (!Boolean.TRUE.equals(getSettings().getAccountRegistrationEnabled())) {
            throw new BusinessException(ResultCode.REGISTRATION_CLOSED, "账号注册已关闭");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(boolean enabled, String mode, Long operatorId) {
        String normalizedMode = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        if (!MODES.contains(normalizedMode)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "组织注册模式不合法");
        }
        PlatformRegistrationSettings settings = getSettings();
        settings.setAccountRegistrationEnabled(enabled);
        settings.setOrganizationRegistrationMode(normalizedMode);
        settings.setUpdatedBy(operatorId);
        mapper.updateById(settings);
    }
}
