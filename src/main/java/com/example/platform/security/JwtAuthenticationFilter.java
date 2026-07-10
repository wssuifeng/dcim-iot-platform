package com.example.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/*
 * 类功能:JWT认证过滤器，从请求头提取Bearer Token，校验签名、有效期与黑名单，
 * 将用户身份与角色写入SecurityContext供后续鉴权使用
 *
 * 【双层黑名单校验流程】
 * 1. 验签通过后，先查单Token黑名单(auth:token:blacklist:{tokenId})
 *    - 命中 → 该Token已被主动登出 → 返回401
 * 2. 再查用户级黑名单(auth:user:blacklist:{userId})
 *    - 存在 → 提取Token的iat(签发时间)，与禁用时间戳对比
 *    - iat < 禁用时间戳 → Token在禁用前签发，不可信 → 返回401
 *    - iat >= 禁用时间戳 → Token在禁用后签发(重新登录)，可信 → 放行
 * */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtAuthenticationService jwtAuthenticationService;

    public JwtAuthenticationFilter(JwtAuthenticationService jwtAuthenticationService) {
        this.jwtAuthenticationService = jwtAuthenticationService;
    }

    /*
     * 方法功能:每次请求执行一次，提取并校验JWT Token，有效则设置SecurityContext
     * 参数:request-HTTP请求, response-HTTP响应, filterChain-过滤器链
     * 返回数据:无
     * */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null) {
            try {
                Authentication authentication = jwtAuthenticationService.authenticate(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (org.springframework.security.core.AuthenticationException e) {
                log.debug("JWT Token无效: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /*
     * 方法功能:从请求头Authorization中提取Bearer Token
     * 参数:request-HTTP请求
     * 返回数据:String-Token字符串，不存在或格式不正确返回null
     * */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
