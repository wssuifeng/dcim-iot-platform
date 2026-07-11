package com.example.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PlatformRegistrationSettingsDTO {
    private boolean accountRegistrationEnabled;
    @NotBlank
    private String organizationRegistrationMode;
}
