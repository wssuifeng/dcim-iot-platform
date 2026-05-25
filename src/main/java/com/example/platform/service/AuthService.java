package com.example.platform.service;

import com.example.platform.dto.LoginDTO;
import com.example.platform.dto.LoginResponseDTO;

/*
 * 类功能:认证服务接口，定义用户登录认证业务方法
 * */
public interface AuthService {

    /*
     * 方法功能:用户登录认证，验证凭据并签发JWT Token
     * 参数:loginDTO-登录请求体(用户名+密码), ip-客户端IP地址
     * 返回数据:LoginResponseDTO-包含Token及用户基本信息的响应对象
     * */
    LoginResponseDTO login(LoginDTO loginDTO, String ip);
}