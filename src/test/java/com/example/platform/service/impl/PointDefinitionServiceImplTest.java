package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.dto.PointDefinitionCreateDTO;
import com.example.platform.dto.PointDefinitionUpdateDTO;
import com.example.platform.entity.PointDefinition;
import com.example.platform.mapper.DevicePointAlarmRuleMapper;
import com.example.platform.mapper.DevicePointBindingMapper;
import com.example.platform.mapper.DeviceTypeMapper;
import com.example.platform.mapper.PointDefinitionMapper;
import com.example.platform.security.CurrentOrganizationContext;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.DeviceTypePointTemplateService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:PointDefinitionServiceImpl 单元测试
 * 覆盖：双层归属（D07）、组织内编码唯一、平台级资源权限（D26）、删除引用保护、分页双层可见
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("PointDefinitionService 单元测试")
class PointDefinitionServiceImplTest {

    @Mock
    private PointDefinitionMapper pointDefinitionMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private CurrentOrganizationContext currentOrganizationContext;
    @Mock
    private DeviceTypeMapper deviceTypeMapper;
    @Mock
    private com.example.platform.mapper.DeviceTypePointTemplateMapper templateMapper;
    @Mock
    private DevicePointBindingMapper bindingMapper;
    @Mock
    private DevicePointAlarmRuleMapper alarmRuleMapper;

    @InjectMocks
    private PointDefinitionServiceImpl pointDefinitionService;

    private static final Long USER_ID = 20L;
    private static final Long ORG_ID = 1L;

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

    private DataScopeContext platformAdmin() {
        return DataScopeContext.builder().userId(1L).role("PLATFORM_ADMIN").scopeType("PLATFORM").build();
    }

    private DataScopeContext orgAdmin() {
        return DataScopeContext.builder().userId(USER_ID).role("ORG_ADMIN")
                .scopeType("ORG_ALL").organizationIds(new java.util.HashSet<>(List.of(ORG_ID))).build();
    }

    private PointDefinition buildPoint(Long id, String code, Long orgId) {
        PointDefinition p = new PointDefinition();
        p.setId(id);
        p.setPointCode(code);
        p.setPointName("A相电压");
        p.setUnit("V");
        p.setDataType("NUMBER");
        p.setOrganizationId(orgId);
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
    @DisplayName("createPointDefinition - 新增点位（双层归属）")
    class Create {

        @Test
        @DisplayName("组织用户带组织上下文创建为组织私有")
        void should_create_asOrgPrivate_withOrgContext() {
            when(dataScopeService.resolveDataScope(USER_ID)).thenReturn(orgAdmin());
            when(currentOrganizationContext.currentOrganizationId()).thenReturn(Optional.of(ORG_ID));
            when(pointDefinitionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            PointDefinition created = pointDefinitionService.createPointDefinition(createDTO("voltage_b"), USER_ID);

            assertThat(created.getOrganizationId()).isEqualTo(ORG_ID);
        }

        @Test
        @DisplayName("组织用户无组织上下文拒绝创建（防误建平台级）")
        void should_reject_orgUser_withoutOrgContext() {
            when(dataScopeService.resolveDataScope(USER_ID)).thenReturn(orgAdmin());
            when(currentOrganizationContext.currentOrganizationId()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pointDefinitionService.createPointDefinition(createDTO("voltage_b"), USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("组织上下文");
        }

        @Test
        @DisplayName("平台管理员无组织上下文创建为平台级")
        void should_create_asPlatform_whenPlatformAdminNoContext() {
            when(dataScopeService.resolveDataScope(1L)).thenReturn(platformAdmin());
            when(currentOrganizationContext.currentOrganizationId()).thenReturn(Optional.empty());
            when(pointDefinitionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            PointDefinition created = pointDefinitionService.createPointDefinition(createDTO("voltage_c"), 1L);

            assertThat(created.getOrganizationId()).isEqualTo(0L);
        }

        @Test
        @DisplayName("同组织编码冲突拒绝（4034）")
        void should_throw_when_codeExistsInSameOrg() {
            when(dataScopeService.resolveDataScope(USER_ID)).thenReturn(orgAdmin());
            when(currentOrganizationContext.currentOrganizationId()).thenReturn(Optional.of(ORG_ID));
            when(pointDefinitionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThatThrownBy(() -> pointDefinitionService.createPointDefinition(createDTO("voltage_a"), USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(4034);
        }
    }

    @Nested
    @DisplayName("updatePointDefinition - 更新（归属权限）")
    class Update {

        @Test
        @DisplayName("平台管理员修改组织级资源拒绝（D26）")
        void should_reject_platformAdmin_onOrgPoint() {
            when(pointDefinitionMapper.selectById(5L)).thenReturn(buildPoint(5L, "temperature", ORG_ID));
            when(dataScopeService.resolveDataScope(1L)).thenReturn(platformAdmin());

            assertThatThrownBy(() -> pointDefinitionService.updatePointDefinition(
                    5L, new PointDefinitionUpdateDTO(), 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("D26");
        }

        @Test
        @DisplayName("组织角色修改平台级资源拒绝")
        void should_reject_orgRole_onPlatformPoint() {
            when(pointDefinitionMapper.selectById(5L)).thenReturn(buildPoint(5L, "temperature", 0L));
            when(dataScopeService.resolveDataScope(USER_ID)).thenReturn(orgAdmin());

            assertThatThrownBy(() -> pointDefinitionService.updatePointDefinition(
                    5L, new PointDefinitionUpdateDTO(), USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("平台管理员");
        }
    }

    @Nested
    @DisplayName("deletePointDefinition - 删除保护")
    class Delete {

        @Test
        @DisplayName("被告警规则引用的点位拒绝删除（4035）")
        void should_reject_whenReferencedByRule() {
            when(pointDefinitionMapper.selectById(5L)).thenReturn(buildPoint(5L, "temperature", ORG_ID));
            when(dataScopeService.resolveDataScope(USER_ID)).thenReturn(orgAdmin());
            lenient().when(templateMapper.selectCount(any())).thenReturn(0L);
            lenient().when(bindingMapper.selectCount(any())).thenReturn(0L);
            when(alarmRuleMapper.selectCount(any())).thenReturn(2L);

            assertThatThrownBy(() -> pointDefinitionService.deletePointDefinition(5L, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(4035);
            verify(pointDefinitionMapper, never()).deleteById(5L);
        }
    }

    @Nested
    @DisplayName("pagePointDefinitions - 分页（双层可见）")
    class PageQuery {

        @Test
        @DisplayName("组织用户无组织授权返回空页")
        void should_returnEmpty_whenNoOrgScope() {
            when(dataScopeService.resolveDataScope(USER_ID)).thenReturn(
                    DataScopeContext.builder().userId(USER_ID).role("VIEWER")
                            .scopeType("READ_ONLY").build());

            Page<PointDefinition> result = pointDefinitionService.pagePointDefinitions(USER_ID, 1, 10, null, null);

            assertThat(result.getRecords()).isEmpty();
        }
    }
}
