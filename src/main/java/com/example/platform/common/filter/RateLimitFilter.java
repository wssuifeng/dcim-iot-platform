package com.example.platform.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/*
 * 类功能:基于 Redis 滑动窗口的 IP 限流过滤器
 *
 * 使用有序集合（ZSET）记录每个 IP 在窗口期内的请求时间戳，
 * 每次请求前清理过期成员，若当前请求数未超过阈值则放行，否则返回 429。
 *
 * 优先级次于 TraceIdFilter，确保 traceId 先生成。
 * */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_KEY_PREFIX = "rate:limit:";

    /*
     * 滑动窗口限流 Lua 脚本
     * KEYS[1]   : 限流 key
     * ARGV[1]   : 窗口时长（秒）
     * ARGV[2]   : 窗口内最大请求数
     * ARGV[3]   : 当前时间戳（毫秒）
     * 返回值 0  : 允许通过
     * 返回值 1  : 拒绝请求
     *
     * member 使用 INCR 生成的唯一序号，避免同毫秒请求 ZADD 互相覆盖导致计数失真
     * */
    private static final String SLIDE_WINDOW_LUA =
            "local key = KEYS[1] " +
            "local window = tonumber(ARGV[1]) " +
            "local limit = tonumber(ARGV[2]) " +
            "local now = tonumber(ARGV[3]) " +
            "redis.call('ZREMRANGEBYSCORE', key, 0, now - window * 1000) " +
            "local count = redis.call('ZCARD', key) " +
            "if count < limit then " +
            "    local seq = redis.call('INCR', key .. ':seq') " +
            "    redis.call('ZADD', key, now, seq) " +
            "    redis.call('EXPIRE', key, window) " +
            "    redis.call('EXPIRE', key .. ':seq', window) " +
            "    return 0 " +
            "else " +
            "    return 1 " +
            "end";

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    @Value("${rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Value("${rate-limit.max-requests:100}")
    private int maxRequests;

    @Value("${rate-limit.enabled:true}")
    private boolean enabled;

    public RateLimitFilter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setScriptText(SLIDE_WINDOW_LUA);
        this.rateLimitScript.setResultType(Long.class);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || isStaticResource(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String key = RATE_LIMIT_KEY_PREFIX + clientIp;
        long now = System.currentTimeMillis();

        try {
            List<String> keys = Collections.singletonList(key);
            Long result = stringRedisTemplate.execute(rateLimitScript, keys,
                    String.valueOf(windowSeconds), String.valueOf(maxRequests), String.valueOf(now));

            if (result != null && result == 1L) {
                log.warn("IP 限流触发: ip={}, uri={}", clientIp, request.getRequestURI());
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":400,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}");
                return;
            }
        } catch (Exception e) {
            log.warn("限流 Redis 脚本执行失败，降级放行: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private boolean isStaticResource(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/actuator") || uri.startsWith("/favicon.ico");
    }

    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
