package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.MetricSampleBatchDTO;
import com.example.platform.dto.MetricSampleDTO;
import com.example.platform.dto.MetricSampleVO;
import com.example.platform.entity.DevicePointBinding;
import com.example.platform.entity.MetricLatest;
import com.example.platform.entity.MetricSample;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.MetricSampleMapper;
import com.example.platform.service.DeviceScopeHelper;
import com.example.platform.service.DevicePointBindingService;
import com.example.platform.service.MetricLatestService;
import com.example.platform.service.PointDefinitionService;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:MetricSampleServiceImpl 单元测试
 * 覆盖 BIZ-OPEN-02：写入范围校验、联动最新值、查询必须指定 deviceId、90 天上限保护
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("MetricSampleService 单元测试")
class MetricSampleServiceImplTest {

    @Mock
    private MetricSampleMapper metricSampleMapper;
    @Mock
    private DeviceScopeHelper deviceScopeHelper;
    @Mock
    private DevicePointBindingService devicePointBindingService;
    @Mock
    private PointDefinitionService pointDefinitionService;
    @Mock
    private MetricLatestService metricLatestService;

    @InjectMocks
    private MetricSampleServiceImpl metricSampleService;

    private static final Long CURRENT_USER_ID = 20L;

    @BeforeEach
    void setUp() throws Exception {
        initBaseMapper();
        initLambdaCache();
    }

    private void initBaseMapper() throws Exception {
        Class<?> clazz = MetricSampleServiceImpl.class;
        Field field = null;
        while (clazz != null) {
            try { field = clazz.getDeclaredField("baseMapper"); break; }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        assertThat(field).as("应存在 baseMapper 字段").isNotNull();
        field.setAccessible(true);
        field.set(metricSampleService, metricSampleMapper);
    }

    private void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(MetricSampleMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, MetricSample.class);
    }

    private PointDefinition buildPoint(Long id) {
        PointDefinition p = new PointDefinition();
        p.setId(id);
        p.setPointCode("voltage_a");
        p.setPointName("A相电压");
        p.setUnit("V");
        return p;
    }

    @Nested
    @DisplayName("saveSample - 写入采样")
    class SaveSample {

        @Test
        @DisplayName("设备越权时拒绝写入")
        void should_throw_when_deviceAccessDenied() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenThrow(new DataScopeViolationException("Device", "1"));

            MetricSampleDTO dto = new MetricSampleDTO();
            dto.setDeviceId(1);
            dto.setPointId(10L);
            dto.setSampleValue(new BigDecimal("220.5"));

            assertThatThrownBy(() -> metricSampleService.saveSample(dto, CURRENT_USER_ID))
                    .isInstanceOf(DataScopeViolationException.class);
            verify(metricSampleMapper, never()).insert((MetricSample) any());
        }

        @Test
        @DisplayName("点位不存在时拒绝写入")
        void should_throw_when_pointNotFound() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenReturn(null);
            when(pointDefinitionService.getPointDefinitionOrThrow(10L))
                    .thenThrow(new BusinessException("点位不存在"));

            MetricSampleDTO dto = new MetricSampleDTO();
            dto.setDeviceId(1);
            dto.setPointId(10L);
            dto.setSampleValue(new BigDecimal("220.5"));

            assertThatThrownBy(() -> metricSampleService.saveSample(dto, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class);
            verify(metricSampleMapper, never()).insert((MetricSample) any());
        }

        @Test
        @DisplayName("写入成功后联动 upsert 最新值")
        void should_saveAndUpsertLatest() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenReturn(null);
            when(pointDefinitionService.getPointDefinitionOrThrow(10L)).thenReturn(buildPoint(10L));
            DevicePointBinding activeBinding = new DevicePointBinding();
            activeBinding.setDeviceId(1);
            activeBinding.setPointId(10L);
            activeBinding.setStatus(0);
            when(devicePointBindingService.getActiveBindingOrThrow(1, 10L, CURRENT_USER_ID))
                    .thenReturn(activeBinding);
            when(metricSampleMapper.insert((MetricSample) any())).thenReturn(1);
            MetricLatest latestMock = new MetricLatest();
            latestMock.setDeviceId(1); latestMock.setPointId(10L);
            when(metricLatestService.upsertLatest(eq(1), eq(10L), eq(new BigDecimal("220.5")), any(LocalDateTime.class)))
                    .thenReturn(latestMock);

            MetricSampleDTO dto = new MetricSampleDTO();
            dto.setDeviceId(1);
            dto.setPointId(10L);
            dto.setSampleValue(new BigDecimal("220.5"));

            MetricSample saved = metricSampleService.saveSample(dto, CURRENT_USER_ID);
            assertThat(saved.getDeviceId()).isEqualTo(1);
            verify(metricSampleMapper, times(1)).insert((MetricSample) any());
            verify(metricLatestService, times(1))
                    .upsertLatest(eq(1), eq(10L), eq(new BigDecimal("220.5")), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("未来采样时间超过容忍窗口应拒绝")
        void should_rejectFutureSampleTime() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenReturn(null);
            when(pointDefinitionService.getPointDefinitionOrThrow(10L)).thenReturn(buildPoint(10L));
            DevicePointBinding activeBinding = new DevicePointBinding();
            activeBinding.setDeviceId(1);
            activeBinding.setPointId(10L);
            activeBinding.setStatus(0);
            when(devicePointBindingService.getActiveBindingOrThrow(1, 10L, CURRENT_USER_ID))
                    .thenReturn(activeBinding);

            MetricSampleDTO dto = new MetricSampleDTO();
            dto.setDeviceId(1);
            dto.setPointId(10L);
            dto.setSampleValue(new BigDecimal("220.5"));
            dto.setSampleTime(LocalDateTime.now().plusMinutes(1));

            assertThatThrownBy(() -> metricSampleService.saveSample(dto, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("采样时间不能晚于");
            verify(metricSampleMapper, never()).insert((MetricSample) any());
            verify(metricLatestService, never()).upsertLatest(any(), any(), any(), any());
        }

        @Test
        @DisplayName("BOOLEAN 点位仅接受 0 或 1")
        void should_rejectInvalidBooleanValue() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenReturn(null);
            PointDefinition booleanPoint = buildPoint(10L);
            booleanPoint.setDataType("BOOLEAN");
            when(pointDefinitionService.getPointDefinitionOrThrow(10L)).thenReturn(booleanPoint);
            DevicePointBinding activeBinding = new DevicePointBinding();
            activeBinding.setDeviceId(1);
            activeBinding.setPointId(10L);
            activeBinding.setStatus(0);
            when(devicePointBindingService.getActiveBindingOrThrow(1, 10L, CURRENT_USER_ID))
                    .thenReturn(activeBinding);

            MetricSampleDTO dto = new MetricSampleDTO();
            dto.setDeviceId(1);
            dto.setPointId(10L);
            dto.setSampleValue(new BigDecimal("2"));

            assertThatThrownBy(() -> metricSampleService.saveSample(dto, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅允许 0 或 1");
            verify(metricSampleMapper, never()).insert((MetricSample) any());
        }

        @Test
        @DisplayName("批量写入逐条联动最新值")
        void should_batchSave() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID))).thenReturn(null);

            PointDefinition p10 = buildPoint(10L);
            PointDefinition p20 = buildPoint(20L);
            when(pointDefinitionService.getPointDefinitionOrThrow(10L)).thenReturn(p10);
            when(pointDefinitionService.getPointDefinitionOrThrow(20L)).thenReturn(p20);
            DevicePointBinding binding10 = new DevicePointBinding();
            binding10.setDeviceId(1); binding10.setPointId(10L); binding10.setStatus(0);
            DevicePointBinding binding20 = new DevicePointBinding();
            binding20.setDeviceId(1); binding20.setPointId(20L); binding20.setStatus(0);
            when(devicePointBindingService.getActiveBindingOrThrow(1, 10L, CURRENT_USER_ID))
                    .thenReturn(binding10);
            when(devicePointBindingService.getActiveBindingOrThrow(1, 20L, CURRENT_USER_ID))
                    .thenReturn(binding20);
            when(metricSampleMapper.insert((MetricSample) any())).thenReturn(1);

            MetricSampleDTO d1 = new MetricSampleDTO();
            d1.setDeviceId(1); d1.setPointId(10L); d1.setSampleValue(new BigDecimal("220.5"));

            MetricSampleDTO d2 = new MetricSampleDTO();
            d2.setDeviceId(1); d2.setPointId(20L); d2.setSampleValue(new BigDecimal("55.1"));

            MetricSampleBatchDTO batch = new MetricSampleBatchDTO();
            batch.setItems(List.of(d1, d2));

            List<MetricSampleVO> result = metricSampleService.saveBatch(batch, CURRENT_USER_ID);
            assertThat(result).hasSize(2);
            verify(metricSampleMapper, times(2)).insert((MetricSample) any());
        }
    }

    @Nested
    @DisplayName("pageSamples - 分页查询")
    class PageSamples {

        @Test
        @DisplayName("未指定 deviceId 应拒绝（防止跨设备扫描）")
        void should_throw_when_noDeviceId() {
            assertThatThrownBy(() -> metricSampleService.pageSamples(
                    CURRENT_USER_ID, 1, 10, null, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("必须指定 deviceId");
        }

        @Test
        @DisplayName("范围校验失败时拒绝")
        void should_throw_when_deviceAccessDenied() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenThrow(new DataScopeViolationException("Device", "1"));

            assertThatThrownBy(() -> metricSampleService.pageSamples(
                    CURRENT_USER_ID, 1, 10, 1, null, null, null))
                    .isInstanceOf(DataScopeViolationException.class);
        }

        @Test
        @DisplayName("查询时间跨度超过 90 天应拒绝")
        void should_throw_when_timeRangeExceed90Days() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID))).thenReturn(null);

            LocalDateTime start = LocalDateTime.now().minusDays(110);
            LocalDateTime end = LocalDateTime.now();

            assertThatThrownBy(() -> metricSampleService.pageSamples(
                    CURRENT_USER_ID, 1, 10, 1, null, start, end))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("时间跨度不能超过");
        }

        @Test
        @DisplayName("startTime>endTime 应拒绝")
        void should_throw_when_startAfterEnd() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID))).thenReturn(null);

            LocalDateTime start = LocalDateTime.now();
            LocalDateTime end = LocalDateTime.now().minusDays(1);

            assertThatThrownBy(() -> metricSampleService.pageSamples(
                    CURRENT_USER_ID, 1, 10, 1, null, start, end))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("起始时间不能晚于结束时间");
        }

        @Test
        @DisplayName("未传时间范围时默认取最近90天")
        void should_defaultTimeRange_whenAbsent() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID))).thenReturn(null);
            Page<MetricSample> mockPage = new Page<>(1, 10);
            mockPage.setRecords(List.of());
            when(metricSampleMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            Page<MetricSampleVO> result = metricSampleService.pageSamples(
                    CURRENT_USER_ID, 1, 10, 1, null, null, null);
            assertThat(result.getRecords()).isEmpty();
            verify(metricSampleMapper, times(1))
                    .selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }
    }
}
