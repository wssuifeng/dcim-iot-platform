package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.entity.DataScopeAssignment;
import com.example.platform.entity.OrganizationMembership;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.DataScopeAssignmentMapper;
import com.example.platform.mapper.OrganizationMembershipMapper;
import com.example.platform.service.SysUserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlarmRecipientResolverImplTest {

    private static final Long ORGANIZATION_ID = 10L;
    private static final Long CLOUD_ROOM_ID = 100L;

    @Mock
    private DataScopeAssignmentMapper dataScopeAssignmentMapper;
    @Mock
    private OrganizationMembershipMapper membershipMapper;
    @Mock
    private SysUserService sysUserService;

    @InjectMocks
    private AlarmRecipientResolverImpl resolver;

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, DataScopeAssignment.class);
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @Test
    void shouldFailClosedWhenOrganizationOrRoomIsMissing() {
        assertThat(resolver.resolveRecipientUserIds(null, CLOUD_ROOM_ID)).isEmpty();
        assertThat(resolver.resolveRecipientUserIds(ORGANIZATION_ID, null)).isEmpty();

        verify(dataScopeAssignmentMapper, never()).selectList(any());
        verify(sysUserService, never()).list(any(LambdaQueryWrapper.class));
    }

    @Test
    void shouldReturnOnlyEnabledBusinessUsersExplicitlyAssignedToExactRoom() {
        when(dataScopeAssignmentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                assignment(1L, ORGANIZATION_ID, CLOUD_ROOM_ID, "ROOMS"),
                assignment(1L, ORGANIZATION_ID, CLOUD_ROOM_ID, "ROOMS"),
                assignment(2L, ORGANIZATION_ID, CLOUD_ROOM_ID, "READ_ONLY"),
                assignment(3L, ORGANIZATION_ID, CLOUD_ROOM_ID, "ROOMS"),
                assignment(4L, ORGANIZATION_ID, CLOUD_ROOM_ID, "ROOMS"),
                assignment(5L, ORGANIZATION_ID, CLOUD_ROOM_ID, "ROOMS"),
                assignment(6L, ORGANIZATION_ID, CLOUD_ROOM_ID, "ORG_ALL"),
                assignment(7L, 999L, CLOUD_ROOM_ID, "ROOMS"),
                assignment(8L, ORGANIZATION_ID, 999L, "ROOMS")
        ));
        when(membershipMapper.selectActiveByOrganization(ORGANIZATION_ID))
                .thenReturn(List.of(
                        activeMembership(1L), activeMembership(2L), activeMembership(3L),
                        activeMembership(4L), activeMembership(5L), activeMembership(6L),
                        activeMembership(8L)));
        when(sysUserService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                user(1L, "ORG_ADMIN", 0),
                user(2L, "OPERATOR", 0),
                user(3L, "VIEWER", 0),
                user(4L, "PLATFORM_ADMIN", 0),
                user(5L, "VIEWER", 1),
                user(6L, "VIEWER", 0),
                user(7L, "VIEWER", 0),
                user(8L, "VIEWER", 0)
        ));

        Set<Long> recipients = resolver.resolveRecipientUserIds(ORGANIZATION_ID, CLOUD_ROOM_ID);

        assertThat(recipients).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void shouldSkipUserLookupWhenNoExplicitRoomAssignmentExists() {
        when(dataScopeAssignmentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThat(resolver.resolveRecipientUserIds(ORGANIZATION_ID, CLOUD_ROOM_ID)).isEmpty();

        verify(sysUserService, never()).list(any(LambdaQueryWrapper.class));
    }

    private DataScopeAssignment assignment(Long userId, Long organizationId, Long cloudRoomId,
                                           String scopeType) {
        DataScopeAssignment assignment = new DataScopeAssignment();
        assignment.setUserId(userId);
        assignment.setOrganizationId(organizationId);
        assignment.setCloudRoomId(cloudRoomId);
        assignment.setScopeType(scopeType);
        return assignment;
    }

    private OrganizationMembership activeMembership(Long userId) {
        OrganizationMembership membership = new OrganizationMembership();
        membership.setUserId(userId);
        membership.setOrganizationId(ORGANIZATION_ID);
        membership.setMembershipRole("VIEWER");
        membership.setStatus("ACTIVE");
        return membership;
    }

    private SysUser user(Long id, String role, Integer status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
