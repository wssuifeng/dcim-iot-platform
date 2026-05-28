package com.example.platform.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/*
 * 类功能:JWT工具类，负责Token的生成、解析与有效性验证
 * */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Getter
    @Value("${jwt.expiration}")
    private Long expiration;

    @Getter
    @Value("${jwt.lock-duration:1800}")
    private Long lockDuration;

    @Getter
    @Value("${jwt.fail-window:1800}")
    private Long failWindow;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /*
     * 方法功能:生成JWT Token，包含唯一ID(jti)用于黑名单精确标识
     * 参数:userId-用户ID, username-用户名, role-角色标识
     * 返回数据:String-JWT Token字符串
     * */
    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration * 1000);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    /*
     * 方法功能:解析JWT Token，提取Claims
     * 参数:token-JWT Token字符串
     * 返回数据:Claims-Token中携带的声明信息
     * */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /*
     * 方法功能:从Token中提取用户ID
     * 参数:token-JWT Token字符串
     * 返回数据:Long-用户ID
     * */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /*
     * 方法功能:从Token中提取用户名
     * 参数:token-JWT Token字符串
     * 返回数据:String-用户名
     * */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /*
     * 方法功能:从Token中提取角色
     * 参数:token-JWT Token字符串
     * 返回数据:String-角色标识
     * */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    /*
     * 方法功能:从Token中提取JWT唯一ID(jti)，用于单Token黑名单精确标识
     * 参数:token-JWT Token字符串
     * 返回数据:String-JWT唯一ID
     * */
    public String getTokenId(String token) {
        return parseToken(token).getId();
    }

    /*
     * 方法功能:从Token中提取签发时间(iat)，用于用户级黑名单的时间戳对比
     * 参数:token-JWT Token字符串
     * 返回数据:Date-Token签发时间
     * */
    public Date getIssuedAt(String token) {
        return parseToken(token).getIssuedAt();
    }

    /*
     * 方法功能:计算Token剩余有效期（秒），用于单Token黑名单的TTL
     * 参数:token-JWT Token字符串
     * 返回数据:long-剩余有效秒数，已过期返回0
     * */
    public long getRemainingTtl(String token) {
        Date expiration = parseToken(token).getExpiration();
        long remainingMs = expiration.getTime() - System.currentTimeMillis();
        return remainingMs > 0 ? remainingMs / 1000 : 0;
    }

    /*
     * 方法功能:获取系统配置的Token最大有效期（秒），用于用户级黑名单的TTL
     * 返回数据:long-最大有效秒数
     * */
    public long getMaxTokenTtl() {
        return expiration;
    }
}
