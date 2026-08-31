package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.MetricSampleBatchDTO;
import com.example.platform.dto.MetricSampleBatchResultVO;
import com.example.platform.dto.MetricSampleDTO;
import com.example.platform.dto.MetricSampleTrendPointVO;
import com.example.platform.dto.MetricSampleVO;
import com.example.platform.entity.DevicePointBinding;
import com.example.platform.entity.MetricLatest;
import com.example.platform.entity.MetricSample;
import com.example.platform.entity.PointDefinition;
import com.example.platform.event.MetricSampleSavedEvent;
import com.example.platform.mapper.MetricSampleMapper;
import com.example.platform.service.DeviceScopeHelper;
import com.example.platform.service.DevicePointBindingService;
import com.example.platform.service.DeviceService;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:MetricSampleServiceImpl 单元测试
 * 覆盖：写入设备访问校验、联动最新值、设备在线状态刷新、triggerAlarm 事件分流、
 *   双时间基准容忍窗口、批量逐条独立语义（部分成功合法）、90 天查询上限
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
    @Mock
    private DeviceService deviceService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private com.example.platform.mapper.MetricSampleHourlyMapper metricSampleHourlyMapper;

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
        // 设备在线状态刷新使用 LambdaUpdateWrapper<Device>，需初始化 Device 表信息
        MapperBuilderAssistant deviceAssistant = new MapperBuilderAssistant(configuration, "");
        deviceAssistant.setCurrentNamespace(com.example.platform.mapper.DeviceMapper.class.getName());
        TableInfoHelper.initTableInfo(deviceAssistant, com.example.platform.entity.Device.class);
    }

    private PointDefinition buildPoint(Long id) {
        PointDefinition p = new PointDefinition();
        p.setId(id);
        p.setPointCode("voltage_a");
        p.setPointName("A相电压");
        p.setUnit("V");
        return p;
    }

    private DevicePointBinding activeBinding(Integer deviceId, Long pointId) {
        DevicePointBinding binding = new DevicePointBinding();
        binding.setDeviceId(deviceId);
        binding.setPointId(pointId);
        binding.setStatus(0);
        return binding;
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

            assertThatThrownBy(() -> metricSampleService.saveSample(dto, CURRENT_USER_ID, true))
                    .isInstanceOf(DataScopeViolationException.class);
            verify(metricSampleMapper, never()).insert((MetricSample) any());
        }

        @Test
        @DisplayName("指标不存在时拒绝写入")
        void should_throw_when_pointNotFound() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenReturn(null);
            when(pointDefinitionService.getPointDefinitionOrThrow(10L))
                    .thenThrow(new BusinessException("指标不存在"));

            MetricSampleDTO dto = new MetricSampleDTO();
            dto.setDeviceId(1);
            dto.setPointId(10L);
            dto.setSampleValue(new BigDecimal("220.5"));

            assertThatThrownBy(() -> metricSampleService.saveSample(dto, CURRENT_USER_ID, true))
                    .isInstanceOf(BusinessException.class);
            verify(metricSampleMapper, never()).insert((MetricSample) any());
        }

        @Test
        @DisplayName("写入成功后联动 upsert 最新值、刷设备在线并发布评估事件")
        void should_save_upsertLatest_refreshOnline_publishEvent() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenReturn(null);
            when(pointDefinitionService.getPointDefinitionOrThrow(10L)).thenReturn(buildPoint(10L));
            when(devicePointBindingService.getActiveBindingOrThrow(1, 10L, CURRENT_USER_ID))
                    .thenReturn(activeBinding(1, 10L));
            when(metricSampleMapper.insert((MetricSample) any())).thenReturn(1);
            MetricLatest latestMock = new MetricLatest();
            latestMock.setDeviceId(1); latestMock.setPointId(10L);
            when(metricLatestService.upsertLatest(eq(1), eq(10L), eq(new BigDecimal("220.5")), any(LocalDateTime.class)))
                    .thenReturn(latestMock);
            when(deviceService.update(any())).thenReturn(true);

            MetricSampleDTO dto = new MetricSampleDTO();
            dto.setDeviceId(1);
            dto.setPointId(10L);
            dto.setSampleValue(new BigDecimal("220.5"));

            MetricSample saved = metricSampleService.saveSample(dto, CURRENT_USER_ID, true);
            assertThat(saved.getDeviceId()).isEqualTo(1);
            verify(metricSampleMapper, times(1)).insert((MetricSample) any());
            verify(metricLatestService, times(1))
                    .upsertLatest(eq(1), eq(10L), eq(new BigDecimal("220.5")), any(LocalDateTime.class));
            verify(deviceService, times(1)).update(any());
            verify(eventPublisher, times(1)).publishEvent(any(MetricSampleSavedEvent.class));
        }

        @Test
        @DisplayName("triggerAlarm=false（模拟写入）不发布评估事件")
        void should_not_publishEvent_whenTriggerAlarmFalse() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenReturn(null);
            when(pointDefinitionService.getPointDefinitionOrThrow(10L)).thenReturn(buildPoint(10L));
            when(devicePointBindingService.getActiveBindingOrThrow(1, 10L, CURRENT_USER_ID))
                    .thenReturn(activeBinding(1, 10L));
            when(metricSampleMapper.insert((MetricSample) any())).thenReturn(1);
            when(metricLatestService.upsertLatest(any(), any(), any(), any())).thenReturn(new MetricLatest());
            when(deviceService.update(any())).thenReturn(true);

            MetricSampleDTO dto = new MetricSampleDTO();
            dto.setDeviceId(1);
            dto.setPointId(10L);
            dto.setSampleValue(new BigDecimal("220.5"));

            metricSampleService.saveSample(dto, CURRENT_USER_ID, false);
            verify(eventPublisher, never()).publishEvent(any(MetricSampleSavedEvent.class));
        }

        @Test
        @DisplayName("未来采样时间超过5分钟容忍窗口应拒绝")
        void should_rejectFutureSampleTimeBeyondTolerance() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenReturn(null);
            when(pointDefinitionService.getPointDefinitionOrThrow(10L)).thenReturn(buildPoint(10L));
            when(devicePointBindingService.getActiveBindingOrThrow(1, 10L, CURRENT_USER_ID))
                    .thenReturn(activeBinding(1, 10L));

            MetricSampleDTO dto = new MetricSampleDTO();
            dto.setDeviceId(1);
            dto.setPointId(10L);
            dto.setSampleValue(new BigDecimal("220.5"));
            dto.setSampleTime(LocalDateTime.now().plusMinutes(6));

            assertThatThrownBy(() -> metricSampleService.saveSample(dto, CURRENT_USER_ID, true))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("采样时间不能晚于");
            verify(metricSampleMapper, never()).insert((MetricSample) any());
            verify(metricLatestService, never()).upsertLatest(any(), any(), any(), any());
        }

        @Test
        @DisplayName("未来采样时间在5分钟容忍窗口内可接受（设备时钟偏差）")
        void should_acceptFutureSampleTimeWithinTolerance() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenReturn(null);
            when(pointDefinitionService.getPointDefinitionOrThrow(10L)).thenReturn(buildPoint(10L));
            when(devicePointBindingService.getActiveBindingOrThrow(1, 10L, CURRENT_USER_ID))
                    .thenReturn(activeBinding(1, 10L));
            when(metricSampleMapper.insert((MetricSample) any())).thenReturn(1);
            when(metricLatestService.upsertLatest(any(), any(), any(), any())).thenReturn(new MetricLatest());
            when(deviceService.update(any())).thenReturn(true);

            MetricSampleDTO dto = new MetricSampleDTO();
            dto.setDeviceId(1);
            dto.setPointId(10L);
            dto.setSampleValue(new BigDecimal("220.5"));
            dto.setSampleTime(LocalDateTime.now().plusMinutes(1));

            MetricSample saved = metricSampleService.saveSample(dto, CURRENT_USER_ID, true);
            assertThat(saved.getDeviceId()).isEqualTo(1);
        }

        @Test
        @DisplayName("BOOLEAN 指标仅接受 0 或 1")
        void should_rejectInvalidBooleanValue() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenReturn(null);
            PointDefinition booleanPoint = buildPoint(10L);
            booleanPoint.setDataType("BOOLEAN");
            when(pointDefinitionService.getPointDefinitionOrThrow(10L)).thenReturn(booleanPoint);
            when(devicePointBindingService.getActiveBindingOrThrow(1, 10L, CURRENT_USER_ID))
                    .thenReturn(activeBinding(1, 10L));

            MetricSampleDTO dto = new MetricSampleDTO();
            dto.setDeviceId(1);
            dto.setPointId(10L);
            dto.setSampleValue(new BigDecimal("2"));

            assertThatThrownBy(() -> metricSampleService.saveSample(dto, CURRENT_USER_ID, true))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅允许 0 或 1");
            verify(metricSampleMapper, never()).insert((MetricSample) any());
        }
    }

    @Nested
    @DisplayName("saveBatch - 批量逐条独立写入")
    class SaveBatch {

        private void stubTransactionPassThrough() {
            // 逐条独立事务：直接执行回调（模拟每条独立提交）
            when(transactionTemplate.execute(any()))
                    .thenAnswer(invocation -> {
                        TransactionCallback<?> callback = invocation.getArgument(0);
                        return callback.doInTransaction(null);
                    });
        }

        @Test
        @DisplayName("批量全部成功返回 successCount 与空失败明细")
        void should_batchSaveAllSuccess() {
            stubTransactionPassThrough();
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID))).thenReturn(null);
            when(pointDefinitionService.getPointDefinitionOrThrow(10L)).thenReturn(buildPoint(10L));
            when(pointDefinitionService.getPointDefinitionOrThrow(20L)).thenReturn(buildPoint(20L));
            when(devicePointBindingService.getActiveBindingOrThrow(eq(1), eq(10L), eq(CURRENT_USER_ID)))
                    .thenReturn(activeBinding(1, 10L));
            when(devicePointBindingService.getActiveBindingOrThrow(eq(1), eq(20L), eq(CURRENT_USER_ID)))
                    .thenReturn(activeBinding(1, 20L));
            when(metricSampleMapper.insert((MetricSample) any())).thenReturn(1);
            when(metricLatestService.upsertLatest(any(), any(), any(), any())).thenReturn(new MetricLatest());
            when(deviceService.update(any())).thenReturn(true);

            MetricSampleDTO d1 = new MetricSampleDTO();
            d1.setDeviceId(1); d1.setPointId(10L); d1.setSampleValue(new BigDecimal("220.5"));
            MetricSampleDTO d2 = new MetricSampleDTO();
            d2.setDeviceId(1); d2.setPointId(20L); d2.setSampleValue(new BigDecimal("55.1"));

            MetricSampleBatchDTO batch = new MetricSampleBatchDTO();
            batch.setItems(List.of(d1, d2));

            MetricSampleBatchResultVO result = metricSampleService.saveBatch(batch, CURRENT_USER_ID, true);
            assertThat(result.getSuccessCount()).isEqualTo(2);
            assertThat(result.getFailures()).isEmpty();
            verify(metricSampleMapper, times(2)).insert((MetricSample) any());
            verify(eventPublisher, times(2)).publishEvent(any(MetricSampleSavedEvent.class));
        }

        @Test
        @DisplayName("批量部分失败不影响其余条目（D10 非原子）")
        void should_batchPartialFailureNotAffectOthers() {
            stubTransactionPassThrough();
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID))).thenReturn(null);
            // 第2条指标不存在 → 该条失败
            when(pointDefinitionService.getPointDefinitionOrThrow(10L)).thenReturn(buildPoint(10L));
            when(pointDefinitionService.getPointDefinitionOrThrow(20L))
                    .thenThrow(new BusinessException(4040, "指标不存在"));
            when(devicePointBindingService.getActiveBindingOrThrow(eq(1), eq(10L), eq(CURRENT_USER_ID)))
                    .thenReturn(activeBinding(1, 10L));
            when(metricSampleMapper.insert((MetricSample) any())).thenReturn(1);
            when(metricLatestService.upsertLatest(any(), any(), any(), any())).thenReturn(new MetricLatest());
            when(deviceService.update(any())).thenReturn(true);

            MetricSampleDTO d1 = new MetricSampleDTO();
            d1.setDeviceId(1); d1.setPointId(10L); d1.setSampleValue(new BigDecimal("220.5"));
            MetricSampleDTO d2 = new MetricSampleDTO();
            d2.setDeviceId(1); d2.setPointId(20L); d2.setSampleValue(new BigDecimal("55.1"));

            MetricSampleBatchDTO batch = new MetricSampleBatchDTO();
            batch.setItems(List.of(d1, d2));

            MetricSampleBatchResultVO result = metricSampleService.saveBatch(batch, CURRENT_USER_ID, true);
            assertThat(result.getSuccessCount()).isEqualTo(1);
            assertThat(result.getFailures()).hasSize(1);
            assertThat(result.getFailures().get(0).getIndex()).isEqualTo(1);
            assertThat(result.getFailures().get(0).getErrorCode()).isEqualTo(4040);
            verify(metricSampleMapper, times(1)).insert((MetricSample) any());
            verify(eventPublisher, times(1)).publishEvent(any(MetricSampleSavedEvent.class));
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

    @Nested
    @DisplayName("trendSamples - 趋势分桶聚合查询")
    class TrendSamples {

        @Test
        @DisplayName("未指定 pointId 应拒绝")
        void should_throw_when_noPointId() {
            assertThatThrownBy(() -> metricSampleService.trendSamples(
                    CURRENT_USER_ID, 1, null, LocalDateTime.now().minusHours(1), LocalDateTime.now()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("必须指定 pointId");
        }

        @Test
        @DisplayName("未指定时间范围应拒绝")
        void should_throw_when_noTimeRange() {
            assertThatThrownBy(() -> metricSampleService.trendSamples(
                    CURRENT_USER_ID, 1, 10L, null, LocalDateTime.now()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("必须指定时间范围");
        }

        @Test
        @DisplayName("startTime>endTime 应拒绝")
        void should_throw_when_startAfterEnd() {
            assertThatThrownBy(() -> metricSampleService.trendSamples(
                    CURRENT_USER_ID, 1, 10L, LocalDateTime.now(), LocalDateTime.now().minusHours(1)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("起始时间不能晚于结束时间");
        }

        @Test
        @DisplayName("时间跨度超过 90 天应拒绝")
        void should_throw_when_timeRangeExceed90Days() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID))).thenReturn(null);

            assertThatThrownBy(() -> metricSampleService.trendSamples(
                    CURRENT_USER_ID, 1, 10L, LocalDateTime.now().minusDays(100), LocalDateTime.now()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("时间跨度不能超过");
        }

        @Test
        @DisplayName("设备越权时拒绝查询")
        void should_throw_when_deviceAccessDenied() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenThrow(new DataScopeViolationException("Device", "1"));

            assertThatThrownBy(() -> metricSampleService.trendSamples(
                    CURRENT_USER_ID, 1, 10L, LocalDateTime.now().minusHours(1), LocalDateTime.now()))
                    .isInstanceOf(DataScopeViolationException.class);
            verify(metricSampleMapper, never()).selectTrendBuckets(any(), any(), any(), any(), anyLong());
        }

        @Test
        @DisplayName("24 小时范围查询按 10 分钟桶聚合并返回升序结果")
        void should_aggregateWithBucket_and_returnAsc() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID))).thenReturn(null);
            MetricSampleTrendPointVO bucket = new MetricSampleTrendPointVO();
            bucket.setTime(LocalDateTime.now().minusHours(2));
            bucket.setAvgValue(new BigDecimal("24.35"));
            bucket.setMinValue(new BigDecimal("23.80"));
            bucket.setMaxValue(new BigDecimal("24.90"));
            when(metricSampleMapper.selectTrendBuckets(eq(1), eq(10L), any(LocalDateTime.class),
                    any(LocalDateTime.class), eq(600L))).thenReturn(List.of(bucket));

            List<MetricSampleTrendPointVO> result = metricSampleService.trendSamples(
                    CURRENT_USER_ID, 1, 10L, LocalDateTime.now().minusHours(24), LocalDateTime.now());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAvgValue()).isEqualByComparingTo("24.35");
            verify(metricSampleMapper, times(1)).selectTrendBuckets(eq(1), eq(10L),
                    any(LocalDateTime.class), any(LocalDateTime.class), eq(600L));
        }

        @Test
        @DisplayName("空数据范围返回空列表（不报错）")
        void should_returnEmptyList_whenNoData() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID))).thenReturn(null);
            when(metricSampleMapper.selectTrendBuckets(eq(1), eq(10L), any(LocalDateTime.class),
                    any(LocalDateTime.class), eq(60L))).thenReturn(List.of());

            List<MetricSampleTrendPointVO> result = metricSampleService.trendSamples(
                    CURRENT_USER_ID, 1, 10L, LocalDateTime.now().minusHours(1), LocalDateTime.now());

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("trendSamples - S8-4 跨度路由")
    class TrendRouting {

        @Test
        @DisplayName("≤48h 走原始采样表")
        void should_routeToRaw_whenWithin48h() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID))).thenReturn(null);
            when(metricSampleMapper.selectTrendBuckets(eq(1), eq(10L), any(LocalDateTime.class),
                    any(LocalDateTime.class), anyLong())).thenReturn(List.of());

            metricSampleService.trendSamples(CURRENT_USER_ID, 1, 10L,
                    LocalDateTime.now().minusHours(47), LocalDateTime.now());

            verify(metricSampleHourlyMapper, never()).selectTrendBucketsFromHourly(
                    any(), any(), any(), any(), anyLong());
        }

        @Test
        @DisplayName(">48h 走小时聚合表")
        void should_routeToHourly_whenBeyond48h() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID))).thenReturn(null);
            when(metricSampleHourlyMapper.selectTrendBucketsFromHourly(eq(1), eq(10L),
                    any(LocalDateTime.class), any(LocalDateTime.class), anyLong()))
                    .thenReturn(List.of());

            metricSampleService.trendSamples(CURRENT_USER_ID, 1, 10L,
                    LocalDateTime.now().minusHours(72), LocalDateTime.now());

            verify(metricSampleHourlyMapper).selectTrendBucketsFromHourly(
                    eq(1), eq(10L), any(LocalDateTime.class), any(LocalDateTime.class), anyLong());
            verify(metricSampleMapper, never()).selectTrendBuckets(
                    any(), any(), any(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("resolveBucketSeconds - 桶宽取整")
    class ResolveBucketSeconds {

        @Test
        @DisplayName("1小时→60s、24小时→600s、7天→3600s、30天→14400s")
        void should_roundUpToNiceSteps() {
            assertThat(MetricSampleServiceImpl.resolveBucketSeconds(3600)).isEqualTo(60L);
            assertThat(MetricSampleServiceImpl.resolveBucketSeconds(86400)).isEqualTo(600L);
            assertThat(MetricSampleServiceImpl.resolveBucketSeconds(604800)).isEqualTo(3600L);
            assertThat(MetricSampleServiceImpl.resolveBucketSeconds(2592000)).isEqualTo(14400L);
        }

        @Test
        @DisplayName("极小范围取最小步长 60s，超大范围用原始值兜底")
        void should_floorAt60s_and_fallbackRawBeyondSteps() {
            assertThat(MetricSampleServiceImpl.resolveBucketSeconds(10)).isEqualTo(60L);
            // 100 天：7776000/200=38880 未超步长表上界 86400 → 43200
            assertThat(MetricSampleServiceImpl.resolveBucketSeconds(7776000)).isEqualTo(43200L);
        }
    }
}
