package com.example.platform.service;

import com.example.platform.entity.PlatformRegistrationSettings;

public interface PlatformRegistrationSettingsService {
    PlatformRegistrationSettings getSettings();

    void requireAccountRegistrationEnabled();

    void update(boolean accountRegistrationEnabled, String organizationRegistrationMode,
                Long operatorId);
}
