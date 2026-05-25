package com.example.platform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/*
 * 类功能:登录请求DTO，封装用户认证所需的用户名和密码
 * */
@Data
public class LoginDTO {

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码（明文，Service层BCrypt校验） */
    @NotBlank(message = "密码不能为空")
    private String password;
}