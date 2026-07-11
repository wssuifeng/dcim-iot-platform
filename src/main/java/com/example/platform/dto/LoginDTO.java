package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求DTO，封装用户认证所需的用户名和密码
 */
@Schema(description = "登录请求")
@Data
public class LoginDTO {

    @Schema(description = "邮箱或用户名", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "邮箱或用户名不能为空")
    private String identifier;

    @Deprecated
    public String getUsername() {
        return identifier;
    }

    @Deprecated
    public void setUsername(String username) {
        this.identifier = username;
    }

    @Schema(description = "密码（明文，Service层BCrypt校验）", example = "Admin@123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "密码不能为空")
    private String password;
}
