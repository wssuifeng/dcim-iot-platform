package com.example.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterDTO {
    @NotBlank @Email @Size(max = 254)
    private String email;
    @NotBlank @Size(min = 4, max = 20)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅允许字母数字和下划线")
    private String username;
    @Size(max = 50)
    private String nickname;
    @NotBlank @Size(min = 8, max = 32)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,32}$",
            message = "密码必须包含大小写字母和数字")
    private String password;
    @NotBlank
    private String confirmPassword;
    @NotBlank @Pattern(regexp = "^\\d{6}$", message = "验证码必须为6位数字")
    private String verificationCode;
}
