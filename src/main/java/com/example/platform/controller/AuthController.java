package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.LoginDTO;
import com.example.platform.dto.LoginResponseDTO;
import com.example.platform.dto.RegisterDTO;
import com.example.platform.dto.RegistrationCodeDTO;
import com.example.platform.service.EmailVerificationService;
import com.example.platform.service.PublicRegistrationService;
import com.example.platform.service.VerificationPurpose;
import com.example.platform.service.AuthService;
import com.example.platform.service.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口层，提供用户登录、登出等认证RESTful接口
 */
@Tag(name = "认证", description = "用户登录、登出与 JWT 认证")
@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final TokenBlacklistService tokenBlacklistService;
    private final PublicRegistrationService registrationService;
    private final EmailVerificationService verificationService;

    public AuthController(AuthService authService, TokenBlacklistService tokenBlacklistService,
                          PublicRegistrationService registrationService,
                          EmailVerificationService verificationService) {
        this.authService = authService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.registrationService = registrationService;
        this.verificationService = verificationService;
    }

    @Operation(summary = "用户登录", description = "验证用户名密码并返回 JWT Token；自动提取客户端真实 IP（支持 X-Forwarded-For/X-Real-IP 代理转发）；该端点 permitAll，无需认证")
    @PostMapping("/login")
    public Result<LoginResponseDTO> login(@Validated @RequestBody LoginDTO loginDTO,
                                          HttpServletRequest request) {
        String ip = getClientIp(request);
        return Result.success(authService.login(loginDTO, ip));
    }

    @PostMapping("/registration-codes")
    public Result<Void> sendRegistrationCode(@Validated @RequestBody RegistrationCodeDTO dto,
                                             HttpServletRequest request) {
        verificationService.sendCode(VerificationPurpose.REGISTRATION, dto.getEmail(), getClientIp(request));
        return Result.success();
    }

    @PostMapping("/register")
    public Result<Void> register(@Validated @RequestBody RegisterDTO dto,
                                 HttpServletRequest request) {
        registrationService.register(dto, getClientIp(request));
        return Result.success();
    }

    @Operation(summary = "用户登出", description = "将当前请求的 JWT Token 加入黑名单，登出后该 Token 立即失效；需认证")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            tokenBlacklistService.addSingleTokenBlacklist(token);
        }
        return Result.success();
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
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
