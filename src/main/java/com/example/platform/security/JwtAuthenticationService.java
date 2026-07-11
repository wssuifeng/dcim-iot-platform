package com.example.platform.security;

import com.example.platform.common.util.JwtUtil;
import com.example.platform.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;

/*
 * 类功能:统一校验 JWT 签名、黑名单和角色，并创建 HTTP/WebSocket 共用的认证对象
 * */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationService {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    public Authentication authenticate(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BadCredentialsException("JWT Token不能为空");
        }

        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadCredentialsException("JWT Token无效", e);
        }

        String tokenId = claims.getId();
        if (tokenId != null && tokenBlacklistService.isTokenBlacklisted(tokenId)) {
            throw new BadCredentialsException("JWT Token已失效");
        }

        Long userId = parseUserId(claims.getSubject());
        Date issuedAt = claims.getIssuedAt();
        if (issuedAt != null && tokenBlacklistService.isUserBlacklistedAndTokenBefore(
                String.valueOf(userId), issuedAt)) {
            throw new BadCredentialsException("JWT Token已失效");
        }

        String username = claims.get("username", String.class);
        String platformRole = claims.get("platformRole", String.class);
        if (!StringUtils.hasText(username)
                || (StringUtils.hasText(platformRole)
                && !PlatformRole.PLATFORM_ADMIN.equals(platformRole))) {
            throw new BadCredentialsException("JWT Token缺少有效用户信息");
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(PlatformRole.authenticatedUserAuthority());
        if (PlatformRole.PLATFORM_ADMIN.equals(platformRole)) {
            authorities.addAll(PlatformRole.authoritiesFor(platformRole));
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId, null, authorities);
        authentication.setDetails(username);
        return authentication;
    }

    private Long parseUserId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException | NullPointerException e) {
            throw new BadCredentialsException("JWT Token用户标识无效", e);
        }
    }
}
