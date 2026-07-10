package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.MetricLatestVO;
import com.example.platform.entity.MetricLatest;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.MetricLatestMapper;
import com.example.platform.service.DeviceScopeHelper;
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
 * 类功能:MetricLatestServiceImpl 单元测试
 * 覆盖 BIZ-OPEN-02：最新值 upsert（旧时间跳过、新时间更新、插入）、范围查询
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("MetricLatestService 单元测试")
class MetricLatestServiceImplTest {

    @Mock
    private MetricLatestMapper metricLatestMapper;
    @Mock
    private DeviceScopeHelper deviceScopeHelper;
    @Mock
    private PointDefinitionService pointDefinitionService;

    @InjectMocks
    private MetricLatestServiceImpl metricLatestService;

    private static final Long CURRENT_USER_ID = 20L;

    @BeforeEach
    void setUp() throws Exception {
        initBaseMapper();
        initLambdaCache();
    }

    private void initBaseMapper() throws Exception {
        Class<?> clazz = MetricLatestServiceImpl.class;
        Field field = null;
        while (clazz != null) {
            try { field = clazz.getDeclaredField("baseMapper"); break; }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        assertThat(field).as("应存在 baseMapper 字段").isNotNull();
        field.setAccessible(true);
        field.set(metricLatestService, metricLatestMapper);
    }

    private void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(MetricLatestMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, MetricLatest.class);
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
    @DisplayName("upsertLatest - 写入最新值")
    class Upsert {

        @Test
        @DisplayName("无最新值记录时应插入")
        void should_insert_when_notExist() {
            when(metricLatestMapper.selectOne(any(LambdaQueryWrapper.class),
                    eq(true))).thenReturn(null);
            when(metricLatestMapper.insert((MetricLatest) any())).thenReturn(1);

            MetricLatest result = metricLatestService.upsertLatest(
                    1, 10L, new BigDecimal("220.5"), LocalDateTime.now());

            assertThat(result.getLatestValue()).isEqualByComparingTo("220.5");
            verify(metricLatestMapper, times(1)).insert((MetricLatest) any());
            verify(metricLatestMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("更早采样时间应跳过（保留较新快照）")
        void should_skip_when_sampleTimeBeforeExisting() {
            MetricLatest existing = new MetricLatest();
            existing.setId(50L);
            existing.setLatestValue(new BigDecimal("221.0"));
            existing.setLatestTime(LocalDateTime.now());
            when(metricLatestMapper.selectOne(any(LambdaQueryWrapper.class),
                    eq(true))).thenReturn(existing);

            MetricLatest result = metricLatestService.upsertLatest(
                    1, 10L, new BigDecimal("220.5"),
                    LocalDateTime.now().minusMinutes(5) // 5分钟前
            );

            assertThat(result.getLatestValue()).isEqualByComparingTo("221.0");
            verify(metricLatestMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
        }

        @Test
        @DisplayName("更新时间戳后的新值应通过 update 刷新")
        void should_update_when_newSample() {
            MetricLatest existing = new MetricLatest();
            existing.setId(50L);
            existing.setLatestValue(new BigDecimal("221.0"));
            existing.setLatestTime(LocalDateTime.now().minusMinutes(10));
            when(metricLatestMapper.selectOne(any(LambdaQueryWrapper.class),
                    eq(true))).thenReturn(existing);
            when(metricLatestMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

            MetricLatest result = metricLatestService.upsertLatest(
                    1, 10L, new BigDecimal("225.0"), LocalDateTime.now());

            assertThat(result.getLatestValue()).isEqualByComparingTo("225.0");
            verify(metricLatestMapper, times(1))
                    .update(any(), any(LambdaUpdateWrapper.class));
        }
    }

    @Nested
    @DisplayName("listByDevice - 查询某设备最新值")
    class ListByDevice {

        @Test
        @DisplayName("设备范围越权拒绝")
        void should_throw_when_deviceAccessDenied() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenThrow(new DataScopeViolationException("Device", "1"));

            assertThatThrownBy(() -> metricLatestService.listByDevice(1, CURRENT_USER_ID))
                    .isInstanceOf(DataScopeViolationException.class);
            verify(metricLatestMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("正常返回 VO 列表（带点位名称/单位）")
        void should_returnVOList() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID))).thenReturn(null);

            MetricLatest latest = new MetricLatest();
            latest.setId(1L); latest.setDeviceId(1); latest.setPointId(10L);
            latest.setLatestValue(new BigDecimal("220.5"));
            latest.setLatestTime(LocalDateTime.now());
            when(metricLatestMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(latest));
            when(pointDefinitionService.getById(10L)).thenReturn(buildPoint(10L));

            List<MetricLatestVO> result = metricLatestService.listByDevice(1, CURRENT_USER_ID);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPointCode()).isEqualTo("voltage_a");
            assertThat(result.get(0).getUnit()).isEqualTo("V");
        }
    }

    @Nested
    @DisplayName("pageLatest - 分页查询最新值")
    class PageLatest {

        @Test
        @DisplayName("未指定 deviceId 应拒绝")
        void should_throw_when_noDeviceId() {
            assertThatThrownBy(() -> metricLatestService.pageLatest(
                    CURRENT_USER_ID, 1, 10, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("必须指定 deviceId");
        }

        @Test
        @DisplayName("设备越权拒绝")
        void should_throw_when_deviceAccessDenied() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenThrow(new DataScopeViolationException("Device", "1"));

            assertThatThrownBy(() -> metricLatestService.pageLatest(
                    CURRENT_USER_ID, 1, 10, 1, null))
                    .isInstanceOf(DataScopeViolationException.class);
        }
    }
}