package com.example.platform.service.impl;

import com.example.platform.common.exception.MembershipConflictException;
import com.example.platform.entity.OrganizationMembership;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.OrganizationMembershipMapper;
import com.example.platform.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationMembershipServiceImplTest {
    @Mock
    private OrganizationMembershipMapper membershipMapper;
    @Mock
    private SysUserMapper userMapper;
    @InjectMocks
    private OrganizationMembershipServiceImpl service;

    @Test
    void promotingToOrgAdminRejectsAnyOtherActiveMembership() {
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(user(7L));
        when(membershipMapper.selectByUserAndOrganizationForUpdate(7L, 10L))
                .thenReturn(membership(7L, 10L, "VIEWER"));
        when(membershipMapper.countOtherActiveMemberships(7L, 10L)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(7L, 10L, "ORG_ADMIN", 1L))
                .isInstanceOf(MembershipConflictException.class)
                .hasMessageContaining("不能同时属于其他组织");

        verify(membershipMapper, never()).updateRole(7L, "ORG_ADMIN", 1L);
    }

    @Test
    void removingLastOrgAdminIsRejected() {
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(user(7L));
        when(membershipMapper.selectByUserAndOrganizationForUpdate(7L, 10L))
                .thenReturn(membership(7L, 10L, "ORG_ADMIN"));
        when(membershipMapper.selectActiveOrgAdminIdsForUpdate(10L)).thenReturn(List.of(7L));

        assertThatThrownBy(() -> service.removeMembership(7L, 10L, 1L))
                .isInstanceOf(MembershipConflictException.class)
                .hasMessageContaining("管理员交接");
    }

    @Test
    void ordinaryMembershipIsRejectedWhenUserIsAlreadyAdministrator() {
        when(userMapper.selectByIdForUpdate(7L)).thenReturn(user(7L));
        when(membershipMapper.countActiveAdminMemberships(7L)).thenReturn(1L);

        assertThatThrownBy(() -> service.activateMembership(7L, 20L, "VIEWER", 1L))
                .isInstanceOf(MembershipConflictException.class)
                .hasMessageContaining("不能加入其他组织");
    }

    @Test
    void platformAdministratorCannotBeActivatedAsOrganizationMember() {
        SysUser platformAdmin = user(1L);
        platformAdmin.setPlatformRole("PLATFORM_ADMIN");
        when(userMapper.selectByIdForUpdate(1L)).thenReturn(platformAdmin);

        assertThatThrownBy(() -> service.activateMembership(1L, 10L, "ORG_ADMIN", 2L))
                .isInstanceOf(MembershipConflictException.class)
                .hasMessageContaining("平台管理员不能成为组织成员");

        verify(membershipMapper, never()).insert(org.mockito.ArgumentMatchers.any(OrganizationMembership.class));
        verify(membershipMapper, never()).updateById(org.mockito.ArgumentMatchers.any(OrganizationMembership.class));
    }

    private SysUser user(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        return user;
    }

    private OrganizationMembership membership(Long userId, Long orgId, String role) {
        OrganizationMembership membership = new OrganizationMembership();
        membership.setId(userId);
        membership.setUserId(userId);
        membership.setOrganizationId(orgId);
        membership.setMembershipRole(role);
        membership.setStatus("ACTIVE");
        return membership;
    }
}
