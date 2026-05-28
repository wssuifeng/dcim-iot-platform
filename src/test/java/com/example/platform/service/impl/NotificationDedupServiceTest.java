package com.example.platform.service.impl;

import com.example.platform.service.CacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * 类功能:NotificationDedupServiceImpl纯单元测试，覆盖首次通知/重复通知/异常降级/释放标记等场景
 *
 * 【测试策略】
 * - @ExtendWith(MockitoExtension.class)，禁止@SpringBootTest
 * - @Mock CacheService，@InjectMocks NotificationDedupServiceImpl
 * - ArgumentCaptor 精确断言去重Key格式、Value、TTL、TimeUnit
 * - 异常降级：Mock抛异常，验证返回true（放行通知）或仅记录日志（releaseDedupMark）
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDedupService 单元测试")
class NotificationDedupServiceTest {

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private NotificationDedupServiceImpl notificationDedupService;

    @Nested
    @DisplayName("shouldSendNotification - 告警通知去重判断")
    class ShouldSendNotificationTests {

        @Test
        @DisplayName("首次通知 → setIfAbsent返回true → 应发送通知")
        void shouldReturnTrue_when_FirstNotification() {
            when(cacheService.setIfAbsent(anyString(), any(), anyLong(), eq(TimeUnit.HOURS)))
                    .thenReturn(true);

            boolean result = notificationDedupService.shouldSendNotification(100);

            assertThat(result).isTrue();

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
            ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
            verify(cacheService).setIfAbsent(
                    keyCaptor.capture(),
                    valueCaptor.capture(),
                    ttlCaptor.capture(),
                    eq(TimeUnit.HOURS)
            );
            assertThat(keyCaptor.getValue()).isEqualTo("notify:alert:100");
            assertThat(valueCaptor.getValue()).isEqualTo("1");
            assertThat(ttlCaptor.getValue()).isEqualTo(48L);
        }

        @Test
        @DisplayName("重复通知 → setIfAbsent返回false → 不发送通知")
        void shouldReturnFalse_when_DuplicateNotification() {
            when(cacheService.setIfAbsent(anyString(), any(), anyLong(), eq(TimeUnit.HOURS)))
                    .thenReturn(false);

            boolean result = notificationDedupService.shouldSendNotification(100);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Redis异常 → setIfAbsent抛异常 → 降级放行通知返回true")
        void shouldReturnTrue_when_RedisException() {
            when(cacheService.setIfAbsent(anyString(), any(), anyLong(), eq(TimeUnit.HOURS)))
                    .thenThrow(new RuntimeException("Redis connection refused"));

            boolean result = notificationDedupService.shouldSendNotification(100);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("不同alertId → 各自独立去重")
        void shouldDeduplicateIndependently_when_DifferentAlertIds() {
            when(cacheService.setIfAbsent(anyString(), any(), anyLong(), eq(TimeUnit.HOURS)))
                    .thenReturn(true);

            boolean result1 = notificationDedupService.shouldSendNotification(100);
            boolean result2 = notificationDedupService.shouldSendNotification(101);

            assertThat(result1).isTrue();
            assertThat(result2).isTrue();

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(cacheService, times(2)).setIfAbsent(
                    keyCaptor.capture(),
                    any(),
                    anyLong(),
                    eq(TimeUnit.HOURS)
            );
            assertThat(keyCaptor.getAllValues())
                    .containsExactly("notify:alert:100", "notify:alert:101");
        }

        @Test
        @DisplayName("处理后再触发新告警 → 新alertId → 正确发送通知（核心场景）")
        void shouldSendNotification_when_NewAlertAfterHandling() {
            when(cacheService.setIfAbsent(eq("notify:alert:100"), any(), anyLong(), eq(TimeUnit.HOURS)))
                    .thenReturn(true);
            when(cacheService.setIfAbsent(eq("notify:alert:101"), any(), anyLong(), eq(TimeUnit.HOURS)))
                    .thenReturn(true);

            boolean result1 = notificationDedupService.shouldSendNotification(100);
            notificationDedupService.releaseDedupMark(100);
            boolean result2 = notificationDedupService.shouldSendNotification(101);

            assertThat(result1).isTrue();
            assertThat(result2).isTrue();
            verify(cacheService).delete("notify:alert:100");
        }

        @Test
        @DisplayName("releaseDedupMark失败后新告警仍可发送（alertId天然隔离）")
        void shouldStillSend_when_ReleaseFailsAndNewAlertTriggered() {
            when(cacheService.setIfAbsent(eq("notify:alert:100"), any(), anyLong(), eq(TimeUnit.HOURS)))
                    .thenReturn(true);
            when(cacheService.setIfAbsent(eq("notify:alert:101"), any(), anyLong(), eq(TimeUnit.HOURS)))
                    .thenReturn(true);
            when(cacheService.delete("notify:alert:100")).thenThrow(new RuntimeException("Redis down"));

            boolean result1 = notificationDedupService.shouldSendNotification(100);
            notificationDedupService.releaseDedupMark(100);
            boolean result2 = notificationDedupService.shouldSendNotification(101);

            assertThat(result1).isTrue();
            assertThat(result2).isTrue();
        }
    }

    @Nested
    @DisplayName("releaseDedupMark - 释放告警去重标记")
    class ReleaseDedupMarkTests {

        @Test
        @DisplayName("正常释放 → 调用cacheService.delete")
        void shouldDeleteKey_when_NormalRelease() {
            when(cacheService.delete(anyString())).thenReturn(true);

            notificationDedupService.releaseDedupMark(100);

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(cacheService).delete(keyCaptor.capture());
            assertThat(keyCaptor.getValue()).isEqualTo("notify:alert:100");
        }

        @Test
        @DisplayName("Redis异常 → 仅记录日志，不抛异常（不影响正确性）")
        void shouldNotThrow_when_RedisException() {
            when(cacheService.delete(anyString())).thenThrow(new RuntimeException("Redis down"));

            notificationDedupService.releaseDedupMark(100);

            verify(cacheService).delete("notify:alert:100");
        }
    }
}
