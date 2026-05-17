package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.common.exception.DeviceNotFoundException;
import com.example.platform.entity.Alert;
import com.example.platform.entity.Device;
import com.example.platform.mapper.AlertMapper;
import com.example.platform.service.DeviceService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceImplTest {

    @Mock
    private AlertMapper alertMapper;

    @Mock
    private DeviceService deviceService;

    @InjectMocks
    private AlertServiceImpl alertService;

    @BeforeEach
    void setUp() throws Exception {
        initBaseMapper();
        initLambdaCache();
    }

    private void initBaseMapper() throws Exception {
        Class<?> clazz = AlertServiceImpl.class;
        Field baseMapperField = null;
        while (clazz != null) {
            try {
                baseMapperField = clazz.getDeclaredField("baseMapper");
                break;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        assertNotNull(baseMapperField, "应在继承链中找到baseMapper字段");
        baseMapperField.setAccessible(true);
        baseMapperField.set(alertService, alertMapper);
    }

    private void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(AlertMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, Alert.class);
    }

    private Alert buildAlert(Integer id, Integer deviceId, String alertType, int alertLevel, int isHandled) {
        Alert alert = new Alert();
        alert.setId(id);
        alert.setDeviceId(deviceId);
        alert.setAlertType(alertType);
        alert.setAlertLevel(alertLevel);
        alert.setIsHandled(isHandled);
        alert.setAlertMessage(alertType + "-测试告警");
        return alert;
    }

    @Nested
    @DisplayName("findUnhandled - 查询未处理告警")
    class FindUnhandledTests {

        @Test
        @DisplayName("应返回所有未处理告警，排除已处理告警")
        void should_returnOnlyUnhandledAlerts_when_mixedAlertsExist() {
            Alert unhandled1 = buildAlert(1, 1, "高温告警", 1, 0);
            Alert unhandled2 = buildAlert(2, 1, "低温告警", 3, 0);
            when(alertMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(unhandled1, unhandled2));

            List<Alert> result = alertService.findUnhandled();

            assertEquals(2, result.size(), "应返回2条未处理告警");
            result.forEach(alert -> assertEquals(0, alert.getIsHandled(), "所有返回的告警应为未处理状态"));
            verify(alertMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("无未处理告警时应返回空列表")
        void should_returnEmptyList_when_noUnhandledAlerts() {
            when(alertMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            List<Alert> result = alertService.findUnhandled();

            assertNotNull(result, "结果不应为null");
            assertTrue(result.isEmpty(), "无未处理告警时应返回空列表");
        }
    }

    @Nested
    @DisplayName("findByDeviceId - 根据设备ID查询告警")
    class FindByDeviceIdTests {

        @Test
        @DisplayName("应返回指定设备的告警列表")
        void should_returnAlertsForDevice_when_deviceIdIsValid() {
            Alert alert1 = buildAlert(1, 1, "高温告警", 1, 0);
            Alert alert2 = buildAlert(2, 1, "低温告警", 3, 0);
            when(alertMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(alert1, alert2));

            List<Alert> result = alertService.findByDeviceId(1);

            assertEquals(2, result.size(), "应返回2条告警");
            result.forEach(alert -> assertEquals(1, alert.getDeviceId(), "所有告警的deviceId应为1"));
            verify(alertMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("deviceId为null时应返回空集合且不调用Mapper")
        void should_returnEmptyList_when_deviceIdIsNull() {
            List<Alert> result = alertService.findByDeviceId(null);

            assertNotNull(result, "结果不应为null");
            assertTrue(result.isEmpty(), "deviceId为null时应返回空集合");
            verify(alertMapper, never()).selectList(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("设备无告警时应返回空列表")
        void should_returnEmptyList_when_deviceHasNoAlerts() {
            when(alertMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            List<Alert> result = alertService.findByDeviceId(9999);

            assertNotNull(result, "结果不应为null");
            assertTrue(result.isEmpty(), "无告警的设备应返回空列表");
        }
    }

    @Nested
    @DisplayName("batchHandle - 批量处理告警")
    class BatchHandleTests {

        @Test
        @DisplayName("应成功批量处理未处理告警并记录处理人和时间")
        void should_handleAlertsAndRecordUser_when_idsAreValid() {
            when(alertMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(2);

            int count = alertService.batchHandle(List.of(1, 2), "admin");

            assertEquals(2, count, "应成功处理2条告警");
            verify(alertMapper, times(1)).update(isNull(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("ids为null时应返回0且不执行SQL")
        void should_returnZero_when_idsIsNull() {
            int count = alertService.batchHandle(null, "admin");

            assertEquals(0, count, "ids为null时应返回0");
            verify(alertMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("ids为空集合时应返回0且不执行SQL")
        void should_returnZero_when_idsIsEmpty() {
            int count = alertService.batchHandle(new ArrayList<>(), "admin");

            assertEquals(0, count, "ids为空集合时应返回0");
            verify(alertMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("应只处理未处理的告警，跳过已处理的")
        void should_onlyHandleUnhandledAlerts_when_mixedStatusExists() {
            when(alertMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

            int count = alertService.batchHandle(List.of(1, 2), "admin");

            assertEquals(1, count, "只应处理1条未处理告警");
            verify(alertMapper, times(1)).update(isNull(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("ids中包含不存在的ID时应返回0")
        void should_returnZero_when_idsContainNonExistentId() {
            when(alertMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

            int count = alertService.batchHandle(List.of(99999), "admin");

            assertEquals(0, count, "不存在的ID应返回0");
        }
    }

    @Nested
    @DisplayName("getStatsByDeviceId - 按设备统计告警")
    class GetStatsByDeviceIdTests {

        @Test
        @DisplayName("设备存在且有告警时应返回正确的统计数据")
        void should_returnCorrectStats_when_deviceExistsWithAlerts() {
            Device device = new Device();
            device.setId(1);
            device.setDeviceName("测试设备");
            when(deviceService.getDeviceOrThrow(1)).thenReturn(device);

            Map<String, Object> expectedStats = new HashMap<>();
            expectedStats.put("alertCount", 5L);
            expectedStats.put("maxLevel", 1);
            expectedStats.put("avgLevel", 2.4);
            expectedStats.put("minLevel", 3);
            when(alertMapper.getStatsByDeviceId(1)).thenReturn(expectedStats);

            Map<String, Object> stats = alertService.getStatsByDeviceId(1);

            assertNotNull(stats, "统计数据不应为null");
            assertEquals(5L, stats.get("alertCount"), "alertCount应为5");
            assertEquals(1, stats.get("maxLevel"), "maxLevel应为1");
            assertEquals(2.4, stats.get("avgLevel"), "avgLevel应为2.4");
            assertEquals(3, stats.get("minLevel"), "minLevel应为3");
            verify(deviceService, times(1)).getDeviceOrThrow(1);
            verify(alertMapper, times(1)).getStatsByDeviceId(1);
        }

        @Test
        @DisplayName("设备不存在时应抛出DeviceNotFoundException")
        void should_throwDeviceNotFoundException_when_deviceDoesNotExist() {
            when(deviceService.getDeviceOrThrow(99999))
                    .thenThrow(new DeviceNotFoundException(99999));

            assertThrows(DeviceNotFoundException.class,
                    () -> alertService.getStatsByDeviceId(99999),
                    "设备不存在时应抛出DeviceNotFoundException");
            verify(alertMapper, never()).getStatsByDeviceId(anyInt());
        }

        @Test
        @DisplayName("设备存在但无告警时应返回alertCount为0")
        void should_returnZeroCount_when_deviceHasNoAlerts() {
            Device device = new Device();
            device.setId(2);
            when(deviceService.getDeviceOrThrow(2)).thenReturn(device);

            Map<String, Object> expectedStats = new HashMap<>();
            expectedStats.put("alertCount", 0L);
            expectedStats.put("maxLevel", null);
            expectedStats.put("avgLevel", null);
            expectedStats.put("minLevel", null);
            when(alertMapper.getStatsByDeviceId(2)).thenReturn(expectedStats);

            Map<String, Object> stats = alertService.getStatsByDeviceId(2);

            assertNotNull(stats, "统计数据不应为null");
            assertEquals(0L, stats.get("alertCount"), "无告警设备alertCount应为0");
        }
    }

    @Nested
    @DisplayName("getStatsByTimeRange - 按时间区间统计告警")
    class GetStatsByTimeRangeTests {

        @Test
        @DisplayName("有效时间区间应返回统计数据")
        void should_returnStats_when_timeRangeIsValid() {
            LocalDateTime startTime = LocalDateTime.of(2026, 5, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.of(2026, 5, 16, 0, 0);

            Map<String, Object> expectedStats = new HashMap<>();
            expectedStats.put("alertCount", 10L);
            expectedStats.put("maxLevel", 1);
            expectedStats.put("avgLevel", 2.5);
            expectedStats.put("minLevel", 5);
            when(alertMapper.getStatsByTimeRange(startTime, endTime)).thenReturn(expectedStats);

            Map<String, Object> stats = alertService.getStatsByTimeRange(startTime, endTime);

            assertNotNull(stats, "统计数据不应为null");
            assertTrue(stats.containsKey("alertCount"), "应包含alertCount键");
            assertTrue(stats.containsKey("maxLevel"), "应包含maxLevel键");
            assertTrue(stats.containsKey("avgLevel"), "应包含avgLevel键");
            assertTrue(stats.containsKey("minLevel"), "应包含minLevel键");
            verify(alertMapper, times(1)).getStatsByTimeRange(startTime, endTime);
        }

        @Test
        @DisplayName("无数据的时间区间应返回alertCount为0")
        void should_returnZeroCount_when_noDataInTimeRange() {
            LocalDateTime futureStart = LocalDateTime.of(2099, 1, 1, 0, 0);
            LocalDateTime futureEnd = LocalDateTime.of(2099, 12, 31, 23, 59);

            Map<String, Object> expectedStats = new HashMap<>();
            expectedStats.put("alertCount", 0L);
            expectedStats.put("maxLevel", null);
            expectedStats.put("avgLevel", null);
            expectedStats.put("minLevel", null);
            when(alertMapper.getStatsByTimeRange(futureStart, futureEnd)).thenReturn(expectedStats);

            Map<String, Object> stats = alertService.getStatsByTimeRange(futureStart, futureEnd);

            assertNotNull(stats, "统计数据不应为null");
            assertEquals(0L, stats.get("alertCount"), "未来时间区间alertCount应为0");
        }
    }

    @Nested
    @DisplayName("listDeviceAlertCountByTimeRange - 按时间区间统计各设备告警数量")
    class ListDeviceAlertCountByTimeRangeTests {

        @Test
        @DisplayName("多设备告警应按alertCount降序排列")
        void should_returnDeviceAlertCountsInDescOrder() {
            LocalDateTime startTime = LocalDateTime.of(2026, 5, 1, 0, 0);
            LocalDateTime endTime = LocalDateTime.of(2026, 5, 16, 0, 0);

            Map<String, Object> item1 = new HashMap<>();
            item1.put("deviceId", 1);
            item1.put("alertCount", 8L);

            Map<String, Object> item2 = new HashMap<>();
            item2.put("deviceId", 2);
            item2.put("alertCount", 3L);

            when(alertMapper.listDeviceAlertCountByTimeRange(startTime, endTime))
                    .thenReturn(List.of(item1, item2));

            List<Map<String, Object>> result = alertService.listDeviceAlertCountByTimeRange(startTime, endTime);

            assertNotNull(result, "结果不应为null");
            assertEquals(2, result.size(), "应返回2个设备的统计");
            assertEquals(8L, result.get(0).get("alertCount"), "第一个设备的alertCount应为8");
            assertEquals(3L, result.get(1).get("alertCount"), "第二个设备的alertCount应为3");
            assertTrue(((Number) result.get(0).get("alertCount")).longValue()
                            >= ((Number) result.get(1).get("alertCount")).longValue(),
                    "结果应按alertCount降序排列");
            verify(alertMapper, times(1)).listDeviceAlertCountByTimeRange(startTime, endTime);
        }

        @Test
        @DisplayName("无数据的时间区间应返回空列表")
        void should_returnEmptyList_when_noDataInTimeRange() {
            LocalDateTime futureStart = LocalDateTime.of(2099, 1, 1, 0, 0);
            LocalDateTime futureEnd = LocalDateTime.of(2099, 12, 31, 23, 59);

            when(alertMapper.listDeviceAlertCountByTimeRange(futureStart, futureEnd))
                    .thenReturn(Collections.emptyList());

            List<Map<String, Object>> result = alertService.listDeviceAlertCountByTimeRange(futureStart, futureEnd);

            assertNotNull(result, "结果不应为null");
            assertTrue(result.isEmpty(), "未来时间区间应返回空列表");
        }
    }
}
