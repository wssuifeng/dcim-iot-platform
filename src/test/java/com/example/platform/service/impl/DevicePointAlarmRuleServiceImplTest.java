package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.PointAlarmRuleCreateDTO;
import com.example.platform.dto.PointAlarmRuleVO;
import com.example.platform.entity.Device;
import com.example.platform.entity.DevicePointAlarmRule;
import com.example.platform.entity.DeviceTypePointTemplate;
import com.example.platform.mapper.DevicePointAlarmRuleMapper;
import com.example.platform.mapper.DeviceTypePointTemplateMapper;
import com.example.platform.service.DeviceScopeHelper;
import com.example.platform.service.DeviceService;
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
import static org.mockito.Mockito.when;

/*
 * 类功能:DevicePointAlarmRuleServiceImpl 单元测试
 * 覆盖：两级降级（按点位替换式）、METRIC/OFFLINE 形状校验、重复键拒绝
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("DevicePointAlarmRuleService 单元测试")
class DevicePointAlarmRuleServiceImplTest {

    @Mock
    private DevicePointAlarmRuleMapper ruleMapper;
    @Mock
    private DeviceTypePointTemplateMapper templateMapper;
    @Mock
    private DeviceService deviceService;
    @Mock
    private DeviceScopeHelper deviceScopeHelper;
    @Mock
    private PointDefinitionService pointDefinitionService;

    @InjectMocks
    private DevicePointAlarmRuleServiceImpl ruleService;

    private static final Long CURRENT_USER_ID = 20L;
    private static final Integer DEVICE_ID = 2;
    private static final Integer TYPE_ID = 4;
    private static final Long POINT_TEMP = 1L;

    @BeforeEach
    void setUp() throws Exception {
        initBaseMapper();
        initLambdaCache();
    }

    private void initBaseMapper() throws Exception {
        Class<?> clazz = DevicePointAlarmRuleServiceImpl.class;
        Field field = null;
        while (clazz != null) {
            try { field = clazz.getDeclaredField("baseMapper"); break; }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        field.setAccessible(true);
        field.set(ruleService, ruleMapper);
    }

    private void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant ruleAssistant = new MapperBuilderAssistant(configuration, "");
        ruleAssistant.setCurrentNamespace(DevicePointAlarmRuleMapper.class.getName());
        TableInfoHelper.initTableInfo(ruleAssistant, DevicePointAlarmRule.class);
        MapperBuilderAssistant templateAssistant = new MapperBuilderAssistant(configuration, "");
        templateAssistant.setCurrentNamespace(DeviceTypePointTemplateMapper.class.getName());
        TableInfoHelper.initTableInfo(templateAssistant, DeviceTypePointTemplate.class);
    }

    private DeviceTypePointTemplate templateItem(long id, long pointId) {
        DeviceTypePointTemplate item = new DeviceTypePointTemplate();
        item.setId(id);
        item.setTypeId(TYPE_ID);
        item.setPointId(pointId);
        return item;
    }

    private DevicePointAlarmRule templateRule(long id, Long pointId, String operator,
                                              String threshold, int level) {
        DevicePointAlarmRule rule = new DevicePointAlarmRule();
        rule.setId(id);
        rule.setTemplateItemId(1L);
        rule.setDeviceId(null);
        rule.setPointId(pointId);
        rule.setRuleType("METRIC");
        rule.setRuleName("高温");
        rule.setOperator(operator);
        rule.setThreshold(new BigDecimal(threshold));
        rule.setAlarmLevel(level);
        rule.setEnabled(1);
        return rule;
    }

    private DevicePointAlarmRule overrideRule(long id, Long pointId, String operator,
                                              String threshold, int level) {
        DevicePointAlarmRule rule = templateRule(id, pointId, operator, threshold, level);
        rule.setDeviceId(DEVICE_ID);
        return rule;
    }

    private Device device() {
        Device device = new Device();
        device.setId(DEVICE_ID);
        device.setTypeId(TYPE_ID);
        return device;
    }

    @Nested
    @DisplayName("findEffectiveRules - 两级降级（按点位替换式）")
    class FindEffectiveRules {

        @Test
        @DisplayName("设备级覆盖存在时该点位整体替换模板级")
        void should_replaceTemplateRules_whenDeviceOverrideExists() {
            when(deviceService.getById(DEVICE_ID)).thenReturn(device());
            when(templateMapper.selectList(any())).thenReturn(List.of(templateItem(1, POINT_TEMP)));
            // 模板级 35/40 + 设备级 30
            when(ruleMapper.selectList(any())).thenReturn(List.of(
                    templateRule(1, POINT_TEMP, "GT", "35", 2),
                    templateRule(2, POINT_TEMP, "GT", "40", 1),
                    overrideRule(3, POINT_TEMP, "GT", "30", 2)));

            List<PointAlarmRuleVO> effective = ruleService.findEffectiveRules(DEVICE_ID, POINT_TEMP);

            assertThat(effective).hasSize(1);
            assertThat(effective.get(0).getId()).isEqualTo(3L);
            assertThat(effective.get(0).getThreshold()).isEqualByComparingTo("30");
            assertThat(effective.get(0).getSource()).isEqualTo(PointAlarmRuleVO.SOURCE_DEVICE_OVERRIDE);
        }

        @Test
        @DisplayName("无设备覆盖时降级模板规则")
        void should_fallbackToTemplate() {
            when(deviceService.getById(DEVICE_ID)).thenReturn(device());
            when(templateMapper.selectList(any())).thenReturn(List.of(templateItem(1, POINT_TEMP)));
            when(ruleMapper.selectList(any())).thenReturn(List.of(
                    templateRule(1, POINT_TEMP, "GT", "35", 2),
                    templateRule(2, POINT_TEMP, "GT", "40", 1)));

            List<PointAlarmRuleVO> effective = ruleService.findEffectiveRules(DEVICE_ID, POINT_TEMP);

            assertThat(effective).hasSize(2);
            assertThat(effective.get(0).getSource()).isEqualTo(PointAlarmRuleVO.SOURCE_TEMPLATE);
            // 组内级别升序（最严重在前）
            assertThat(effective.get(0).getAlarmLevel()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("create - 规则形状与重复校验")
    class Create {

        private PointAlarmRuleCreateDTO dto(String ruleType, String operator, String threshold) {
            PointAlarmRuleCreateDTO dto = new PointAlarmRuleCreateDTO();
            dto.setTemplateItemId(1L);
            dto.setRuleType(ruleType);
            dto.setRuleName("高温警告");
            dto.setOperator(operator);
            dto.setThreshold(threshold != null ? new BigDecimal(threshold) : null);
            dto.setAlarmLevel(2);
            return dto;
        }

        @Test
        @DisplayName("METRIC 规则缺阈值拒绝（4044）")
        void should_rejectMetricWithoutThreshold() {
            when(templateMapper.selectById(1L)).thenReturn(templateItem(1, POINT_TEMP));

            assertThatThrownBy(() -> ruleService.create(dto("METRIC", "GT", null), CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(4044);
        }

        @Test
        @DisplayName("OFFLINE 规则阈值超出 1-1440 拒绝（4044）")
        void should_rejectOfflineThresholdOutOfRange() {
            when(templateMapper.selectById(1L)).thenReturn(templateItem(1, POINT_TEMP));

            assertThatThrownBy(() -> ruleService.create(dto("OFFLINE", null, "2000"), CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(4044);
        }

        @Test
        @DisplayName("业务唯一键重复拒绝（4043）")
        void should_rejectDuplicateRule() {
            when(templateMapper.selectById(1L)).thenReturn(templateItem(1, POINT_TEMP));
            when(ruleMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> ruleService.create(dto("METRIC", "GT", "35"), CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(4043);
        }
    }
}
