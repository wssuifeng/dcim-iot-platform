package com.example.platform.service.impl;

import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.DataScopeAssignment;
import com.example.platform.entity.OrganizationMembership;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.DataScopeAssignmentMapper;
import com.example.platform.mapper.OrganizationMembershipMapper;
import com.example.platform.security.CurrentOrganizationContext;
import com.example.platform.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataScopeServiceTest {
    @Mock
    private SysUserService sysUserService;
    @Mock
    private DataScopeAssignmentMapper dataScopeAssignmentMapper;
    @Mock
    private OrganizationMembershipMapper membershipMapper;
    @Mock
    private CurrentOrganizationContext organizationContext;
    @InjectMocks
    private DataScopeServiceImpl service;

    @Test
    void platformScopeDoesNotGrantOrganizationAccess() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setPlatformRole("PLATFORM_ADMIN");
        when(sysUserService.getUserOrThrow(1L)).thenReturn(user);

        DataScopeContext context = service.resolvePlatformScope(1L);

        assertThat(context.isPlatformAdmin()).isTrue();
        assertThat(context.hasOrganizationAccess(99L)).isFalse();
    }

    @Test
    void orgAdminReceivesOnlySelectedOrganization() {
        when(membershipMapper.selectActiveByUserAndOrganization(7L, 10L))
                .thenReturn(membership(7L, 10L, "ORG_ADMIN"));
        when(dataScopeAssignmentMapper.selectList(any())).thenReturn(List.of());

        DataScopeContext context = service.resolveDataScope(7L, 10L);

        assertThat(context.getRole()).isEqualTo("ORG_ADMIN");
        assertThat(context.getScopeType()).isEqualTo("ORG_ALL");
        assertThat(context.getOrganizationIds()).containsExactly(10L);
    }

    @Test
    void operatorReceivesOnlyRoomsFromSelectedOrganizationQuery() {
        DataScopeAssignment assignment = new DataScopeAssignment();
        assignment.setOrganizationId(10L);
        assignment.setCloudRoomId(100L);
        when(membershipMapper.selectActiveByUserAndOrganization(7L, 10L))
                .thenReturn(membership(7L, 10L, "OPERATOR"));
        when(dataScopeAssignmentMapper.selectList(any())).thenReturn(List.of(assignment));

        DataScopeContext context = service.resolveDataScope(7L, 10L);

        assertThat(context.getScopeType()).isEqualTo("ROOMS");
        assertThat(context.getCloudRoomIds()).containsExactly(100L);
    }

    @Test
    void inactiveMembershipForSelectedOrganizationIsRejected() {
        when(membershipMapper.selectActiveByUserAndOrganization(7L, 20L)).thenReturn(null);

        assertThatThrownBy(() -> service.resolveDataScope(7L, 20L))
                .isInstanceOf(DataScopeViolationException.class);
    }

    private OrganizationMembership membership(Long userId, Long orgId, String role) {
        OrganizationMembership membership = new OrganizationMembership();
        membership.setUserId(userId);
        membership.setOrganizationId(orgId);
        membership.setMembershipRole(role);
        membership.setStatus("ACTIVE");
        return membership;
    }
}
