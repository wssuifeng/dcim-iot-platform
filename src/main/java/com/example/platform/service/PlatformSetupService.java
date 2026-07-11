package com.example.platform.service;

import com.example.platform.dto.PlatformSetupDTO;

public interface PlatformSetupService {
    boolean isSetupRequired();

    void sendVerificationCode(String email, String ip);

    void initialize(PlatformSetupDTO dto, String ip);
}
