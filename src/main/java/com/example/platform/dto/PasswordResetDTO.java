package com.example.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/*
 * 类功能:密码重置请求DTO，封装重置密码操作的请求参数
 * */
@Data
public class PasswordResetDTO {

    /** 新密码（明文8-32位，经Service层BCrypt加密后存储） */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度需在8-32之间")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,32}$",
            message = "密码必须包含大小写字母和数字")
    private String newPassword;
}