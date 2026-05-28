package com.example.platform.service.impl;

import com.example.platform.dto.DashboardStatsDTO;
import com.example.platform.service.AlertService;
import com.example.platform.service.CacheService;
import com.example.platform.service.DeviceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * 类功能:DashboardStatsServiceImpl纯单元测试，覆盖缓存命中/互斥锁获锁/获锁失败重试/降级查DB等场景
 *
 * 【测试策略】
 * - @ExtendWith(MockitoExtension.class)，禁止@SpringBootTest
 * - @Mock CacheService + DeviceService + AlertService，@InjectMocks DashboardStatsServiceImpl
 * - 泛型Mock约束：get(key, clazz) 用 anyString() + eq(DashboardStatsDTO.class)
 * - ArgumentCaptor 精确断言缓存Key、Value、TTL
 * - Thread.sleep 在获锁失败重试场景中由Mock控制，无需真实等待
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardStatsService 单元测试")
class DashboardStatsServiceTest {

    @Mock
    private CacheService cacheService;

    @Mock
    private DeviceService deviceService;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private DashboardStatsServiceImpl dashboardStatsService;

    /*
     * 场景1：缓存命中 → 直接返回，不查DB
     * 设计理由：最常见的场景，验证缓存穿透防护的第一道防线
     * */
    @Test
    @DisplayName("getDashboardStats - 缓存命中直接返回")
    void getDashboardStats_CacheHit() {
        // Given
        DashboardStatsDTO cached = buildMockStats(10, 5, 3, 2, 100L, 20L, 15L);
        when(cacheService.get("stats:dashboard", DashboardStatsDTO.class)).thenReturn(cached);

        // When
        DashboardStatsDTO result = dashboardStatsService.getDashboardStats();

        // Then
        assertThat(result).isSameAs(cached);
        verify(deviceService, never()).count();
        verify(alertService, never()).count();
    }

    /*
     * 场景2：缓存未命中 + 获锁成功 → 查DB + 回填缓存 + 释放锁
     * 设计理由：验证互斥锁获锁后的完整重建流程
     * */
    @Test
    @DisplayName("getDashboardStats - 缓存未命中获锁成功，查DB回填缓存")
    void getDashboardStats_CacheMissLockAcquired() {
        // Given
        when(cacheService.get("stats:dashboard", DashboardStatsDTO.class)).thenReturn(null);
        when(cacheService.setIfAbsent(eq("stats:dashboard:lock"), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(deviceService.count()).thenReturn(10L);
        when(deviceService.countByStatus()).thenReturn(buildStatusCounts());
        when(alertService.count()).thenReturn(100L);
        when(alertService.count(any())).thenReturn(20L);
        when(alertService.getStatsByTimeRange(any(), any())).thenReturn(Map.of("alertCount", 15));
        when(cacheService.delete("stats:dashboard:lock")).thenReturn(true);

        // When
        DashboardStatsDTO result = dashboardStatsService.getDashboardStats();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDeviceStats().getTotal()).isEqualTo(10);
        assertThat(result.getDeviceStats().getOnline()).isEqualTo(5);
        assertThat(result.getDeviceStats().getOffline()).isEqualTo(3);
        assertThat(result.getDeviceStats().getFault()).isEqualTo(2);
        assertThat(result.getAlertStats().getTotal()).isEqualTo(100);
        assertThat(result.getAlertStats().getUnhandled()).isEqualTo(20);
        assertThat(result.getAlertStats().getLast24h()).isEqualTo(15);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(cacheService).set(
                keyCaptor.capture(),
                any(DashboardStatsDTO.class),
                ttlCaptor.capture(),
                eq(TimeUnit.SECONDS)
        );
        assertThat(keyCaptor.getValue()).isEqualTo("stats:dashboard");
        assertThat(ttlCaptor.getValue()).isEqualTo(30L);

        verify(cacheService).delete("stats:dashboard:lock");
    }

    /*
     * 场景3：缓存未命中 + 获锁失败 → 重试后缓存可读
     * 设计理由：验证获锁失败时的重试机制，其他线程重建缓存后本线程能读到
     * */
    @Test
    @DisplayName("getDashboardStats - 获锁失败重试后读到缓存")
    void getDashboardStats_LockFailedRetrySuccess() {
        // Given
        DashboardStatsDTO cached = buildMockStats(5, 2, 2, 1, 50L, 10L, 5L);
        when(cacheService.get("stats:dashboard", DashboardStatsDTO.class))
                .thenReturn(null)
                .thenReturn(null)
                .thenReturn(cached);
        when(cacheService.setIfAbsent(eq("stats:dashboard:lock"), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(false);

        // When
        DashboardStatsDTO result = dashboardStatsService.getDashboardStats();

        // Then
        assertThat(result).isSameAs(cached);
        verify(deviceService, never()).count();
    }

    /*
     * 场景4：缓存未命中 + 获锁失败 + 重试耗尽 → 降级直接查DB
     * 设计理由：极端场景，其他线程重建缓存失败，本线程重试耗尽后仍需返回数据
     * */
    @Test
    @DisplayName("getDashboardStats - 重试耗尽降级查DB")
    void getDashboardStats_RetryExhaustedFallbackToDB() {
        // Given
        when(cacheService.get("stats:dashboard", DashboardStatsDTO.class)).thenReturn(null);
        when(cacheService.setIfAbsent(eq("stats:dashboard:lock"), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(false);
        when(deviceService.count()).thenReturn(8L);
        when(deviceService.countByStatus()).thenReturn(buildStatusCounts());
        when(alertService.count()).thenReturn(80L);
        when(alertService.count(any())).thenReturn(15L);
        when(alertService.getStatsByTimeRange(any(), any())).thenReturn(Map.of("alertCount", 10));

        // When
        DashboardStatsDTO result = dashboardStatsService.getDashboardStats();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDeviceStats().getTotal()).isEqualTo(8);
        verify(deviceService).count();
    }

    /*
     * 场景5：获锁成功后DB查询异常 → 仍返回结果（buildFromDB内部不会抛异常）
     * 设计理由：验证异常不会向上传播，保证接口可用性
     * */
    @Test
    @DisplayName("getDashboardStats - DB查询时getStatsByTimeRange返回null不崩溃")
    void getDashboardStats_StatsByTimeRangeReturnsNull() {
        // Given
        when(cacheService.get("stats:dashboard", DashboardStatsDTO.class)).thenReturn(null);
        when(cacheService.setIfAbsent(eq("stats:dashboard:lock"), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(deviceService.count()).thenReturn(5L);
        when(deviceService.countByStatus()).thenReturn(List.of());
        when(alertService.count()).thenReturn(50L);
        when(alertService.count(any())).thenReturn(10L);
        when(alertService.getStatsByTimeRange(any(), any())).thenReturn(null);
        when(cacheService.delete("stats:dashboard:lock")).thenReturn(true);

        // When
        DashboardStatsDTO result = dashboardStatsService.getDashboardStats();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAlertStats().getLast24h()).isEqualTo(0);
        verify(cacheService).delete("stats:dashboard:lock");
    }

    /*
     * 场景6：获锁成功后getStatsByTimeRange返回的Map中alertCount为null
     * 设计理由：验证对null值的防御性处理
     * */
    @Test
    @DisplayName("getDashboardStats - alertCount为null时last24h默认0")
    void getDashboardStats_AlertCountNull() {
        // Given
        when(cacheService.get("stats:dashboard", DashboardStatsDTO.class)).thenReturn(null);
        when(cacheService.setIfAbsent(eq("stats:dashboard:lock"), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(deviceService.count()).thenReturn(5L);
        when(deviceService.countByStatus()).thenReturn(List.of());
        when(alertService.count()).thenReturn(50L);
        when(alertService.count(any())).thenReturn(10L);
        Map<String, Object> statsWithNull = new HashMap<>();
        statsWithNull.put("alertCount", null);
        when(alertService.getStatsByTimeRange(any(), any())).thenReturn(statsWithNull);
        when(cacheService.delete("stats:dashboard:lock")).thenReturn(true);

        // When
        DashboardStatsDTO result = dashboardStatsService.getDashboardStats();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAlertStats().getLast24h()).isEqualTo(0);
    }

    private DashboardStatsDTO buildMockStats(int total, int online, int offline, int fault,
                                              long alertTotal, long unhandled, long last24h) {
        DashboardStatsDTO dto = new DashboardStatsDTO();
        DashboardStatsDTO.DeviceStats deviceStats = new DashboardStatsDTO.DeviceStats();
        deviceStats.setTotal(total);
        deviceStats.setOnline(online);
        deviceStats.setOffline(offline);
        deviceStats.setFault(fault);
        dto.setDeviceStats(deviceStats);

        DashboardStatsDTO.AlertStats alertStats = new DashboardStatsDTO.AlertStats();
        alertStats.setTotal(alertTotal);
        alertStats.setUnhandled(unhandled);
        alertStats.setLast24h(last24h);
        dto.setAlertStats(alertStats);
        return dto;
    }

    private List<Map<String, Object>> buildStatusCounts() {
        return List.of(
                Map.of("status", 0, "count", 3),
                Map.of("status", 1, "count", 5),
                Map.of("status", 2, "count", 2)
        );
    }
}
