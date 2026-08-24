package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.DevicePointBindingDTO;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.Device;
import com.example.platform.entity.DevicePointBinding;
import com.example.platform.entity.DeviceTypePointTemplate;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.DevicePointBindingMapper;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.DeviceScopeHelper;
import com.example.platform.service.DeviceTypePointTemplateService;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:DevicePointBindingServiceImpl 单元测试
 * 覆盖 BIZ-OPEN-02：设备点位绑定 CRUD + 设备数据范围校验 + 模板拷贝初始化
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("DevicePointBindingService 单元测试")
class DevicePointBindingServiceImplTest {

    @Mock
    private DevicePointBindingMapper devicePointBindingMapper;
    @Mock
    private DeviceScopeHelper deviceScopeHelper;
    @Mock
    private PointDefinitionService pointDefinitionService;
    @Mock
    private DeviceTypePointTemplateService deviceTypePointTemplateService;
    @Mock
    private DataScopeService dataScopeService;

    @InjectMocks
    private DevicePointBindingServiceImpl devicePointBindingService;

    private static final Long CURRENT_USER_ID = 10L;

    @BeforeEach
    void setUp() throws Exception {
        initBaseMapper();
        initLambdaCache();
        lenient().when(dataScopeService.resolveDataScope(CURRENT_USER_ID))
                .thenReturn(writableOrgAdminContext());
    }

    private DataScopeContext writableOrgAdminContext() {
        return DataScopeContext.builder()
                .userId(CURRENT_USER_ID)
                .role("ORG_ADMIN")
                .scopeType("ORG_ALL")
                .organizationIds(java.util.Set.of(1L))
                .cloudRoomIds(java.util.Set.of())
                .build();
    }

    private void initBaseMapper() throws Exception {
        Class<?> clazz = DevicePointBindingServiceImpl.class;
        Field field = null;
        while (clazz != null) {
            try { field = clazz.getDeclaredField("baseMapper"); break; }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        assertThat(field).as("应存在 baseMapper 字段").isNotNull();
        field.setAccessible(true);
        field.set(devicePointBindingService, devicePointBindingMapper);
    }

    private void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(DevicePointBindingMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, DevicePointBinding.class);
    }

    private Device buildDevice(Integer id, Integer typeId) {
        Device device = new Device();
        device.setId(id);
        device.setTypeId(typeId);
        device.setOrganizationId(1L);
        device.setCloudRoomId(100L);
        return device;
    }

    @Nested
    @DisplayName("bindPoint - 绑定点位")
    class Bind {

        @Test
        @DisplayName("无设备范围权限应失败")
        void should_throw_when_deviceAccessDenied() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenThrow(new DataScopeViolationException("Device", "1"));

            DevicePointBindingDTO dto = new DevicePointBindingDTO();
            dto.setDeviceId(1);
            dto.setPointId(100L);

            assertThatThrownBy(() -> devicePointBindingService.bindPoint(dto, CURRENT_USER_ID))
                    .isInstanceOf(DataScopeViolationException.class);
            verify(devicePointBindingMapper, never()).insert((DevicePointBinding) any());
        }

        @Test
        @DisplayName("点位不存在应失败")
        void should_throw_when_pointNotFound() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenReturn(buildDevice(1, 1));
            when(pointDefinitionService.getPointDefinitionOrThrow(100L))
                    .thenThrow(new BusinessException("点位不存在"));

            DevicePointBindingDTO dto = new DevicePointBindingDTO();
            dto.setDeviceId(1);
            dto.setPointId(100L);

            assertThatThrownBy(() -> devicePointBindingService.bindPoint(dto, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("点位不存在");
            verify(devicePointBindingMapper, never()).insert((DevicePointBinding) any());
        }

        @Test
        @DisplayName("已绑定相同点位应拒绝")
        void should_throw_when_alreadyBound() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenReturn(buildDevice(1, 1));
            PointDefinition point = new PointDefinition();
            point.setId(100L);
            when(pointDefinitionService.getPointDefinitionOrThrow(100L)).thenReturn(point);
            when(devicePointBindingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            DevicePointBindingDTO dto = new DevicePointBindingDTO();
            dto.setDeviceId(1);
            dto.setPointId(100L);

            assertThatThrownBy(() -> devicePointBindingService.bindPoint(dto, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已绑定点位");
            verify(devicePointBindingMapper, never()).insert((DevicePointBinding) any());
        }

        @Test
        @DisplayName("正常绑定点位")
        void should_bindPoint() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenReturn(buildDevice(1, 1));
            PointDefinition point = new PointDefinition();
            point.setId(100L);
            when(pointDefinitionService.getPointDefinitionOrThrow(100L)).thenReturn(point);
            when(devicePointBindingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(devicePointBindingMapper.insert((DevicePointBinding) any())).thenReturn(1);

            DevicePointBindingDTO dto = new DevicePointBindingDTO();
            dto.setDeviceId(1);
            dto.setPointId(100L);
            dto.setStatus(0);

            DevicePointBinding result = devicePointBindingService.bindPoint(dto, CURRENT_USER_ID);
            assertThat(result.getDeviceId()).isEqualTo(1);
            assertThat(result.getPointId()).isEqualTo(100L);
            assertThat(result.getStatus()).isEqualTo(0);
            verify(devicePointBindingMapper, times(1)).insert((DevicePointBinding) any());
        }
    }

    @Nested
    @DisplayName("initBindingsFromTemplate - 模板初始化")
    class InitFromTemplate {

        @Test
        @DisplayName("类型ID 与设备类型不匹配应拒绝")
        void should_throw_when_typeMismatch() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenReturn(buildDevice(1, 2)); // 设备类型是 2

            assertThatThrownBy(() -> devicePointBindingService
                    .initBindingsFromTemplate(1, 99, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("类型与传入 typeId 不匹配");
        }

        @Test
        @DisplayName("按模板批量插入未绑定的新点位（已绑定跳过）")
        void should_copyUnboundTemplatePoints() {
            Device device = buildDevice(1, 1);
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID))).thenReturn(device);

            DeviceTypePointTemplate t1 = new DeviceTypePointTemplate();
            t1.setTypeId(1); t1.setPointId(10L); t1.setIsRequired(1);
            DeviceTypePointTemplate t2 = new DeviceTypePointTemplate();
            t2.setTypeId(1); t2.setPointId(20L); t2.setIsRequired(0);
            when(deviceTypePointTemplateService.listEffectiveTemplates(1, 1L, CURRENT_USER_ID))
                    .thenReturn(List.of(t1, t2));

            // 设备已绑定点位 10
            DevicePointBinding existing = new DevicePointBinding();
            existing.setDeviceId(1);
            existing.setPointId(10L);
            when(devicePointBindingMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(existing));

            PointDefinition p20 = new PointDefinition();
            p20.setId(20L);
            when(pointDefinitionService.getPointDefinitionOrThrow(20L)).thenReturn(p20);
            when(devicePointBindingMapper.insert((DevicePointBinding) any())).thenReturn(1);

            List<DevicePointBinding> result =
                    devicePointBindingService.initBindingsFromTemplate(1, 1, CURRENT_USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPointId()).isEqualTo(20L);
            verify(devicePointBindingMapper, times(1)).insert((DevicePointBinding) any());
        }

        @Test
        @DisplayName("模板为空时返回空列表不报错")
        void should_returnEmpty_when_templateEmpty() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenReturn(buildDevice(1, 1));
            when(deviceTypePointTemplateService.listEffectiveTemplates(1, 1L, CURRENT_USER_ID))
                    .thenReturn(List.of());

            List<DevicePointBinding> result =
                    devicePointBindingService.initBindingsFromTemplate(1, 1, CURRENT_USER_ID);
            assertThat(result).isEmpty();
            verify(devicePointBindingMapper, never()).insert((DevicePointBinding) any());
        }
    }

    @Nested
    @DisplayName("listByDevice - 查询某设备绑定列表")
    class ListByDevice {

        @Test
        @DisplayName("范围校验失败应抛 DataScopeViolationException")
        void should_throw_when_deviceAccessDenied() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenThrow(new DataScopeViolationException("Device", "1"));

            assertThatThrownBy(() -> devicePointBindingService.listByDevice(1, CURRENT_USER_ID))
                    .isInstanceOf(DataScopeViolationException.class);
            verify(devicePointBindingMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("正常返回该设备的绑定列表")
        void should_returnList() {
            when(deviceScopeHelper.ensureDeviceAccess(eq(1), eq(CURRENT_USER_ID)))
                    .thenReturn(buildDevice(1, 1));
            DevicePointBinding b = new DevicePointBinding();
            b.setDeviceId(1); b.setPointId(10L);
            when(devicePointBindingMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(b));

            List<DevicePointBinding> result = devicePointBindingService.listByDevice(1, CURRENT_USER_ID);
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("pageByConditions - 分页查询绑定")
    class PageBindings {

        @Test
        @DisplayName("未指定 deviceId 应拒绝跨设备扫描")
        void should_throw_when_deviceIdMissing() {
            assertThatThrownBy(() -> devicePointBindingService.pageByConditions(
                    CURRENT_USER_ID, 1, 10, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("必须指定 deviceId");
            verify(devicePointBindingMapper, never())
                    .selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }
    }

    @Nested
    @DisplayName("getActiveBindingOrThrow - 采样绑定校验")
    class ActiveBinding {

        @Test
        @DisplayName("未启用点位绑定应拒绝采样")
        void should_throw_when_activeBindingMissing() {
            when(deviceScopeHelper.ensureDeviceAccess(1, CURRENT_USER_ID))
                    .thenReturn(buildDevice(1, 1));
            when(devicePointBindingMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean()))
                    .thenReturn(null);

            assertThatThrownBy(() -> devicePointBindingService
                    .getActiveBindingOrThrow(1, 10L, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("未启用点位");
        }
    }
}
