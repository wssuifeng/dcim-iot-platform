package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.DataScopeAssignment;
import com.example.platform.entity.OrganizationMembership;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.DataScopeAssignmentMapper;
import com.example.platform.mapper.OrganizationMembershipMapper;
import com.example.platform.security.CurrentOrganizationContext;
import com.example.platform.security.PlatformRole;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DataScopeServiceImpl implements DataScopeService {
    private final SysUserService sysUserService;
    private final DataScopeAssignmentMapper dataScopeAssignmentMapper;
    private final OrganizationMembershipMapper membershipMapper;
    private final CurrentOrganizationContext organizationContext;

    @Override
    public DataScopeContext resolveDataScope(Long userId) {
        SysUser user = sysUserService.getUserOrThrow(userId);
        if (PlatformRole.PLATFORM_ADMIN.equals(user.getPlatformRole())
                && organizationContext.currentOrganizationId().isEmpty()) {
            return resolvePlatformScope(userId);
        }
        return resolveDataScope(userId, organizationContext.requireOrganizationId());
    }

    @Override
    public DataScopeContext resolveDataScope(Long userId, Long organizationId) {
        OrganizationMembership membership = membershipMapper
                .selectActiveByUserAndOrganization(userId, organizationId);
        if (membership == null) {
            throw new DataScopeViolationException("Organization", String.valueOf(organizationId));
        }

        String role = membership.getMembershipRole();
        List<DataScopeAssignment> assignments = dataScopeAssignmentMapper.selectList(
                new LambdaQueryWrapper<DataScopeAssignment>()
                        .eq(DataScopeAssignment::getUserId, userId)
                        .eq(DataScopeAssignment::getOrganizationId, organizationId));
        Set<Long> cloudRoomIds = new HashSet<>();
        for (DataScopeAssignment assignment : assignments) {
            if (assignment.getCloudRoomId() != null) {
                cloudRoomIds.add(assignment.getCloudRoomId());
            }
        }
        String scopeType = inferScopeType(role, cloudRoomIds);
        return DataScopeContext.builder()
                .userId(userId)
                .role(role)
                .scopeType(scopeType)
                .organizationIds(Set.of(organizationId))
                .cloudRoomIds(cloudRoomIds)
                .build();
    }

    @Override
    public DataScopeContext resolvePlatformScope(Long userId) {
        SysUser user = sysUserService.getUserOrThrow(userId);
        if (!PlatformRole.PLATFORM_ADMIN.equals(user.getPlatformRole())) {
            throw new DataScopeViolationException("当前账号不是平台管理员");
        }
        return DataScopeContext.builder()
                .userId(userId)
                .role(PlatformRole.PLATFORM_ADMIN)
                .scopeType("PLATFORM")
                .organizationIds(Set.of())
                .cloudRoomIds(Set.of())
                .build();
    }

    @Override
    public boolean canAccessOrganization(Long userId, Long organizationId) {
        try {
            resolveDataScope(userId, organizationId);
            return true;
        } catch (DataScopeViolationException e) {
            return false;
        }
    }

    @Override
    public boolean canAccessCloudRoom(Long userId, Long cloudRoomId) {
        return resolveDataScope(userId).hasCloudRoomAccess(cloudRoomId);
    }

    @Override
    public boolean hasWritePermission(Long userId) {
        return resolveDataScope(userId).hasWritePermission();
    }

    private String inferScopeType(String role, Set<Long> cloudRoomIds) {
        if (PlatformRole.ORG_ADMIN.equals(role)) {
            return "ORG_ALL";
        }
        if (PlatformRole.OPERATOR.equals(role)) {
            return cloudRoomIds.isEmpty() ? "READ_ONLY" : "ROOMS";
        }
        return "READ_ONLY";
    }
}
