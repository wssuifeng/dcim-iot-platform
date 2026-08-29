package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.DeviceTypePointTemplateBatchDTO;
import com.example.platform.dto.DeviceTypePointTemplateDTO;
import com.example.platform.entity.DeviceType;
import com.example.platform.entity.DeviceTypePointTemplate;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.DeviceTypeMapper;
import com.example.platform.mapper.DeviceTypePointTemplateMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:DeviceTypePointTemplateServiceImpl 单元测试
 * 覆盖 BIZ-OPEN-02：模板覆盖写、指标存在性校验、重复 pointId 拒绝、清空旧模板
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceTypePointTemplateService 单元测试")
class DeviceTypePointTemplateServiceImplTest {

    @Mock
    private DeviceTypePointTemplateMapper deviceTypePointTemplateMapper;
    @Mock
    private DeviceTypeMapper deviceTypeMapper;
    @Mock
    private PointDefinitionService pointDefinitionService;

    @InjectMocks
    private DeviceTypePointTemplateServiceImpl deviceTypePointTemplateService;

    @BeforeEach
    void setUp() throws Exception {
        initBaseMapper();
        initLambdaCache();
    }

    private void initBaseMapper() throws Exception {
        Class<?> clazz = DeviceTypePointTemplateServiceImpl.class;
        Field field = null;
        while (clazz != null) {
            try { field = clazz.getDeclaredField("baseMapper"); break; }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        assertThat(field).as("应存在 baseMapper 字段").isNotNull();
        field.setAccessible(true);
        field.set(deviceTypePointTemplateService, deviceTypePointTemplateMapper);
    }

    private void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(DeviceTypePointTemplateMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, DeviceTypePointTemplate.class);
    }

    private DeviceType buildType(Integer id) {
        DeviceType t = new DeviceType();
        t.setId(id);
        t.setTypeCode("UPS");
        return t;
    }

    @Nested
    @DisplayName("listTemplates - 查询模板")
    class ListTemplates {

        @Test
        @DisplayName("类型不存在应抛 BusinessException")
        void should_throw_when_typeNotFound() {
            when(deviceTypeMapper.selectById(99)).thenReturn(null);

            assertThatThrownBy(() -> deviceTypePointTemplateService.listTemplates(99))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("正常返回模板列表（按 sortOrder 升序）")
        void should_returnTemplates() {
            when(deviceTypeMapper.selectById(1)).thenReturn(buildType(1));
            DeviceTypePointTemplate t1 = new DeviceTypePointTemplate();
            t1.setTypeId(1); t1.setPointId(10L); t1.setSortOrder(0);
            when(deviceTypePointTemplateMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(t1));

            List<DeviceTypePointTemplate> result = deviceTypePointTemplateService.listTemplates(1);
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("saveTemplates - 覆盖写模板")
    class SaveTemplates {

        @Test
        @DisplayName("指标不存在应整批拒绝")
        void should_throw_when_pointNotFound() {
            when(deviceTypeMapper.selectById(1)).thenReturn(buildType(1));
            when(pointDefinitionService.getPointDefinitionOrThrow(10L))
                    .thenThrow(new BusinessException("指标不存在"));

            DeviceTypePointTemplateBatchDTO batch = new DeviceTypePointTemplateBatchDTO();
            DeviceTypePointTemplateDTO item = new DeviceTypePointTemplateDTO();
            item.setPointId(10L);
            item.setIsRequired(1);
            item.setSortOrder(0);
            batch.setItems(List.of(item));

            assertThatThrownBy(() -> deviceTypePointTemplateService.saveTemplates(1, batch))
                    .isInstanceOf(BusinessException.class);
            verify(deviceTypePointTemplateMapper, never()).insert((DeviceTypePointTemplate) any());
        }

        @Test
        @DisplayName("批量中同一 pointId 重复应拒绝")
        void should_throw_when_duplicatePointInBatch() {
            when(deviceTypeMapper.selectById(1)).thenReturn(buildType(1));
            PointDefinition point = new PointDefinition();
            point.setId(10L);
            when(pointDefinitionService.getPointDefinitionOrThrow(10L)).thenReturn(point);

            DeviceTypePointTemplateBatchDTO batch = new DeviceTypePointTemplateBatchDTO();
            DeviceTypePointTemplateDTO item1 = new DeviceTypePointTemplateDTO();
            item1.setPointId(10L);
            DeviceTypePointTemplateDTO item2 = new DeviceTypePointTemplateDTO();
            item2.setPointId(10L);
            batch.setItems(List.of(item1, item2));

            assertThatThrownBy(() -> deviceTypePointTemplateService.saveTemplates(1, batch))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("指标定义ID重复");
            verify(deviceTypePointTemplateMapper, never()).insert((DeviceTypePointTemplate) any());
        }

        @Test
        @DisplayName("正常覆盖写：先清空再批量插入")
        void should_overwriteTemplates() {
            when(deviceTypeMapper.selectById(1)).thenReturn(buildType(1));
            PointDefinition p1 = new PointDefinition(); p1.setId(10L);
            PointDefinition p2 = new PointDefinition(); p2.setId(20L);
            when(pointDefinitionService.getPointDefinitionOrThrow(10L)).thenReturn(p1);
            when(pointDefinitionService.getPointDefinitionOrThrow(20L)).thenReturn(p2);
            when(deviceTypePointTemplateMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
            when(deviceTypePointTemplateMapper.insert((DeviceTypePointTemplate) any())).thenReturn(1);

            DeviceTypePointTemplateBatchDTO batch = new DeviceTypePointTemplateBatchDTO();
            DeviceTypePointTemplateDTO item1 = new DeviceTypePointTemplateDTO();
            item1.setPointId(10L);
            item1.setIsRequired(1);
            item1.setSortOrder(0);
            DeviceTypePointTemplateDTO item2 = new DeviceTypePointTemplateDTO();
            item2.setPointId(20L);
            item2.setIsRequired(0);
            item2.setSortOrder(1);
            batch.setItems(List.of(item1, item2));

            List<DeviceTypePointTemplate> result = deviceTypePointTemplateService.saveTemplates(1, batch);

            assertThat(result).hasSize(2);
            verify(deviceTypePointTemplateMapper, times(1)).delete(any(LambdaQueryWrapper.class));
            verify(deviceTypePointTemplateMapper, times(2)).insert((DeviceTypePointTemplate) any());
        }
    }
}
