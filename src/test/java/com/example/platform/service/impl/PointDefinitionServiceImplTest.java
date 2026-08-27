package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.PointDefinitionCreateDTO;
import com.example.platform.dto.PointDefinitionUpdateDTO;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.DevicePointAlarmRuleMapper;
import com.example.platform.mapper.DevicePointBindingMapper;
import com.example.platform.mapper.DeviceTypeMapper;
import com.example.platform.mapper.DeviceTypePointTemplateMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:PointDefinitionServiceImpl 单元测试
 * 覆盖：编码全局唯一、数据类型校验、删除引用保护
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("PointDefinitionService 单元测试")
class PointDefinitionServiceImplTest {

    @Mock
    private PointDefinitionMapper pointDefinitionMapper;
    @Mock
    private DeviceTypeMapper deviceTypeMapper;
    @Mock
    private DeviceTypePointTemplateMapper templateMapper;
    @Mock
    private DevicePointBindingMapper bindingMapper;
    @Mock
    private DevicePointAlarmRuleMapper alarmRuleMapper;

    @InjectMocks
    private PointDefinitionServiceImpl pointDefinitionService;

    private static final Long USER_ID = 20L;

    @BeforeEach
    void setUp() throws Exception {
        initBaseMapper();
        initLambdaCache();
        lenient().when(pointDefinitionMapper.insert(any(PointDefinition.class))).thenReturn(1);
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

    private PointDefinition buildPoint(Long id, String code) {
        PointDefinition p = new PointDefinition();
        p.setId(id);
        p.setPointCode(code);
        p.setPointName("A相电压");
        p.setUnit("V");
        p.setDataType("NUMBER");
        return p;
    }

    private PointDefinitionCreateDTO createDTO(String code) {
        PointDefinitionCreateDTO dto = new PointDefinitionCreateDTO();
        dto.setPointCode(code);
        dto.setPointName("A相电压");
        dto.setDataType("NUMBER");
        return dto;
    }

    @Nested
    @DisplayName("createPointDefinition - 新增点位")
    class Create {

        @Test
        @DisplayName("编码全局唯一冲突拒绝（4034）")
        void should_throw_when_codeExists() {
            when(pointDefinitionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThatThrownBy(() -> pointDefinitionService.createPointDefinition(createDTO("voltage_a"), USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(4034);
        }

        @Test
        @DisplayName("非法数据类型拒绝")
        void should_throw_when_dataTypeInvalid() {
            when(pointDefinitionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            PointDefinitionCreateDTO dto = createDTO("voltage_b");
            dto.setDataType("COMPLEX");

            assertThatThrownBy(() -> pointDefinitionService.createPointDefinition(dto, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("数据类型");
        }

        @Test
        @DisplayName("编码不冲突时创建成功")
        void should_create_when_codeUnique() {
            when(pointDefinitionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            PointDefinition created = pointDefinitionService.createPointDefinition(
                    createDTO("voltage_b"), USER_ID);

            assertThat(created.getPointCode()).isEqualTo("voltage_b");
            assertThat(created.getDataType()).isEqualTo("NUMBER");
        }
    }

    @Nested
    @DisplayName("updatePointDefinition - 更新")
    class Update {

        @Test
        @DisplayName("正常更新点位字段")
        void should_update_fields() {
            when(pointDefinitionMapper.selectById(5L)).thenReturn(buildPoint(5L, "temperature"));
            when(pointDefinitionMapper.updateById(any(PointDefinition.class))).thenReturn(1);

            PointDefinitionUpdateDTO dto = new PointDefinitionUpdateDTO();
            dto.setPointName("温度");
            dto.setUnit("℃");

            PointDefinition updated = pointDefinitionService.updatePointDefinition(5L, dto, USER_ID);

            assertThat(updated.getPointName()).isEqualTo("温度");
            assertThat(updated.getUnit()).isEqualTo("℃");
        }
    }

    @Nested
    @DisplayName("deletePointDefinition - 删除保护")
    class Delete {

        @Test
        @DisplayName("被告警规则引用的点位拒绝删除（4035）")
        void should_reject_whenReferencedByRule() {
            when(pointDefinitionMapper.selectById(5L)).thenReturn(buildPoint(5L, "temperature"));
            lenient().when(templateMapper.selectCount(any())).thenReturn(0L);
            lenient().when(bindingMapper.selectCount(any())).thenReturn(0L);
            when(alarmRuleMapper.selectCount(any())).thenReturn(2L);

            assertThatThrownBy(() -> pointDefinitionService.deletePointDefinition(5L, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(4035);
            verify(pointDefinitionMapper, never()).deleteById(5L);
        }
    }
}
