package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 密码重置请求DTO，封装重置密码操作的请求参数
 */
@Schema(description = "密码重置请求")
@Data
public class PasswordResetDTO {

    @Schema(description = "新密码（明文8-32位，须含大小写字母和数字，Service层BCrypt加密后存储）",
            example = "NewPass123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度需在8-32之间")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,32}$",
            message = "密码必须包含大小写字母和数字")
    private String newPassword;
}