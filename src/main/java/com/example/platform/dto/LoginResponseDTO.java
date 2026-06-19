package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录成功响应DTO，封装JWT令牌及用户基本信息，避免返回密码等敏感字段
 */
@Schema(description = "登录成功响应")
@Data
public class LoginResponseDTO {

    @Schema(description = "JWT访问令牌", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "令牌类型，固定值 Bearer", example = "Bearer")
    private String tokenType;

    @Schema(description = "令牌有效时长（秒）", example = "86400")
    private Long expiresIn;

    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Schema(description = "用户名", example = "admin")
    private String username;

    @Schema(description = "昵称", example = "系统管理员")
    private String nickname;

    @Schema(description = "角色标识：ADMIN/OPERATOR/VIEWER", example = "ADMIN")
    private String role;
}