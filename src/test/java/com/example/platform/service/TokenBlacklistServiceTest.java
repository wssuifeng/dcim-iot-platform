package com.example.platform.service;

import com.example.platform.common.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * 类功能:TokenBlacklistService纯单元测试，覆盖单Token黑名单、用户级黑名单及时间戳对比逻辑
 *
 * 【测试策略】
 * - @ExtendWith(MockitoExtension.class)，禁止@SpringBootTest
 * - @Mock CacheService + JwtUtil，@InjectMocks TokenBlacklistService
 * - 泛型Mock约束：get(key, clazz) 用 anyString() + eq(Long.class)
 * - 基本类型参数：set(key, value, long ttl, unit) 的 ttl 用 anyLong()
 * - ArgumentCaptor 精确断言 Key、Value、TTL
 * - 时间戳断言用 isBetween() 容忍 ±2秒误差
 * - isUserBlacklistedAndTokenBefore 的边界场景用 @ParameterizedTest 合并
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenBlacklistService 单元测试")
class TokenBlacklistServiceTest {

    @Mock
    private CacheService cacheService;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    /*
     * 场景1：Token有效时，应正确提取tokenId和remainingTtl，调用cacheService.set
     * */
    @Test
    @DisplayName("addSingleTokenBlacklist - Token有效时写入黑名单")
    void addSingleTokenBlacklist_TokenValid() {
        // Given
        String token = "valid.jwt.token";
        String tokenId = "jti-abc-123";
        long remainingTtl = 3600L;
        when(jwtUtil.getTokenId(token)).thenReturn(tokenId);
        when(jwtUtil.getRemainingTtl(token)).thenReturn(remainingTtl);

        // When
        tokenBlacklistService.addSingleTokenBlacklist(token);

        // Then
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(cacheService).set(
                keyCaptor.capture(),
                eq("1"),
                ttlCaptor.capture(),
                eq(TimeUnit.SECONDS)
        );
        assertThat(keyCaptor.getValue()).isEqualTo("auth:token:blacklist:jti-abc-123");
        assertThat(ttlCaptor.getValue()).isEqualTo(3600L);
    }

    /*
     * 场景2：Token已过期（remainingTtl=0），不应写入黑名单
     * 设计理由：过期Token无需加入黑名单，Redis会自动清理，写入反而浪费内存
     * */
    @Test
    @DisplayName("addSingleTokenBlacklist - Token已过期时不写入")
    void addSingleTokenBlacklist_TokenExpired() {
        // Given
        String token = "expired.jwt.token";
        when(jwtUtil.getTokenId(token)).thenReturn("jti-expired");
        when(jwtUtil.getRemainingTtl(token)).thenReturn(0L);

        // When
        tokenBlacklistService.addSingleTokenBlacklist(token);

        // Then
        verify(cacheService, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    /*
     * 场景3：Token剩余时间极短（1秒），仍应写入黑名单
     * 设计理由：remainingTtl > 0 的边界值，1秒后Redis自动清理，刚好覆盖
     * */
    @Test
    @DisplayName("addSingleTokenBlacklist - Token剩余1秒时仍写入")
    void addSingleTokenBlacklist_TokenMinimalTtl() {
        // Given
        String token = "minimal.jwt.token";
        when(jwtUtil.getTokenId(token)).thenReturn("jti-min");
        when(jwtUtil.getRemainingTtl(token)).thenReturn(1L);

        // When
        tokenBlacklistService.addSingleTokenBlacklist(token);

        // Then
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(cacheService).set(eq("auth:token:blacklist:jti-min"), eq("1"), ttlCaptor.capture(), eq(TimeUnit.SECONDS));
        assertThat(ttlCaptor.getValue()).isEqualTo(1L);
    }

    /*
     * 场景4：管理员禁用用户时，写入用户级黑名单，Value为当前时间戳，TTL为maxTokenTtl
     * */
    @Test
    @DisplayName("addUserBlacklist - 写入禁用时间戳和最大TTL")
    void addUserBlacklist_Normal() {
        // Given
        String userId = "1001";
        long maxTtl = 7200L;
        when(jwtUtil.getMaxTokenTtl()).thenReturn(maxTtl);
        long beforeCall = System.currentTimeMillis();

        // When
        tokenBlacklistService.addUserBlacklist(userId);

        // Then
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(cacheService).set(
                keyCaptor.capture(),
                valueCaptor.capture(),
                ttlCaptor.capture(),
                eq(TimeUnit.SECONDS)
        );

        assertThat(keyCaptor.getValue()).isEqualTo("auth:user:blacklist:1001");
        assertThat(ttlCaptor.getValue()).isEqualTo(7200L);

        long capturedTimestamp = (Long) valueCaptor.getValue();
        long afterCall = System.currentTimeMillis();
        assertThat(capturedTimestamp).isBetween(beforeCall - 2000, afterCall + 2000);
    }

    /*
     * 场景5：单Token黑名单命中
     * */
    @Test
    @DisplayName("isTokenBlacklisted - 命中返回true")
    void isTokenBlacklisted_Hit() {
        // Given
        when(cacheService.hasKey("auth:token:blacklist:xyz")).thenReturn(true);

        // When
        boolean result = tokenBlacklistService.isTokenBlacklisted("xyz");

        // Then
        assertThat(result).isTrue();
    }

    /*
     * 场景6：单Token黑名单未命中
     * */
    @Test
    @DisplayName("isTokenBlacklisted - 未命中返回false")
    void isTokenBlacklisted_Miss() {
        // Given
        when(cacheService.hasKey("auth:token:blacklist:xyz")).thenReturn(false);

        // When
        boolean result = tokenBlacklistService.isTokenBlacklisted("xyz");

        // Then
        assertThat(result).isFalse();
    }

    /*
     * 场景7：用户不在黑名单（get返回null），应返回false
     * 设计理由：用户从未被禁用，无需判断时间
     * */
    @Test
    @DisplayName("isUserBlacklistedAndTokenBefore - 用户不在黑名单返回false")
    void isUserBlacklistedAndTokenBefore_UserNotInBlacklist() {
        // Given
        when(cacheService.get(anyString(), eq(Long.class))).thenReturn(null);

        // When
        boolean result = tokenBlacklistService.isUserBlacklistedAndTokenBefore(
                "1001", new Date());

        // Then
        assertThat(result).isFalse();
    }

    /*
     * 场景8/9/10：时间戳对比边界测试（参数化）
     *
     * 设计理由：
     *   - iat < 禁用时间戳 → Token在禁用前签发 → 不可信 → 返回true
     *   - iat > 禁用时间戳 → Token在禁用后签发 → 可信 → 返回false（防误杀）
     *   - iat == 禁用时间戳 → 使用 >= 判定 → 返回false（不误杀同毫秒签发的Token）
     *
     * 这三个场景本质是同一个比较逻辑的不同分支，用参数化测试消除重复代码
     * */
    @ParameterizedTest(name = "禁用时间戳={0}, 签发时间={1}, 期望结果={2}")
    @CsvSource({
            "1716800000000, 1716790000000, true",
            "1716800000000, 1716810000000, false",
            "1716800000000, 1716800000000, false"
    })
    @DisplayName("isUserBlacklistedAndTokenBefore - 时间戳对比边界")
    void isUserBlacklistedAndTokenBefore_TimestampComparison(
            long disableTimestamp, long issuedAtMs, boolean expected) {
        // Given
        when(cacheService.get(anyString(), eq(Long.class))).thenReturn(disableTimestamp);
        Date issuedAt = new Date(issuedAtMs);

        // When
        boolean result = tokenBlacklistService.isUserBlacklistedAndTokenBefore("1001", issuedAt);

        // Then
        assertThat(result).isEqualTo(expected);
    }
}
