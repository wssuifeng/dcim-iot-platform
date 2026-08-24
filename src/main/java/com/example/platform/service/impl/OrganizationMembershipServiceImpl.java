package com.example.platform.service.impl;

import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.common.exception.MembershipConflictException;
import com.example.platform.entity.OrganizationMembership;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.OrganizationMembershipMapper;
import com.example.platform.mapper.SysUserMapper;
import com.example.platform.security.PlatformRole;
import com.example.platform.service.OrganizationMembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrganizationMembershipServiceImpl implements OrganizationMembershipService {
    private static final Set<String> MEMBERSHIP_ROLES = Set.of("ORG_ADMIN", "OPERATOR", "VIEWER");

    private final OrganizationMembershipMapper membershipMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public OrganizationMembership requireActiveMembership(Long userId, Long organizationId) {
        OrganizationMembership membership = membershipMapper
                .selectActiveByUserAndOrganization(userId, organizationId);
        if (membership == null) {
            throw new DataScopeViolationException("Organization", String.valueOf(organizationId));
        }
        return membership;
    }

    @Override
    public List<OrganizationMembership> listActiveMemberships(Long userId) {
        return membershipMapper.selectActiveByUser(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateMembership(Long userId, Long organizationId, String role, Long approverId) {
        SysUser user = lockUser(userId);
        rejectPlatformAdministrator(user);
        String normalizedRole = normalizeRole(role);
        validateRoleCompatibility(userId, organizationId, normalizedRole);

        OrganizationMembership membership = membershipMapper
                .selectByUserAndOrganizationForUpdate(userId, organizationId);
        if (membership == null) {
            membership = new OrganizationMembership();
            membership.setUserId(userId);
            membership.setOrganizationId(organizationId);
        } else if ("ACTIVE".equals(membership.getStatus())) {
            throw new MembershipConflictException("用户已是该组织的活动成员");
        }
        membership.setMembershipRole(normalizedRole);
        membership.setStatus("ACTIVE");
        membership.setApprovedBy(approverId);
        membership.setApprovedTime(LocalDateTime.now());
        if (membership.getId() == null) {
            membershipMapper.insert(membership);
        } else {
            membershipMapper.updateById(membership);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeRole(Long userId, Long organizationId, String role, Long operatorId) {
        SysUser user = lockUser(userId);
        rejectPlatformAdministrator(user);
        String normalizedRole = normalizeRole(role);
        OrganizationMembership membership = requireMembershipForUpdate(userId, organizationId);
        validateRoleCompatibility(userId, organizationId, normalizedRole);
        protectLastAdministrator(membership, normalizedRole);
        membershipMapper.updateRole(membership.getId(), normalizedRole, operatorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMembership(Long userId, Long organizationId, Long operatorId) {
        SysUser user = lockUser(userId);
        rejectPlatformAdministrator(user);
        OrganizationMembership membership = requireMembershipForUpdate(userId, organizationId);
        protectLastAdministrator(membership, null);
        membership.setStatus("REMOVED");
        membership.setApprovedBy(operatorId);
        membership.setApprovedTime(LocalDateTime.now());
        membershipMapper.updateById(membership);
    }

    private void validateRoleCompatibility(Long userId, Long organizationId, String role) {
        if ("ORG_ADMIN".equals(role)) {
            if (membershipMapper.countOtherActiveMemberships(userId, organizationId) > 0) {
                throw new MembershipConflictException("组织管理员不能同时属于其他组织");
            }
            return;
        }
        if (membershipMapper.countActiveAdminMemberships(userId) > 0) {
            throw new MembershipConflictException("组织管理员不能加入其他组织");
        }
    }

    private void protectLastAdministrator(OrganizationMembership membership, String targetRole) {
        if (!"ORG_ADMIN".equals(membership.getMembershipRole()) || "ORG_ADMIN".equals(targetRole)) {
            return;
        }
        List<Long> adminIds = membershipMapper
                .selectActiveOrgAdminIdsForUpdate(membership.getOrganizationId());
        if (adminIds.size() <= 1) {
            throw MembershipConflictException.lastAdministrator();
        }
    }

    private OrganizationMembership requireMembershipForUpdate(Long userId, Long organizationId) {
        OrganizationMembership membership = membershipMapper
                .selectByUserAndOrganizationForUpdate(userId, organizationId);
        if (membership == null || !"ACTIVE".equals(membership.getStatus())) {
            throw new MembershipConflictException("用户不是该组织的活动成员");
        }
        return membership;
    }

    private SysUser lockUser(Long userId) {
        SysUser user = sysUserMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new MembershipConflictException("用户不存在");
        }
        return user;
    }

    private void rejectPlatformAdministrator(SysUser user) {
        if (PlatformRole.PLATFORM_ADMIN.equals(user.getPlatformRole())) {
            throw new MembershipConflictException("平台管理员不能成为组织成员");
        }
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!MEMBERSHIP_ROLES.contains(normalized)) {
            throw new MembershipConflictException("组织成员角色不合法");
        }
        return normalized;
    }
}
