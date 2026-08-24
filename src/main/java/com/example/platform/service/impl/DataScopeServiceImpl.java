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
        if (user != null && PlatformRole.PLATFORM_ADMIN.equals(user.getPlatformRole())) {
            return resolvePlatformScope(userId);
        }
        return resolveDataScope(userId, organizationContext.requireOrganizationId());
    }

    @Override
    public DataScopeContext resolveDataScope(Long userId, Long organizationId) {
        SysUser user = sysUserService.getUserOrThrow(userId);
        if (user != null && PlatformRole.PLATFORM_ADMIN.equals(user.getPlatformRole())) {
            throw new DataScopeViolationException("平台管理员不能使用组织数据范围");
        }
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

        // 范围类型以实际分配为准，不再按角色猜测；空分配按角色取安全默认（fail-closed）。
        String scopeType = resolveScopeType(role, assignments);
        Set<Long> cloudRoomIds = new HashSet<>();
        for (DataScopeAssignment assignment : assignments) {
            if ("ROOMS".equals(scopeType) && assignment.getCloudRoomId() != null) {
                cloudRoomIds.add(assignment.getCloudRoomId());
            }
        }
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

    private String resolveScopeType(String role, List<DataScopeAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            // 无明确分配时按角色取安全默认（fail-closed）
            if (PlatformRole.ORG_ADMIN.equals(role)) {
                return "ORG_ALL";
            }
            return "READ_ONLY";
        }
        // 取第一条非空 scopeType（同组织内分配应一致；不一致时取最宽松）
        boolean hasWritable = false;
        boolean hasReadOnly = false;
        boolean hasOrgAll = false;
        for (DataScopeAssignment a : assignments) {
            if (a.getScopeType() == null) continue;
            String st = a.getScopeType().trim().toUpperCase();
            if ("ORG_ALL".equals(st)) {
                hasOrgAll = true;
            } else if ("ROOMS".equals(st)) {
                hasWritable = true;
            } else if ("READ_ONLY".equals(st)) {
                hasReadOnly = true;
            }
        }
        // 优先使用 ORG_ALL（组织管理员范围）；ROOMS 次之（运维机房范围）；READ_ONLY 兜底
        if (hasOrgAll) return "ORG_ALL";
        if (hasWritable) return "ROOMS";
        if (hasReadOnly) return "READ_ONLY";
        // 兼容：只有 cloudRoomId 但无 scopeType 的旧分配 → 按角色推导
        if (PlatformRole.ORG_ADMIN.equals(role)) return "ORG_ALL";
        return "ROOMS";
    }
}
