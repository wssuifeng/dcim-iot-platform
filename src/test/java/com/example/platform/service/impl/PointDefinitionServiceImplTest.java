package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.PointDefinitionCreateDTO;
import com.example.platform.dto.PointDefinitionUpdateDTO;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.PointDefinitionMapper;
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
 * 类功能:PointDefinitionServiceImpl 单元测试
 * 覆盖 BIZ-OPEN-02：点位定义 CRUD、编码唯一性、数据类型校验、分页关键字查询
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("PointDefinitionService 单元测试")
class PointDefinitionServiceImplTest {

    @Mock
    private PointDefinitionMapper pointDefinitionMapper;

    @InjectMocks
    private PointDefinitionServiceImpl pointDefinitionService;

    @BeforeEach
    void setUp() throws Exception {
        initBaseMapper();
        initLambdaCache();
    }

    private void initBaseMapper() throws Exception {
        Class<?> clazz = PointDefinitionServiceImpl.class;
        Field field = null;
        while (clazz != null) {
            try { field = clazz.getDeclaredField("baseMapper"); break; }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        assertThat(field).as("应存在 baseMapper 字段").isNotNull();
        field.setAccessible(true);
        field.set(pointDefinitionService, pointDefinitionMapper);
    }

    private void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(PointDefinitionMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, PointDefinition.class);
    }

    private PointDefinition buildPoint(Long id, String code, String name, String unit, String dataType) {
        PointDefinition p = new PointDefinition();
        p.setId(id);
        p.setPointCode(code);
        p.setPointName(name);
        p.setUnit(unit);
        p.setDataType(dataType);
        return p;
    }

    @Nested
    @DisplayName("createPointDefinition - 新增点位")
    class Create {

        @Test
        @DisplayName("编码冲突应拒绝")
        void should_throw_when_codeAlreadyExists() {
            when(pointDefinitionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            PointDefinitionCreateDTO dto = new PointDefinitionCreateDTO();
            dto.setPointCode("voltage_a");
            dto.setPointName("A相电压");
            dto.setDataType("NUMBER");

            assertThatThrownBy(() -> pointDefinitionService.createPointDefinition(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("点位编码已存在");
            verify(pointDefinitionMapper, never()).insert((PointDefinition) any());
        }

        @Test
        @DisplayName("正常新增：dataType 为空时默认为 NUMBER")
        void should_insert_withDefaultDataType() {
            when(pointDefinitionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(pointDefinitionMapper.insert((PointDefinition) any())).thenReturn(1);

            PointDefinitionCreateDTO dto = new PointDefinitionCreateDTO();
            dto.setPointCode("humidity");
            dto.setPointName("湿度");
            dto.setUnit("%");
            // 未设置 dataType

            PointDefinition saved = pointDefinitionService.createPointDefinition(dto);
            assertThat(saved.getPointCode()).isEqualTo("humidity");
            assertThat(saved.getDataType()).isEqualTo("NUMBER");
            verify(pointDefinitionMapper, times(1)).insert((PointDefinition) any());
        }
    }

    @Nested
    @DisplayName("updatePointDefinition - 更新点位")
    class Update {

        @Test
        @DisplayName("点位不存在应抛 BusinessException")
        void should_throw_when_notExists() {
            when(pointDefinitionMapper.selectById(99L)).thenReturn(null);

            PointDefinitionUpdateDTO dto = new PointDefinitionUpdateDTO();
            dto.setPointName("新名");

            assertThatThrownBy(() -> pointDefinitionService.updatePointDefinition(99L, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("点位定义不存在");
        }

        @Test
        @DisplayName("非法 dataType 应拒绝")
        void should_throw_when_invalidDataType() {
            PointDefinition existing = buildPoint(1L, "voltage_a", "A相电压", "V", "NUMBER");
            when(pointDefinitionMapper.selectById(1L)).thenReturn(existing);

            PointDefinitionUpdateDTO dto = new PointDefinitionUpdateDTO();
            dto.setDataType("UNKNOWN");

            assertThatThrownBy(() -> pointDefinitionService.updatePointDefinition(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("数据类型仅允许");
            verify(pointDefinitionMapper, never()).updateById((PointDefinition) any());
        }

        @Test
        @DisplayName("正常更新 name/unit/description/dataType")
        void should_updateFields() {
            PointDefinition existing = buildPoint(1L, "voltage_a", "A相电压", "V", "NUMBER");
            when(pointDefinitionMapper.selectById(1L)).thenReturn(existing);
            when(pointDefinitionMapper.updateById(any(PointDefinition.class))).thenReturn(1);

            PointDefinitionUpdateDTO dto = new PointDefinitionUpdateDTO();
            dto.setPointName("相电压");
            dto.setUnit("mV");
            dto.setDataType("NUMBER");
            dto.setDescription("UPS输出电压");

            PointDefinition updated = pointDefinitionService.updatePointDefinition(1L, dto);
            assertThat(updated.getPointCode()).isEqualTo("voltage_a"); // 编码不可改
            assertThat(updated.getUnit()).isEqualTo("mV");
            assertThat(updated.getDescription()).isEqualTo("UPS输出电压");
        }
    }

    @Nested
    @DisplayName("getPointDefinitionOrThrow - 获取点位")
    class GetOrThrow {

        @Test
        @DisplayName("不存在应抛 BusinessException")
        void should_throw_when_notExists() {
            when(pointDefinitionMapper.selectById(99L)).thenReturn(null);
            assertThatThrownBy(() -> pointDefinitionService.getPointDefinitionOrThrow(99L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("正常返回点位")
        void should_return() {
            PointDefinition existing = buildPoint(1L, "voltage_a", "A相电压", "V", "NUMBER");
            when(pointDefinitionMapper.selectById(1L)).thenReturn(existing);
            PointDefinition result = pointDefinitionService.getPointDefinitionOrThrow(1L);
            assertThat(result.getPointCode()).isEqualTo("voltage_a");
        }
    }

    @Nested
    @DisplayName("pagePointDefinitions - 分页查询")
    class PageDefs {

        @Test
        @DisplayName("关键字与 dataType 同时生效")
        void should_applyFilters() {
            Page<PointDefinition> mockPage = new Page<>(1, 10);
            mockPage.setRecords(List.of(buildPoint(1L, "voltage_a", "A相电压", "V", "NUMBER")));
            when(pointDefinitionMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            Page<PointDefinition> result =
                    pointDefinitionService.pagePointDefinitions(1, 10, "volt", "NUMBER");
            assertThat(result.getRecords()).hasSize(1);
            verify(pointDefinitionMapper, times(1))
                    .selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }
    }
}