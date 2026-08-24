package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.entity.DataScopeAssignment;
import com.example.platform.entity.OrganizationMembership;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.DataScopeAssignmentMapper;
import com.example.platform.mapper.OrganizationMembershipMapper;
import com.example.platform.security.PlatformRole;
import com.example.platform.service.AlarmRecipientResolver;
import com.example.platform.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * 类功能:告警接收人解析服务实现，根据组织ID和机房ID从数据范围分配表中匹配有权限的启用用户作为告警接收人
 * */
@Service
@RequiredArgsConstructor
public class AlarmRecipientResolverImpl implements AlarmRecipientResolver {

    private static final int USER_STATUS_ENABLED = 0; // 用户启用状态
    private static final Set<String> EXPLICIT_ROOM_SCOPE_TYPES = Set.of("ROOMS", "READ_ONLY"); // 精确机房级范围类型
    private static final Set<String> RECIPIENT_ROLES = Set.of( // 可接收告警的角色
            PlatformRole.ORG_ADMIN,
            PlatformRole.OPERATOR,
            PlatformRole.VIEWER);

    private final DataScopeAssignmentMapper dataScopeAssignmentMapper;
    private final OrganizationMembershipMapper membershipMapper;
    private final SysUserService sysUserService;

    /*
     * 方法功能:解析指定组织和机房下可接收告警的用户ID集合
     * 参数:organizationId-组织ID, cloudRoomId-机房ID
     * 返回数据:Set<Long>-符合角色和状态条件的用户ID集合，无匹配时返回空集
     * */
    @Override
    public Set<Long> resolveRecipientUserIds(Long organizationId, Long cloudRoomId) {
        if (organizationId == null || cloudRoomId == null) {
            return Set.of();
        }

        LambdaQueryWrapper<DataScopeAssignment> assignmentQuery = new LambdaQueryWrapper<>();
        assignmentQuery.eq(DataScopeAssignment::getOrganizationId, organizationId)
                .and(scope -> scope
                        .and(orgAll -> orgAll.eq(DataScopeAssignment::getScopeType, "ORG_ALL")
                                .isNull(DataScopeAssignment::getCloudRoomId))
                        .or(explicit -> explicit.eq(DataScopeAssignment::getCloudRoomId, cloudRoomId)
                                .in(DataScopeAssignment::getScopeType, EXPLICIT_ROOM_SCOPE_TYPES)));
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

        // 筛选：接收人必须在目标组织有活动成员关系（成员移除后立即失效）
        Set<Long> activeMemberIds = membershipMapper.selectActiveByOrganization(organizationId)
                .stream().map(OrganizationMembership::getUserId)
                .collect(Collectors.toSet());
        recipientUserIds.retainAll(activeMemberIds);

        return recipientUserIds;
    }

    private boolean isExactRoomAssignment(DataScopeAssignment assignment,
                                          Long organizationId,
                                          Long cloudRoomId) {
        return assignment != null
                && assignment.getUserId() != null
                && organizationId.equals(assignment.getOrganizationId())
                && (("ORG_ALL".equals(assignment.getScopeType())
                        && assignment.getCloudRoomId() == null)
                    || (cloudRoomId.equals(assignment.getCloudRoomId())
                        && EXPLICIT_ROOM_SCOPE_TYPES.contains(assignment.getScopeType())));
    }
}
