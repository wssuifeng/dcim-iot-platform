package com.example.platform.security;

import com.example.platform.common.util.JwtUtil;
import com.example.platform.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;
import java.util.List;

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

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, TokenBlacklistService tokenBlacklistService) {
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
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
                Claims claims = jwtUtil.parseToken(token);

                // 第1步：检查单Token黑名单（用户主动登出）
                String tokenId = claims.getId();
                if (tokenId != null && tokenBlacklistService.isTokenBlacklisted(tokenId)) {
                    log.debug("Token已在黑名单中(主动登出): tokenId={}", tokenId);
                    filterChain.doFilter(request, response);
                    return;
                }

                // 第2步：检查用户级黑名单（管理员禁用用户）
                Long userId = Long.parseLong(claims.getSubject());
                Date issuedAt = claims.getIssuedAt();
                if (issuedAt != null && tokenBlacklistService.isUserBlacklistedAndTokenBefore(
                        String.valueOf(userId), issuedAt)) {
                    log.debug("Token在用户禁用前签发(已被作废): userId={}", userId);
                    filterChain.doFilter(request, response);
                    return;
                }

                // 第3步：黑名单校验通过，提取用户身份信息
                String username = claims.get("username", String.class);
                String role = claims.get("role", String.class);

                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role)
                );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                authentication.setDetails(username);

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (ExpiredJwtException e) {
                log.debug("JWT Token已过期: {}", e.getMessage());
            } catch (JwtException e) {
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
