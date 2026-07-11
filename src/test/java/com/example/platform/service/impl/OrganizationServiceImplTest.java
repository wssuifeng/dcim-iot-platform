package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.dto.OrganizationCreateDTO;
import com.example.platform.dto.OrganizationUpdateDTO;
import com.example.platform.entity.Organization;
import com.example.platform.mapper.OrganizationMapper;
import com.example.platform.service.DataScopeService;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:OrganizationServiceImpl 单元测试，覆盖编码唯一性、范围过滤、写操作权限校验
 * 覆盖任务卡 BIZ-OPEN-01：组织管理员不能跨组织；平台初始化组织管理员
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationService 单元测试")
class OrganizationServiceImplTest {

    @Mock
    private OrganizationMapper organizationMapper;

    @Mock
    private DataScopeService dataScopeService;

    @InjectMocks
    private OrganizationServiceImpl organizationService;

    @BeforeEach
    void setUp() throws Exception {
        initBaseMapper();
        initLambdaCache();
    }

    private void initBaseMapper() throws Exception {
        Class<?> clazz = OrganizationServiceImpl.class;
        Field baseMapperField = null;
        while (clazz != null) {
            try {
                baseMapperField = clazz.getDeclaredField("baseMapper");
                break;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        assertThat(baseMapperField).as("应在继承链中找到baseMapper字段").isNotNull();
        baseMapperField.setAccessible(true);
        baseMapperField.set(organizationService, organizationMapper);
    }

    private void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(OrganizationMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, Organization.class);
    }

    private DataScopeContext platformAdminContext() {
        return DataScopeContext.builder()
                .userId(1L).role("PLATFORM_ADMIN").scopeType("PLATFORM")
                .organizationIds(new HashSet<>()).cloudRoomIds(new HashSet<>()).build();
    }

    private DataScopeContext orgAdminContext(Set<Long> orgIds) {
        return DataScopeContext.builder()
                .userId(2L).role("ORG_ADMIN").scopeType("ORG_ALL")
                .organizationIds(orgIds).cloudRoomIds(new HashSet<>()).build();
    }

    private Organization buildOrg(Long id, String code, String name, Integer status) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setOrgCode(code);
        organization.setOrgName(name);
        organization.setStatus(status);
        return organization;
    }

    @Nested
    @DisplayName("createOrganization - 创建组织")
    class CreateOrganization {

        @Test
        @DisplayName("编码冲突应抛出 BusinessException")
        void should_throw_when_orgCodeAlreadyExists() {
            // count > 0 表示编码已存在
            when(organizationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

            OrganizationCreateDTO dto = new OrganizationCreateDTO();
            dto.setOrgName("测试组织");
            dto.setOrgCode("ORG-001");
            dto.setDescription("测试");

            assertThatThrownBy(() -> organizationService.createOrganization(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("组织编码已存在");
            verify(organizationMapper, never()).insert((Organization) any());
        }

        @Test
        @DisplayName("正常创建应设置默认状态并调用 mapper.insert")
        void should_insert_when_codeUnique() {
            when(organizationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(organizationMapper.insert((Organization) any())).thenReturn(1);

            OrganizationCreateDTO dto = new OrganizationCreateDTO();
            dto.setOrgName("新组织");
            dto.setOrgCode("ORG-002");
            dto.setDescription("测试描述");

            Organization saved = organizationService.createOrganization(dto);

            assertThat(saved.getOrgName()).isEqualTo("新组织");
            assertThat(saved.getOrgCode()).isEqualTo("ORG-002");
            assertThat(saved.getStatus()).isEqualTo(0);
            verify(organizationMapper, times(1)).insert((Organization) any());
        }

        @Test
        @DisplayName("mapper.insert 返回 0 时应抛出 BusinessException")
        void should_throw_when_insertFails() {
            when(organizationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(organizationMapper.insert((Organization) any())).thenReturn(0);

            OrganizationCreateDTO dto = new OrganizationCreateDTO();
            dto.setOrgName("失败组织");
            dto.setOrgCode("ORG-FAIL");
            assertThatThrownBy(() -> organizationService.createOrganization(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("创建组织失败");
        }
    }

    @Nested
    @DisplayName("updateOrganization - 更新组织")
    class UpdateOrganization {

        @Test
        @DisplayName("组织不存在时抛出 BusinessException")
        void should_throw_when_notExists() {
            when(organizationMapper.selectById(99L)).thenReturn(null);

            OrganizationUpdateDTO dto = new OrganizationUpdateDTO();
            dto.setOrgName("新名称");

            assertThatThrownBy(() -> organizationService.updateOrganization(99L, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("组织不存在");
        }

        @Test
        @DisplayName("正常更新字段并更新成功")
        void should_updateFields() {
            Organization existing = buildOrg(1L, "ORG-001", "旧名称", 0);
            when(organizationMapper.selectById(1L)).thenReturn(existing);
            when(organizationMapper.updateById(any(Organization.class))).thenReturn(1);

            OrganizationUpdateDTO dto = new OrganizationUpdateDTO();
            dto.setOrgName("新名称");
            dto.setDescription("新描述");

            Organization updated = organizationService.updateOrganization(1L, dto);

            assertThat(updated.getOrgName()).isEqualTo("新名称");
            assertThat(updated.getDescription()).isEqualTo("新描述");
            assertThat(updated.getOrgCode()).isEqualTo("ORG-001"); // 编码不可改
            verify(organizationMapper, times(1)).updateById(any(Organization.class));
        }
    }

    @Nested
    @DisplayName("updateStatus - 启用/停用组织")
    class UpdateStatus {

        @Test
        @DisplayName("非法状态值应抛出 BusinessException")
        void should_throw_when_invalidStatus() {
            assertThatThrownBy(() -> organizationService.updateStatus(1L, -1))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("状态值无效");
            assertThatThrownBy(() -> organizationService.updateStatus(1L, 3))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("状态值无效");
        }

        @Test
        @DisplayName("正常停用组织")
        void should_updateStatus() {
            Organization existing = buildOrg(1L, "ORG-001", "组织", 0);
            when(organizationMapper.selectById(1L)).thenReturn(existing);
            when(organizationMapper.updateById(any(Organization.class))).thenReturn(1);

            Organization updated = organizationService.updateStatus(1L, 1);
            assertThat(updated.getStatus()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getOrganizationOrThrow - 按ID查询带范围校验")
    class GetOrThrowScopeCheck {

        @Test
        @DisplayName("不存在应抛 BusinessException")
        void should_throw_when_notExists() {
            when(organizationMapper.selectById(99L)).thenReturn(null);
            assertThatThrownBy(() -> organizationService.getOrganizationOrThrow(99L, 2L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("ORG_ADMIN 访问未授权组织应抛 DataScopeViolationException")
        void should_throw_when_orgAdminAccessDenied() {
            Organization existing = buildOrg(2L, "ORG-002", "另一组织", 0);
            when(organizationMapper.selectById(2L)).thenReturn(existing);
            Set<Long> ownedOrgs = new HashSet<>();
            ownedOrgs.add(1L); // 只拥有组织1
            when(dataScopeService.resolveDataScope(2L)).thenReturn(orgAdminContext(ownedOrgs));

            assertThatThrownBy(() -> organizationService.getOrganizationOrThrow(2L, 2L))
                    .isInstanceOf(DataScopeViolationException.class);
        }

        @Test
        @DisplayName("ORG_ADMIN 访问自身组织应成功")
        void should_return_when_orgAdminHasScope() {
            Organization existing = buildOrg(1L, "ORG-001", "本组织", 0);
            when(organizationMapper.selectById(1L)).thenReturn(existing);
            Set<Long> ownedOrgs = new HashSet<>();
            ownedOrgs.add(1L);
            when(dataScopeService.resolveDataScope(2L)).thenReturn(orgAdminContext(ownedOrgs));

            Organization result = organizationService.getOrganizationOrThrow(1L, 2L);
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("PLATFORM_ADMIN 不默认穿透组织业务详情")
        void should_reject_when_platformAdminHasNoMembership() {
            Organization existing = buildOrg(99L, "ORG-X", "任意组织", 0);
            when(organizationMapper.selectById(99L)).thenReturn(existing);
            when(dataScopeService.resolveDataScope(1L)).thenReturn(platformAdminContext());

            assertThatThrownBy(() -> organizationService.getOrganizationOrThrow(99L, 1L))
                    .isInstanceOf(DataScopeViolationException.class);
        }
    }

    @Nested
    @DisplayName("pageOrganizations - 分页范围过滤")
    class PageOrganizations {

        @Test
        @DisplayName("PLATFORM_ADMIN 返回全部组织")
        void should_returnAll_when_platformAdmin() {
            when(dataScopeService.resolveDataScope(1L)).thenReturn(platformAdminContext());
            Page<Organization> mockPage = new Page<>(1, 10);
            mockPage.setRecords(List.of(buildOrg(1L, "ORG-001", "A", 0), buildOrg(2L, "ORG-002", "B", 0)));
            when(organizationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            Page<Organization> result = organizationService.pageOrganizations(1L, 1, 10, null, null);
            assertThat(result.getRecords()).hasSize(2);
            verify(organizationMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("ORG_ADMIN 仅返回授权组织")
        void should_filter_when_orgAdmin() {
            Set<Long> ownedOrgs = new HashSet<>();
            ownedOrgs.add(1L);
            ownedOrgs.add(2L);
            when(dataScopeService.resolveDataScope(2L)).thenReturn(orgAdminContext(ownedOrgs));
            Page<Organization> mockPage = new Page<>(1, 10);
            mockPage.setRecords(List.of(buildOrg(1L, "ORG-001", "A", 0)));
            when(organizationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            Page<Organization> result = organizationService.pageOrganizations(2L, 1, 10, "A", null);
            assertThat(result.getRecords()).hasSize(1);
        }

        @Test
        @DisplayName("ORG_ADMIN 无任何组织时应返回空页且不调用 mapper")
        void should_returnEmpty_when_orgAdminNoScope() {
            when(dataScopeService.resolveDataScope(2L)).thenReturn(orgAdminContext(new HashSet<>()));

            Page<Organization> result = organizationService.pageOrganizations(2L, 1, 10, null, null);
            assertThat(result.getRecords()).isEmpty();
            verify(organizationMapper, never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }
    }
}
