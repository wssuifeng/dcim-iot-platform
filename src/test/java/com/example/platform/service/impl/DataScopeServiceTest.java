package com.example.platform.service.impl;

import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.DataScopeAssignment;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.DataScopeAssignmentMapper;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/*
 * 类功能:DataScopeServiceImpl 单元测试，覆盖范围解析逻辑
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataScopeService 单元测试")
class DataScopeServiceTest {

    @Mock
    private SysUserService sysUserService;

    @Mock
    private DataScopeAssignmentMapper dataScopeAssignmentMapper;

    @InjectMocks
    private DataScopeServiceImpl dataScopeService;

    private SysUser platformAdmin;
    private SysUser orgAdmin;
    private SysUser operator;
    private SysUser viewer;

    @BeforeEach
    void setUp() {
        platformAdmin = new SysUser();
        platformAdmin.setId(1L);
        platformAdmin.setUsername("platform_admin");
        platformAdmin.setRole("PLATFORM_ADMIN");

        orgAdmin = new SysUser();
        orgAdmin.setId(2L);
        orgAdmin.setUsername("org_admin");
        orgAdmin.setRole("ORG_ADMIN");

        operator = new SysUser();
        operator.setId(3L);
        operator.setUsername("operator");
        operator.setRole("OPERATOR");

        viewer = new SysUser();
        viewer.setId(4L);
        viewer.setUsername("viewer");
        viewer.setRole("VIEWER");
    }

    @Test
    @DisplayName("PLATFORM_ADMIN 应返回平台级范围（组织和机房ID为空）")
    void resolveDataScope_PlatformAdmin() {
        // Given
        when(sysUserService.getUserOrThrow(1L)).thenReturn(platformAdmin);

        // When
        DataScopeContext context = dataScopeService.resolveDataScope(1L);

        // Then
        assertThat(context).isNotNull();
        assertThat(context.getUserId()).isEqualTo(1L);
        assertThat(context.getRole()).isEqualTo("PLATFORM_ADMIN");
        assertThat(context.getScopeType()).isEqualTo("PLATFORM");
        assertThat(context.getOrganizationIds()).isEmpty();
        assertThat(context.getCloudRoomIds()).isEmpty();
        assertThat(context.isPlatformAdmin()).isTrue();
        assertThat(context.hasWritePermission()).isTrue();
    }

    @Test
    @DisplayName("ORG_ADMIN 应返回 ORG_ALL 范围类型")
    void resolveDataScope_OrgAdmin() {
        // Given
        when(sysUserService.getUserOrThrow(2L)).thenReturn(orgAdmin);

        DataScopeAssignment assignment1 = new DataScopeAssignment();
        assignment1.setUserId(2L);
        assignment1.setOrganizationId(1L);
        assignment1.setScopeType("ORG_ALL");

        DataScopeAssignment assignment2 = new DataScopeAssignment();
        assignment2.setUserId(2L);
        assignment2.setOrganizationId(2L);
        assignment2.setScopeType("ORG_ALL");

        when(dataScopeAssignmentMapper.selectList(any())).thenReturn(Arrays.asList(assignment1, assignment2));

        // When
        DataScopeContext context = dataScopeService.resolveDataScope(2L);

        // Then
        assertThat(context).isNotNull();
        assertThat(context.getUserId()).isEqualTo(2L);
        assertThat(context.getRole()).isEqualTo("ORG_ADMIN");
        assertThat(context.getScopeType()).isEqualTo("ORG_ALL");
        assertThat(context.getOrganizationIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(context.isOrgAdmin()).isTrue();
        assertThat(context.hasWritePermission()).isTrue();
    }

    @Test
    @DisplayName("OPERATOR 有机房授权应返回 ROOMS 范围类型")
    void resolveDataScope_OperatorWithRooms() {
        // Given
        when(sysUserService.getUserOrThrow(3L)).thenReturn(operator);

        DataScopeAssignment assignment1 = new DataScopeAssignment();
        assignment1.setUserId(3L);
        assignment1.setOrganizationId(1L);
        assignment1.setCloudRoomId(10L);
        assignment1.setScopeType("ROOMS");

        DataScopeAssignment assignment2 = new DataScopeAssignment();
        assignment2.setUserId(3L);
        assignment2.setOrganizationId(1L);
        assignment2.setCloudRoomId(20L);
        assignment2.setScopeType("ROOMS");

        when(dataScopeAssignmentMapper.selectList(any())).thenReturn(Arrays.asList(assignment1, assignment2));

        // When
        DataScopeContext context = dataScopeService.resolveDataScope(3L);

        // Then
        assertThat(context).isNotNull();
        assertThat(context.getUserId()).isEqualTo(3L);
        assertThat(context.getRole()).isEqualTo("OPERATOR");
        assertThat(context.getScopeType()).isEqualTo("ROOMS");
        assertThat(context.getOrganizationIds()).containsExactly(1L);
        assertThat(context.getCloudRoomIds()).containsExactlyInAnyOrder(10L, 20L);
        assertThat(context.isOperator()).isTrue();
        assertThat(context.hasWritePermission()).isTrue();
    }

    @Test
    @DisplayName("VIEWER 无机房授权应返回 READ_ONLY 范围类型")
    void resolveDataScope_ViewerReadOnly() {
        // Given
        when(sysUserService.getUserOrThrow(4L)).thenReturn(viewer);
        when(dataScopeAssignmentMapper.selectList(any())).thenReturn(Collections.emptyList());

        // When
        DataScopeContext context = dataScopeService.resolveDataScope(4L);

        // Then
        assertThat(context).isNotNull();
        assertThat(context.getUserId()).isEqualTo(4L);
        assertThat(context.getRole()).isEqualTo("VIEWER");
        assertThat(context.getScopeType()).isEqualTo("READ_ONLY");
        assertThat(context.getOrganizationIds()).isEmpty();
        assertThat(context.getCloudRoomIds()).isEmpty();
        assertThat(context.isViewer()).isTrue();
        assertThat(context.hasWritePermission()).isFalse();
    }

    @Test
    @DisplayName("hasOrganizationAccess - PLATFORM_ADMIN 可访问任意组织")
    void hasOrganizationAccess_PlatformAdmin() {
        // Given
        when(sysUserService.getUserOrThrow(1L)).thenReturn(platformAdmin);

        // When
        boolean canAccess = dataScopeService.canAccessOrganization(1L, 999L);

        // Then
        assertThat(canAccess).isTrue();
    }

    @Test
    @DisplayName("hasOrganizationAccess - ORG_ADMIN 仅可访问授权组织")
    void hasOrganizationAccess_OrgAdmin() {
        // Given
        when(sysUserService.getUserOrThrow(2L)).thenReturn(orgAdmin);

        DataScopeAssignment assignment = new DataScopeAssignment();
        assignment.setUserId(2L);
        assignment.setOrganizationId(1L);
        assignment.setScopeType("ORG_ALL");

        when(dataScopeAssignmentMapper.selectList(any())).thenReturn(Collections.singletonList(assignment));

        // When
        boolean canAccess1 = dataScopeService.canAccessOrganization(2L, 1L);
        boolean canAccess999 = dataScopeService.canAccessOrganization(2L, 999L);

        // Then
        assertThat(canAccess1).isTrue();
        assertThat(canAccess999).isFalse();
    }

    @Test
    @DisplayName("hasCloudRoomAccess - OPERATOR 仅可访问授权机房")
    void hasCloudRoomAccess_Operator() {
        // Given
        when(sysUserService.getUserOrThrow(3L)).thenReturn(operator);

        DataScopeAssignment assignment = new DataScopeAssignment();
        assignment.setUserId(3L);
        assignment.setOrganizationId(1L);
        assignment.setCloudRoomId(10L);
        assignment.setScopeType("ROOMS");

        when(dataScopeAssignmentMapper.selectList(any())).thenReturn(Collections.singletonList(assignment));

        // When
        boolean canAccess10 = dataScopeService.canAccessCloudRoom(3L, 10L);
        boolean canAccess999 = dataScopeService.canAccessCloudRoom(3L, 999L);

        // Then
        assertThat(canAccess10).isTrue();
        assertThat(canAccess999).isFalse();
    }

    @Test
    @DisplayName("hasWritePermission - READ_ONLY 范围无写权限")
    void hasWritePermission_ReadOnly() {
        // Given
        when(sysUserService.getUserOrThrow(4L)).thenReturn(viewer);
        when(dataScopeAssignmentMapper.selectList(any())).thenReturn(Collections.emptyList());

        // When
        boolean hasWrite = dataScopeService.hasWritePermission(4L);

        // Then
        assertThat(hasWrite).isFalse();
    }
}
