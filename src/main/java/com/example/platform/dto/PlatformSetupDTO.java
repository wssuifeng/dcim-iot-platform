package com.example.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlatformSetupDTO {
    @NotBlank @Email @Size(max = 254)
    private String email;
    @NotBlank @Size(min = 4, max = 20)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$")
    private String username;
    @Size(max = 50)
    private String nickname;
    @NotBlank @Size(min = 8, max = 32)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,32}$")
    private String password;
    @NotBlank
    private String confirmPassword;
    @NotBlank @Pattern(regexp = "^\\d{6}$")
    private String verificationCode;
    @NotBlank
    private String bootstrapToken;
}
