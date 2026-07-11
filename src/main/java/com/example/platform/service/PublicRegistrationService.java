package com.example.platform.service;

import com.example.platform.dto.RegisterDTO;

public interface PublicRegistrationService {
    void register(RegisterDTO dto, String ip);
}
