package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.LoginDTO;
import com.example.platform.dto.LoginResponseDTO;
import com.example.platform.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口层，提供用户登录认证RESTful接口
 */
@Tag(name = "认证", description = "用户登录与 JWT 颁发")
@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "用户登录", description = "验证用户名密码并返回 JWT Token；自动提取客户端真实 IP（支持 X-Forwarded-For/X-Real-IP 代理转发）；该端点 permitAll，无需认证")
    @PostMapping("/login")
    public Result<LoginResponseDTO> login(@Validated @RequestBody LoginDTO loginDTO,
                                          HttpServletRequest request) {
        String ip = getClientIp(request);
        return Result.success(authService.login(loginDTO, ip));
    }

    /**
     * 从HTTP请求中提取客户端真实IP地址（支持代理转发场景）
     * 优先级：X-Forwarded-For → X-Real-IP → request.getRemoteAddr()
     */
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