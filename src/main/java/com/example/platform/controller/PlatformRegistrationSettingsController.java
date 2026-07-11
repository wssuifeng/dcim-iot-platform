package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.PlatformRegistrationSettingsDTO;
import com.example.platform.entity.PlatformRegistrationSettings;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.PlatformRegistrationSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/registration-settings")
@RequiredArgsConstructor
public class PlatformRegistrationSettingsController {
    private final PlatformRegistrationSettingsService settingsService;
    private final CurrentUserContext currentUserContext;

    @GetMapping
    public Result<PlatformRegistrationSettings> get() {
        return Result.success(settingsService.getSettings());
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody PlatformRegistrationSettingsDTO dto) {
        settingsService.update(dto.isAccountRegistrationEnabled(),
                dto.getOrganizationRegistrationMode(),
                currentUserContext.requireCurrentUser().userId());
        return Result.success();
    }
}
