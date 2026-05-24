package com.example.platform.dto;

import lombok.Data;

/*
 * 类功能:登录成功响应DTO，封装JWT令牌及用户基本信息，避免返回密码等敏感字段
 * */
@Data
public class LoginResponseDTO {

    /** JWT访问令牌 */
    private String token;

    /** 令牌类型，固定值"Bearer" */
    private String tokenType;

    /** 令牌有效时长（秒） */
    private Long expiresIn;

    /** 用户ID */
    private Long userId; 

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 角色标识(ADMIN/OPERATOR/VIEWER) */
    private String role;
}