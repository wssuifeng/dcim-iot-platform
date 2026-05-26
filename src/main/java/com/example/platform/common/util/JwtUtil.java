package com.example.platform.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/*
 * 类功能:JWT工具类，负责Token的生成、解析与有效性验证
 * */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    /*
     * 方法功能:获取Token有效时长（秒）
     * 返回数据:Long-有效时长
     * */
    @Getter
    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey signingKey;

    /*
     * 方法功能:Bean初始化后预生成HMAC-SHA签名密钥，避免每次请求重复计算
     * 返回数据:无
     * */
    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /*
     * 方法功能:生成JWT Token
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

}