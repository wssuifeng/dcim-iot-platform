package com.example.platform.service;

import com.example.platform.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/*
 * 类功能:Token黑名单服务，管理单Token黑名单和用户级黑名单
 *
 * 【双层黑名单架构】
 * 1. 单Token黑名单(auth:token:blacklist:{tokenId})
 *    - 精确登出：用户主动登出时，仅将当前Token加入黑名单
 *    - TTL = Token剩余有效期，Token自然过期后黑名单记录自动回收
 *
 * 2. 用户级黑名单(auth:user:blacklist:{userId})
 *    - 架构降维：无需维护"用户→Token列表"的复杂映射
 *    - 值为禁用时的毫秒时间戳，通过iat对比判断Token是否在禁用前签发
 *    - TTL = Token最大有效期，确保禁用记录覆盖所有可能存活的Token
 *    - 核心洞察：不需要知道"哪些Token被禁用"，只需知道"禁用时间点之前的Token都不可信"
 *
 * 【为什么不用Set维护Token列表？】
 *   - 并发更新丢失：两个线程同时读写Set，后写覆盖前写
 *   - JSON序列化开销：每次读写整个集合，Token多时Value达数KB
 *   - 生命周期管理：Redis Set不支持per-member TTL，需定时任务清理过期Token
 *   - 时间戳对比方案一个Key一个Long值，零并发问题，零序列化开销
 * */
@Slf4j
@Service
public class TokenBlacklistService {

    private static final String TOKEN_BLACKLIST_PREFIX = "auth:token:blacklist:";
    private static final String USER_BLACKLIST_PREFIX = "auth:user:blacklist:";

    private final CacheService cacheService;
    private final JwtUtil jwtUtil;

    public TokenBlacklistService(CacheService cacheService, JwtUtil jwtUtil) {
        this.cacheService = cacheService;
        this.jwtUtil = jwtUtil;
    }

    /*
     * 方法功能:将单个Token加入黑名单（用户主动登出时调用）
     * 流程:提取tokenId → 计算剩余TTL → 写入黑名单Key
     * 参数:token-JWT Token字符串
     * 返回数据:无
     * */
    public void addSingleTokenBlacklist(String token) {
        String tokenId = jwtUtil.getTokenId(token);
        long remainingTtl = jwtUtil.getRemainingTtl(token);
        if (remainingTtl > 0) {
            cacheService.set(TOKEN_BLACKLIST_PREFIX + tokenId, "1", remainingTtl, TimeUnit.SECONDS);
            log.info("Token已加入黑名单: tokenId={}, ttl={}s", tokenId, remainingTtl);
        }
    }

    /*
     * 方法功能:将用户加入黑名单（管理员禁用用户时调用）
     * 流程:获取当前时间戳 → 写入用户黑名单Key，TTL为Token最大有效期
     *
     * 【为什么TTL是Token最大有效期？】
     * 禁用后最坏情况下，用户可能持有一个刚签发的Token（剩余有效期≈最大有效期），
     * 所以黑名单记录必须存活至少这么久才能覆盖所有可能存活的Token。
     *
     * 参数:userId-用户ID
     * 返回数据:无
     * */
    public void addUserBlacklist(String userId) {
        long disableTimestamp = System.currentTimeMillis();
        long maxTtl = jwtUtil.getMaxTokenTtl();
        cacheService.set(USER_BLACKLIST_PREFIX + userId, disableTimestamp, maxTtl, TimeUnit.SECONDS);
        log.info("用户已加入黑名单: userId={}, disableTimestamp={}, ttl={}s", userId, disableTimestamp, maxTtl);
    }

    /*
     * 方法功能:检查单Token是否在黑名单中
     * 参数:tokenId-JWT唯一ID(jti)
     * 返回数据:boolean-在黑名单中返回true
     * */
    public boolean isTokenBlacklisted(String tokenId) {
        return cacheService.hasKey(TOKEN_BLACKLIST_PREFIX + tokenId);
    }

    /*
     * 方法功能:检查用户级黑名单，判断Token是否在禁用前签发
     *
     * 【时间戳对比逻辑】
     *   - 用户黑名单值 = 禁用时的毫秒时间戳
     *   - Token的iat = 签发时间的毫秒时间戳
     *   - iat < 禁用时间戳 → Token在禁用前签发 → 不可信 → 返回true
     *   - iat >= 禁用时间戳 → Token在禁用后签发（重新登录获得） → 可信 → 返回false
     *
     * 这种设计实现了"只作废旧Token，不误杀新Token"，且无需维护Token列表
     *
     * 参数:userId-用户ID, issuedAt-Token签发时间
     * 返回数据:boolean-Token应被拒绝返回true
     * */
    public boolean isUserBlacklistedAndTokenBefore(String userId, Date issuedAt) {
        Long disableTimestamp = cacheService.get(USER_BLACKLIST_PREFIX + userId, Long.class);
        if (disableTimestamp == null) {
            return false;
        }
        return issuedAt.getTime() < disableTimestamp;
    }
}
