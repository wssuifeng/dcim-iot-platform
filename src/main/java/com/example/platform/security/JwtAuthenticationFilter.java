package com.example.platform.security;

import com.example.platform.common.util.JwtUtil;
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
import java.util.List;

/*
 * 类功能:JWT认证过滤器，从请求头提取Bearer Token，校验签名与有效期，
 * 将用户身份与角色写入SecurityContext供后续鉴权使用
 * */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
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
                // 解析Token，提取Claims（仅解析一次，避免重复计算）
                Claims claims = jwtUtil.parseToken(token);

                // 从Claims中提取用户身份信息
                Long userId = Long.parseLong(claims.getSubject());
                String username = claims.get("username", String.class);
                String role = claims.get("role", String.class);

                // 构建Spring Security权限列表，添加ROLE_前缀以匹配hasRole()规则
                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role)
                );

                // 将认证信息写入SecurityContext，后续鉴权由Spring Security自动完成
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                authentication.setDetails(username);

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (ExpiredJwtException e) {
                // Token过期：不设置认证信息，由后续EntryPoint返回401
                log.debug("JWT Token已过期: {}", e.getMessage());
            } catch (JwtException e) {
                // Token无效（签名错误/格式错误）：不设置认证信息，由后续EntryPoint返回401
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