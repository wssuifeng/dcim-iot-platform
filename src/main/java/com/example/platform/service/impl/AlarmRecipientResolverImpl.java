package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.entity.DataScopeAssignment;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.DataScopeAssignmentMapper;
import com.example.platform.security.PlatformRole;
import com.example.platform.service.AlarmRecipientResolver;
import com.example.platform.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AlarmRecipientResolverImpl implements AlarmRecipientResolver {

    private static final int USER_STATUS_ENABLED = 0;
    private static final Set<String> EXPLICIT_ROOM_SCOPE_TYPES = Set.of("ROOMS", "READ_ONLY");
    private static final Set<String> RECIPIENT_ROLES = Set.of(
            PlatformRole.ORG_ADMIN,
            PlatformRole.OPERATOR,
            PlatformRole.VIEWER);

    private final DataScopeAssignmentMapper dataScopeAssignmentMapper;
    private final SysUserService sysUserService;

    @Override
    public Set<Long> resolveRecipientUserIds(Long organizationId, Long cloudRoomId) {
        if (organizationId == null || cloudRoomId == null) {
            return Set.of();
        }

        LambdaQueryWrapper<DataScopeAssignment> assignmentQuery = new LambdaQueryWrapper<>();
        assignmentQuery.eq(DataScopeAssignment::getOrganizationId, organizationId)
                .eq(DataScopeAssignment::getCloudRoomId, cloudRoomId)
                .in(DataScopeAssignment::getScopeType, EXPLICIT_ROOM_SCOPE_TYPES);
        List<DataScopeAssignment> assignments = dataScopeAssignmentMapper.selectList(assignmentQuery);

        Set<Long> candidateUserIds = new LinkedHashSet<>();
        for (DataScopeAssignment assignment : assignments) {
            if (isExactRoomAssignment(assignment, organizationId, cloudRoomId)) {
                candidateUserIds.add(assignment.getUserId());
            }
        }
        candidateUserIds.remove(null);
        if (candidateUserIds.isEmpty()) {
            return Set.of();
        }

        LambdaQueryWrapper<SysUser> userQuery = new LambdaQueryWrapper<>();
        userQuery.in(SysUser::getId, candidateUserIds)
                .eq(SysUser::getStatus, USER_STATUS_ENABLED)
                .in(SysUser::getRole, RECIPIENT_ROLES);
        List<SysUser> users = sysUserService.list(userQuery);

        Set<Long> recipientUserIds = new LinkedHashSet<>();
        for (SysUser user : users) {
            if (user.getId() != null
                    && candidateUserIds.contains(user.getId())
                    && Integer.valueOf(USER_STATUS_ENABLED).equals(user.getStatus())
                    && RECIPIENT_ROLES.contains(user.getRole())) {
                recipientUserIds.add(user.getId());
            }
        }
        return recipientUserIds;
    }

    private boolean isExactRoomAssignment(DataScopeAssignment assignment,
                                          Long organizationId,
                                          Long cloudRoomId) {
        return assignment != null
                && assignment.getUserId() != null
                && organizationId.equals(assignment.getOrganizationId())
                && cloudRoomId.equals(assignment.getCloudRoomId())
                && EXPLICIT_ROOM_SCOPE_TYPES.contains(assignment.getScopeType());
    }
}
