package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.MetricRetentionPolicyDTO;
import com.example.platform.entity.MetricRetentionPolicy;
import com.example.platform.mapper.MetricRetentionPolicyMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:MetricRetentionPolicyServiceImpl 单元测试
 * 覆盖 BIZ-OPEN-02：90 天上限、点位策略优先于全局、全局回退、upsert
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("MetricRetentionPolicyService 单元测试")
class MetricRetentionPolicyServiceImplTest {

    @Mock
    private MetricRetentionPolicyMapper metricRetentionPolicyMapper;

    @InjectMocks
    private MetricRetentionPolicyServiceImpl metricRetentionPolicyService;

    @BeforeEach
    void setUp() throws Exception {
        initBaseMapper();
        initLambdaCache();
    }

    private void initBaseMapper() throws Exception {
        Class<?> clazz = MetricRetentionPolicyServiceImpl.class;
        Field field = null;
        while (clazz != null) {
            try { field = clazz.getDeclaredField("baseMapper"); break; }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        assertThat(field).as("应存在 baseMapper 字段").isNotNull();
        field.setAccessible(true);
        field.set(metricRetentionPolicyService, metricRetentionPolicyMapper);
    }

    private void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(MetricRetentionPolicyMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, MetricRetentionPolicy.class);
    }

    @Nested
    @DisplayName("upsertPolicy - 保留策略变更")
    class UpsertPolicy {

        @Test
        @DisplayName("retentionDays > 90 应拒绝")
        void should_throw_whenRetentionDaysTooLarge() {
            MetricRetentionPolicyDTO dto = new MetricRetentionPolicyDTO();
            dto.setRetentionDays(120);

            assertThatThrownBy(() -> metricRetentionPolicyService.upsertPolicy(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("1~");
            verify(metricRetentionPolicyMapper, never()).insert((MetricRetentionPolicy) any());
            verify(metricRetentionPolicyMapper, never()).updateById((MetricRetentionPolicy) any());
        }

        @Test
        @DisplayName("retentionDays < 1 应拒绝")
        void should_throw_whenRetentionDaysTooSmall() {
            MetricRetentionPolicyDTO dto = new MetricRetentionPolicyDTO();
            dto.setRetentionDays(0);

            assertThatThrownBy(() -> metricRetentionPolicyService.upsertPolicy(dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("全局策略存在时应更新而非新增")
        void should_updateGlobal() {
            MetricRetentionPolicy existing = new MetricRetentionPolicy();
            existing.setId(1L);
            existing.setPointId(null);
            existing.setRetentionDays(30);
            when(metricRetentionPolicyMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                    .thenReturn(existing);
            when(metricRetentionPolicyMapper.updateById(any(MetricRetentionPolicy.class))).thenReturn(1);

            MetricRetentionPolicyDTO dto = new MetricRetentionPolicyDTO();
            dto.setPointId(null);
            dto.setRetentionDays(60);

            MetricRetentionPolicy result = metricRetentionPolicyService.upsertPolicy(dto);
            assertThat(result.getRetentionDays()).isEqualTo(60);
            verify(metricRetentionPolicyMapper, never()).insert((MetricRetentionPolicy) any());
        }

        @Test
        @DisplayName("点位策略不存在时新建")
        void should_createPointPolicy() {
            when(metricRetentionPolicyMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                    .thenReturn(null);
            when(metricRetentionPolicyMapper.insert((MetricRetentionPolicy) any())).thenReturn(1);

            MetricRetentionPolicyDTO dto = new MetricRetentionPolicyDTO();
            dto.setPointId(10L);
            dto.setRetentionDays(90);

            MetricRetentionPolicy result = metricRetentionPolicyService.upsertPolicy(dto);
            assertThat(result.getPointId()).isEqualTo(10L);
            assertThat(result.getRetentionDays()).isEqualTo(90);
            verify(metricRetentionPolicyMapper, times(1)).insert((MetricRetentionPolicy) any());
        }
    }

    @Nested
    @DisplayName("resolveEffectiveRetentionDays - 解析有效保留天数")
    class Resolve {

        @Test
        @DisplayName("点位策略优先")
        void should_preferPointPolicy() {
            MetricRetentionPolicy pointPolicy = new MetricRetentionPolicy();
            pointPolicy.setPointId(10L);
            pointPolicy.setRetentionDays(30);
            when(metricRetentionPolicyMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                    .thenReturn(pointPolicy);

            int days = metricRetentionPolicyService.resolveEffectiveRetentionDays(10L);
            assertThat(days).isEqualTo(30);
        }

        @Test
        @DisplayName("无点策略时回退全局策略")
        void should_fallbackToGlobal() {
            MetricRetentionPolicy global = new MetricRetentionPolicy();
            global.setPointId(null);
            global.setRetentionDays(45);
            when(metricRetentionPolicyMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                    .thenReturn(null, global);

            int days = metricRetentionPolicyService.resolveEffectiveRetentionDays(10L);
            assertThat(days).isEqualTo(45);
        }

        @Test
        @DisplayName("全局策略超出上限90天时仍按90天返回")
        void should_capAt90EvenIfDbLarger() {
            MetricRetentionPolicy global = new MetricRetentionPolicy();
            global.setRetentionDays(120); // 直接更新过的不规范数据
            when(metricRetentionPolicyMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                    .thenReturn(null, global);

            int days = metricRetentionPolicyService.resolveEffectiveRetentionDays(10L);
            assertThat(days).isEqualTo(90);
        }

        @Test
        @DisplayName("无任何策略时回退到默认90天")
        void should_fallbackToDefault() {
            when(metricRetentionPolicyMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                    .thenReturn((MetricRetentionPolicy) null, (MetricRetentionPolicy) null);

            int days = metricRetentionPolicyService.resolveEffectiveRetentionDays(10L);
            assertThat(days).isEqualTo(90);
        }
    }

    @Nested
    @DisplayName("getGlobalPolicyOrThrow / listAllPolicies")
    class GetAndList {

        @Test
        @DisplayName("尚未配置全局策略时抛出 BusinessException")
        void should_throw_whenGlobalPolicyNotConfigured() {
            when(metricRetentionPolicyMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                    .thenReturn(null);

            assertThatThrownBy(() -> metricRetentionPolicyService.getGlobalPolicyOrThrow())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("尚未配置全局");
        }

        @Test
        @DisplayName("listAllPolicies 应返回全部策略")
        void should_returnAll() {
            MetricRetentionPolicy p = new MetricRetentionPolicy();
            p.setRetentionDays(45);
            when(metricRetentionPolicyMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(p));

            List<MetricRetentionPolicy> result = metricRetentionPolicyService.listAllPolicies();
            assertThat(result).hasSize(1);
        }
    }
}
