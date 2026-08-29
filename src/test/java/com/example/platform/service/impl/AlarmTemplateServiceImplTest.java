package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.AlarmTemplateItemCreateDTO;
import com.example.platform.dto.AlarmTemplateItemUpdateDTO;
import com.example.platform.dto.AlarmTemplateSaveDTO;
import com.example.platform.dto.AlarmTemplateVO;
import com.example.platform.dto.PointAlarmRuleVO;
import com.example.platform.entity.AlarmTemplate;
import com.example.platform.entity.AlarmTemplateItem;
import com.example.platform.entity.Device;
import com.example.platform.entity.DeviceType;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.AlarmTemplateItemMapper;
import com.example.platform.mapper.AlarmTemplateMapper;
import com.example.platform.mapper.DeviceMapper;
import com.example.platform.mapper.DeviceTypeMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/*
 * 类功能:AlarmTemplateServiceImpl 单元测试
 * 覆盖：两级解析链（设备改绑→类型基础模版）、规则行形状校验、
 *   编码唯一、删除绑定保护、复制模版、迁移等价语义（改绑组整体替换/跟随类型）
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlarmTemplateService 单元测试")
class AlarmTemplateServiceImplTest {

    @Mock
    private AlarmTemplateMapper templateMapper;
    @Mock
    private AlarmTemplateItemMapper itemMapper;
    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private DeviceTypeMapper deviceTypeMapper;
    @Mock
    private PointDefinitionService pointDefinitionService;

    @InjectMocks
    private AlarmTemplateServiceImpl templateService;

    private static final Integer DEVICE_ID = 2;
    private static final Integer TYPE_ID = 4;
    private static final Long BASE_TEMPLATE_ID = 100L;
    private static final Long CUSTOM_TEMPLATE_ID = 200L;
    private static final Long POINT_TEMP = 1L;

    @BeforeEach
    void setUp() throws Exception {
        initBaseMapper();
        initLambdaCache();
        lenient().when(templateMapper.insert(any(AlarmTemplate.class))).thenReturn(1);
        lenient().when(templateMapper.updateById(any(AlarmTemplate.class))).thenReturn(1);
        lenient().when(itemMapper.insert(any(AlarmTemplateItem.class))).thenReturn(1);
        lenient().when(itemMapper.updateById(any(AlarmTemplateItem.class))).thenReturn(1);
    }

    private void initBaseMapper() throws Exception {
        Class<?> clazz = AlarmTemplateServiceImpl.class;
        Field field = null;
        while (clazz != null) {
            try { field = clazz.getDeclaredField("baseMapper"); break; }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        field.setAccessible(true);
        field.set(templateService, templateMapper);
    }

    private void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant tplAssistant = new MapperBuilderAssistant(configuration, "");
        tplAssistant.setCurrentNamespace(AlarmTemplateMapper.class.getName());
        TableInfoHelper.initTableInfo(tplAssistant, AlarmTemplate.class);
        MapperBuilderAssistant itemAssistant = new MapperBuilderAssistant(configuration, "");
        itemAssistant.setCurrentNamespace(AlarmTemplateItemMapper.class.getName());
        TableInfoHelper.initTableInfo(itemAssistant, AlarmTemplateItem.class);
        MapperBuilderAssistant typeAssistant = new MapperBuilderAssistant(configuration, "");
        typeAssistant.setCurrentNamespace(DeviceTypeMapper.class.getName());
        TableInfoHelper.initTableInfo(typeAssistant, DeviceType.class);
        MapperBuilderAssistant deviceAssistant = new MapperBuilderAssistant(configuration, "");
        deviceAssistant.setCurrentNamespace(DeviceMapper.class.getName());
        TableInfoHelper.initTableInfo(deviceAssistant, Device.class);
    }

    private AlarmTemplate template(long id) {
        AlarmTemplate template = new AlarmTemplate();
        template.setId(id);
        template.setTemplateCode("TPL_" + id);
        template.setTemplateName("模版" + id);
        template.setStatus(0);
        return template;
    }

    private AlarmTemplateItem item(long id, Long templateId, Long pointId, String ruleType,
                                   String operator, String threshold, int level) {
        AlarmTemplateItem item = new AlarmTemplateItem();
        item.setId(id);
        item.setTemplateId(templateId);
        item.setPointId(pointId);
        item.setRuleType(ruleType);
        item.setRuleName("规则" + id);
        item.setOperator(operator);
        item.setThreshold(threshold != null ? new BigDecimal(threshold) : null);
        item.setAlarmLevel(level);
        item.setEnabled(1);
        item.setSortOrder(0);
        return item;
    }

    private Device device(Long deviceTemplateId) {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setTypeId(TYPE_ID);
        device.setAlarmTemplateId(deviceTemplateId);
        return device;
    }

    private DeviceType type(Long typeTemplateId) {
        DeviceType type = new DeviceType();
        type.setId(TYPE_ID);
        type.setTypeCode("TEMP_HUMIDITY");
        type.setTypeName("温湿度");
        type.setAlarmTemplateId(typeTemplateId);
        return type;
    }

    @Nested
    @DisplayName("findEffectiveRules - 两级解析链")
    class FindEffectiveRules {

        @Test
        @DisplayName("设备改绑模版时整体切换到改绑模版规则（source=DEVICE_OVERRIDE）")
        void should_useDeviceTemplate_whenRebound() {
            when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(CUSTOM_TEMPLATE_ID));
            when(templateMapper.selectById(CUSTOM_TEMPLATE_ID)).thenReturn(template(CUSTOM_TEMPLATE_ID));
            when(itemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                    item(1, CUSTOM_TEMPLATE_ID, POINT_TEMP, "METRIC", "GT", "30", 2)));

            List<PointAlarmRuleVO> effective = templateService.findEffectiveRules(DEVICE_ID, POINT_TEMP);

            assertThat(effective).hasSize(1);
            assertThat(effective.get(0).getThreshold()).isEqualByComparingTo("30");
            assertThat(effective.get(0).getSource()).isEqualTo(PointAlarmRuleVO.SOURCE_DEVICE_OVERRIDE);
        }

        @Test
        @DisplayName("设备未改绑时跟随类型基础模版（source=TEMPLATE）")
        void should_followTypeTemplate_whenNotRebound() {
            when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(null));
            when(deviceTypeMapper.selectById(TYPE_ID)).thenReturn(type(BASE_TEMPLATE_ID));
            when(templateMapper.selectById(BASE_TEMPLATE_ID)).thenReturn(template(BASE_TEMPLATE_ID));
            when(itemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                    item(1, BASE_TEMPLATE_ID, POINT_TEMP, "METRIC", "GT", "35", 2),
                    item(2, BASE_TEMPLATE_ID, POINT_TEMP, "METRIC", "GT", "40", 1)));

            List<PointAlarmRuleVO> effective = templateService.findEffectiveRules(DEVICE_ID, POINT_TEMP);

            assertThat(effective).hasSize(2);
            // 组内级别升序（最严重在前）——与旧链路排序契约一致
            assertThat(effective.get(0).getAlarmLevel()).isEqualTo(1);
            assertThat(effective.get(0).getSource()).isEqualTo(PointAlarmRuleVO.SOURCE_TEMPLATE);
        }

        @Test
        @DisplayName("迁移等价：类型与设备均无绑定（旧模型无规则）返回空集")
        void should_returnEmpty_whenNoBinding() {
            when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(null));
            when(deviceTypeMapper.selectById(TYPE_ID)).thenReturn(type(null));

            List<PointAlarmRuleVO> effective = templateService.findEffectiveRules(DEVICE_ID, POINT_TEMP);

            assertThat(effective).isEmpty();
        }

        @Test
        @DisplayName("指定指标时通用规则（OFFLINE）同时命中")
        void should_includeGenericRules_whenPointSpecified() {
            when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(null));
            when(deviceTypeMapper.selectById(TYPE_ID)).thenReturn(type(BASE_TEMPLATE_ID));
            when(templateMapper.selectById(BASE_TEMPLATE_ID)).thenReturn(template(BASE_TEMPLATE_ID));
            when(itemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                    item(1, BASE_TEMPLATE_ID, POINT_TEMP, "METRIC", "GT", "35", 2),
                    item(4, BASE_TEMPLATE_ID, null, "OFFLINE", null, "15", 2)));

            List<PointAlarmRuleVO> effective = templateService.findEffectiveRules(DEVICE_ID, POINT_TEMP);

            assertThat(effective).extracting(PointAlarmRuleVO::getRuleType)
                    .containsExactlyInAnyOrder("METRIC", "OFFLINE");
        }
    }

    @Nested
    @DisplayName("createItem - 规则行形状校验")
    class CreateItem {

        private AlarmTemplateItemCreateDTO dto(String ruleType, Long pointId,
                                               String operator, String threshold) {
            AlarmTemplateItemCreateDTO dto = new AlarmTemplateItemCreateDTO();
            dto.setPointId(pointId);
            dto.setRuleType(ruleType);
            dto.setRuleName("高温警告");
            dto.setOperator(operator);
            dto.setThreshold(threshold != null ? new BigDecimal(threshold) : null);
            dto.setAlarmLevel(2);
            return dto;
        }

        @Test
        @DisplayName("METRIC 规则缺指标拒绝（4049）")
        void should_rejectMetricWithoutPoint() {
            when(templateMapper.selectById(BASE_TEMPLATE_ID)).thenReturn(template(BASE_TEMPLATE_ID));

            assertThatThrownBy(() -> templateService.createItem(BASE_TEMPLATE_ID,
                    dto("METRIC", null, "GT", "35")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(4049);
        }

        @Test
        @DisplayName("METRIC 规则缺阈值拒绝（4049）")
        void should_rejectMetricWithoutThreshold() {
            when(templateMapper.selectById(BASE_TEMPLATE_ID)).thenReturn(template(BASE_TEMPLATE_ID));

            assertThatThrownBy(() -> templateService.createItem(BASE_TEMPLATE_ID,
                    dto("METRIC", POINT_TEMP, "GT", null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(4049);
        }

        @Test
        @DisplayName("OFFLINE 规则阈值超出 1-1440 拒绝（4049）")
        void should_rejectOfflineThresholdOutOfRange() {
            when(templateMapper.selectById(BASE_TEMPLATE_ID)).thenReturn(template(BASE_TEMPLATE_ID));

            assertThatThrownBy(() -> templateService.createItem(BASE_TEMPLATE_ID,
                    dto("OFFLINE", null, null, "2000")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(4049);
        }

        @Test
        @DisplayName("同模版同指标同比较符重复拒绝（4043）")
        void should_rejectDuplicateItem() {
            when(templateMapper.selectById(BASE_TEMPLATE_ID)).thenReturn(template(BASE_TEMPLATE_ID));
            when(pointDefinitionService.getById(POINT_TEMP)).thenReturn(new PointDefinition());
            when(itemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThatThrownBy(() -> templateService.createItem(BASE_TEMPLATE_ID,
                    dto("METRIC", POINT_TEMP, "GT", "35")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(4043);
        }

        @Test
        @DisplayName("同指标不同比较符的多级阈值合法创建")
        void should_createMultiLevelThresholds() {
            when(templateMapper.selectById(BASE_TEMPLATE_ID)).thenReturn(template(BASE_TEMPLATE_ID));
            when(pointDefinitionService.getById(POINT_TEMP)).thenReturn(new PointDefinition());
            when(itemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            AlarmTemplateItemCreateDTO dto = dto("METRIC", POINT_TEMP, "GT", "40");
            dto.setRuleName("高温紧急");

            assertThat(templateService.createItem(BASE_TEMPLATE_ID, dto).getRuleName())
                    .isEqualTo("高温紧急");
        }
    }

    @Nested
    @DisplayName("模板 CRUD")
    class TemplateCrud {

        @Test
        @DisplayName("模版编码重复拒绝（4046）")
        void should_rejectDuplicateCode() {
            when(templateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            AlarmTemplateSaveDTO dto = new AlarmTemplateSaveDTO();
            dto.setTemplateCode("TEMP_HUM_STD");
            dto.setTemplateName("温湿度标准监控");

            assertThatThrownBy(() -> templateService.createTemplate(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(4046);
        }

        @Test
        @DisplayName("删除被类型绑定的模版拒绝（4047）")
        void should_rejectDelete_whenBoundToType() {
            when(templateMapper.selectById(BASE_TEMPLATE_ID)).thenReturn(template(BASE_TEMPLATE_ID));
            when(deviceTypeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
            when(deviceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            assertThatThrownBy(() -> templateService.deleteTemplate(BASE_TEMPLATE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(4047);
        }

        @Test
        @DisplayName("删除被设备改绑的模版拒绝（4047）")
        void should_rejectDelete_whenBoundToDevice() {
            when(templateMapper.selectById(CUSTOM_TEMPLATE_ID)).thenReturn(template(CUSTOM_TEMPLATE_ID));
            when(deviceTypeMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(deviceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

            assertThatThrownBy(() -> templateService.deleteTemplate(CUSTOM_TEMPLATE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(4047);
        }

        @Test
        @DisplayName("复制模版：规则行全量拷贝到新模版")
        void should_duplicateAllItems() {
            when(templateMapper.selectById(BASE_TEMPLATE_ID)).thenReturn(template(BASE_TEMPLATE_ID));
            when(templateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(itemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                    item(1, BASE_TEMPLATE_ID, POINT_TEMP, "METRIC", "GT", "35", 2),
                    item(4, BASE_TEMPLATE_ID, null, "OFFLINE", null, "15", 2)));

            AlarmTemplateVO copy = templateService.duplicateTemplate(BASE_TEMPLATE_ID, "TEMP_HUM_A1", "风口加严版");

            assertThat(copy.getTemplateCode()).isEqualTo("TEMP_HUM_A1");
            assertThat(copy.getTemplateName()).isEqualTo("风口加严版");
        }
    }

    @Nested
    @DisplayName("绑定管理")
    class Binding {

        @Test
        @DisplayName("停用模版不能绑定为类型基础模版（4049）")
        void should_rejectDisabledTemplateBinding() {
            when(deviceTypeMapper.selectById(TYPE_ID)).thenReturn(type(null));
            AlarmTemplate disabled = template(BASE_TEMPLATE_ID);
            disabled.setStatus(1);
            when(templateMapper.selectById(BASE_TEMPLATE_ID)).thenReturn(disabled);

            assertThatThrownBy(() -> templateService.updateTypeBinding(TYPE_ID, BASE_TEMPLATE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(4049);
        }

        @Test
        @DisplayName("设备改绑为空即跟随类型")
        void should_unbindDevice() {
            when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device(CUSTOM_TEMPLATE_ID));
            when(deviceMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

            templateService.updateDeviceBinding(DEVICE_ID, null);

            // null 传入即解绑：设备实体上 alarmTemplateId 置空，评估链回落到类型基础模版
            Device captured = device(null);
            assertThat(captured.getAlarmTemplateId()).isNull();
        }

        @Test
        @DisplayName("updateItem：更新后重复键拒绝（4043）")
        void should_rejectDuplicateOnUpdate() {
            when(itemMapper.selectById(1L)).thenReturn(item(1, BASE_TEMPLATE_ID, POINT_TEMP,
                    "METRIC", "GT", "35", 2));
            lenient().when(pointDefinitionService.getById(POINT_TEMP)).thenReturn(new PointDefinition());
            when(itemMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            AlarmTemplateItemUpdateDTO dto = new AlarmTemplateItemUpdateDTO();
            dto.setRuleName("高温警告");
            dto.setOperator("GT");
            dto.setThreshold(new BigDecimal("36"));
            dto.setAlarmLevel(2);

            assertThatThrownBy(() -> templateService.updateItem(1L, dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(4043);
        }
    }
}
