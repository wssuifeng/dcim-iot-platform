package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.LoginDTO;
import com.example.platform.dto.LoginResponseDTO;
import com.example.platform.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/*
 * 类功能:认证接口层，提供用户登录认证RESTful接口
 * */
@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /*
     * 方法功能:用户登录，验证凭据并返回JWT Token
     * 参数:loginDTO-登录请求体(用户名+密码), request-HTTP请求(用于获取客户端IP)
     * 返回数据:Result<LoginResponseDTO>-包含Token及用户基本信息的响应
     * */
    @PostMapping("/login")
    public Result<LoginResponseDTO> login(@Validated @RequestBody LoginDTO loginDTO,
                                          HttpServletRequest request) {
        String ip = getClientIp(request);
        return Result.success(authService.login(loginDTO, ip));
    }

    /*
     * 方法功能:从HTTP请求中提取客户端真实IP地址（支持代理转发场景）
     * 参数:request-HTTP请求
     * 返回数据:String-客户端IP地址
     * */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}