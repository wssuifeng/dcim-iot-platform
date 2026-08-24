package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.DataScopeAssignmentDTO;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.CloudRoom;
import com.example.platform.entity.DataScopeAssignment;
import com.example.platform.entity.OrganizationMembership;
import com.example.platform.mapper.CloudRoomMapper;
import com.example.platform.mapper.DataScopeAssignmentMapper;
import com.example.platform.mapper.OrganizationMembershipMapper;
import com.example.platform.security.PlatformRole;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.SysUserService;
import com.example.platform.service.UserDataScopeService;
import com.example.platform.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/*
 * 类功能:用户数据范围分配服务实现类
 *
 * 共享契约³: scopeType 集合 PLATFORM/ORG_ALL/ROOMS/READ_ONLY；数据范围是后端服务层安全边界
 *
 * 【校验矩阵（向 targetUserId 写入/移除范围时）】
 *   - PLATFORM_ADMIN 当前用户：可分配任意 scopeType（含 PLATFORM），管理任意目标用户
 *   - ORG_ADMIN 当前用户：拒绝 PLATFORM scopeType；可管辖组织 ⊇ dto.organizationId；
 *       若 scopeType=ROOMS，cloudRoomId 必须存在且归属 dto.organizationId
 *   - OPERATOR/VIEWER 当前用户：拒绝（Controller 层 @PreAuthorize 兜底）
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataScopeServiceImpl implements UserDataScopeService {

    private static final Set<String> VALID_SCOPE_TYPES = Set.of("PLATFORM", "ORG_ALL", "ROOMS", "READ_ONLY");

    private final DataScopeService dataScopeService;
    private final SysUserService sysUserService;
    private final DataScopeAssignmentMapper dataScopeAssignmentMapper;
    private final CloudRoomMapper cloudRoomMapper;
    private final OrganizationMembershipMapper membershipMapper;

    @Override
    public List<DataScopeAssignment> listAssignments(Long targetUserId, Long currentUserId) {
        sysUserService.getUserOrThrow(targetUserId);
        LambdaQueryWrapper<DataScopeAssignment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataScopeAssignment::getUserId, targetUserId);
        wrapper.orderByDesc(DataScopeAssignment::getCreateTime);

        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        if (context.isPlatformAdmin()) {
            return dataScopeAssignmentMapper.selectList(wrapper);
        }
        // ORG_ADMIN 仅可见自身组织内的分配
        Set<Long> orgIds = context.getOrganizationIds();
        if (orgIds.isEmpty()) {
            return List.of();
        }
        wrapper.in(DataScopeAssignment::getOrganizationId, orgIds);
        return dataScopeAssignmentMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataScopeAssignment assignScope(Long targetUserId, DataScopeAssignmentDTO dto, Long currentUserId) {
        // 1. 目标用户存在性校验
        SysUser targetUser = sysUserService.getUserOrThrow(targetUserId);

        // 2. scopeType 合法性校验
        String scopeType = validateScopeType(dto.getScopeType());
        Long organizationId = dto.getOrganizationId();
        Long cloudRoomId = dto.getCloudRoomId();

        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        boolean callerIsPlatformAdmin = context.isPlatformAdmin();

        // 3. ORG_ADMIN 不可分配 PLATFORM 范围
        if ("PLATFORM".equals(scopeType) && !callerIsPlatformAdmin) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "仅平台管理员可分配 PLATFORM 范围");
        }

        // 4. 校验目标用户的角色与待分配范围不冲突（PLATFORM 范围只能分配给 PLATFORM_ADMIN 用户）
        boolean targetIsPlatformAdmin = PlatformRole.PLATFORM_ADMIN.equals(targetUser.getPlatformRole());
        if ("PLATFORM".equals(scopeType) && !targetIsPlatformAdmin) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "PLATFORM 范围只能分配给平台管理员用户");
        }

        // 4b. 非 PLATFORM 范围要求目标用户是目标组织的活动成员
        if (!"PLATFORM".equals(scopeType)) {
            if (organizationId == null) {
                throw new BusinessException(ResultCode.INVALID_PARAMETER,
                        "非 PLATFORM 范围必须指定组织");
            }
            OrganizationMembership membership = membershipMapper
                    .selectActiveByUserAndOrganization(targetUserId, organizationId);
            if (membership == null) {
                throw new BusinessException(ResultCode.INVALID_PARAMETER,
                        "目标用户不是该组织的活动成员");
            }
        }

        // 4c. ORG_ALL 只允许分配给组织管理员角色
        if ("ORG_ALL".equals(scopeType) && !targetIsPlatformAdmin) {
            String targetRole = targetUser.getNormalizedRole();
            if (!PlatformRole.ORG_ADMIN.equals(targetRole)) {
                throw new BusinessException(ResultCode.INVALID_PARAMETER,
                        "ORG_ALL 范围只能分配给组织管理员");
            }
        }

        // 5. 校验 org/room 结构对齐（不含 mapper 调用，避免越权时泄露未授权数据）
        validateScopeStructure(scopeType, organizationId, cloudRoomId);

        // 6. 范围管辖校验：非 PLATFORM_ADMIN 必须对该 organizationId 有访问权限（先校验再读机房）
        if (!callerIsPlatformAdmin) {
            if (organizationId == null) {
                throw new BusinessException(ResultCode.INVALID_PARAMETER,
                        "非平台管理员分配范围必须指定组织");
            }
            if (!context.hasOrganizationAccess(organizationId)) {
                throw new DataScopeViolationException("Organization", String.valueOf(organizationId));
            }
            // 不在此处直接校验 cloudRoomId 是否在授权机房集合内
            // 原因：ORG_ADMIN 通常为 ORG_ALL 范围（cloudRoomIds 为空），机房授权隐含在组织层
            // 云机房归组织一致性由步骤 7 的 ensureCloudRoomBelongsToOrg 兜底
        }

        // 7. ROOMS 范围校验：cloudRoomId 必须归属 organizationId
        if ("ROOMS".equals(scopeType) && cloudRoomId != null) {
            ensureCloudRoomBelongsToOrg(cloudRoomId, organizationId);
        }
        if ("READ_ONLY".equals(scopeType) && cloudRoomId != null) {
            ensureCloudRoomBelongsToOrg(cloudRoomId, organizationId);
        }

        // 8. 重复分配幂等校验（避免同一用户相同 scope 重复分配）
        if (assignmentExists(targetUserId, scopeType, organizationId, cloudRoomId)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "该数据范围已分配给用户 " + targetUserId);
        }

        DataScopeAssignment assignment = new DataScopeAssignment();
        assignment.setUserId(targetUserId);
        assignment.setOrganizationId(organizationId);
        assignment.setCloudRoomId(cloudRoomId);
        assignment.setScopeType(scopeType);
        if (dataScopeAssignmentMapper.insert(assignment) <= 0) {
            throw new BusinessException("数据范围分配写入失败");
        }
        log.info("数据范围分配成功 userId={} scopeType={} orgId={} roomId={}",
                targetUserId, scopeType, organizationId, cloudRoomId);
        return assignment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeAssignment(Long targetUserId, Long assignmentId, Long currentUserId) {
        sysUserService.getUserOrThrow(targetUserId);
        DataScopeAssignment assignment = dataScopeAssignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND,
                    "数据范围分配记录不存在: " + assignmentId);
        }
        if (!targetUserId.equals(assignment.getUserId())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "数据范围分配记录不属于目标用户 " + targetUserId);
        }
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        if (!context.isPlatformAdmin()) {
            // 非平台管理员只能移除自身组织内的分配
            Set<Long> orgIds = context.getOrganizationIds();
            if (orgIds.isEmpty()
                    || assignment.getOrganizationId() == null
                    || !orgIds.contains(assignment.getOrganizationId())) {
                throw new DataScopeViolationException("DataScopeAssignment", String.valueOf(assignmentId));
            }
        }
        if (dataScopeAssignmentMapper.deleteById(assignmentId) <= 0) {
            throw new BusinessException("数据范围分配记录删除失败");
        }
        log.info("数据范围移除 userId={} assignmentId={} scopeType={}",
                targetUserId, assignmentId, assignment.getScopeType());
    }

    private String validateScopeType(String scopeType) {
        if (!StringUtils.hasText(scopeType)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "范围类型不能为空");
        }
        String trimmed = scopeType.trim().toUpperCase();
        if (!Set.of("PLATFORM", "ORG_ALL", "ROOMS", "READ_ONLY").contains(trimmed)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "无效的 scopeType: " + scopeType + "，允许值 PLATFORM/ORG_ALL/ROOMS/READ_ONLY");
        }
        return trimmed;
    }

    private void validateScopeStructure(String scopeType, Long organizationId, Long cloudRoomId) {
        if ("PLATFORM".equals(scopeType)) {
            if (organizationId != null || cloudRoomId != null) {
                throw new BusinessException(ResultCode.INVALID_PARAMETER,
                        "PLATFORM 范围不应指定 organizationId/cloudRoomId");
            }
            return;
        }
        if (organizationId == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    scopeType + " 范围必须指定 organizationId");
        }
        if ("ORG_ALL".equals(scopeType) && cloudRoomId != null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "ORG_ALL 范围不应指定 cloudRoomId");
        }
        if ("ROOMS".equals(scopeType) && cloudRoomId == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "ROOMS 范围必须指定 cloudRoomId");
        }
    }

    private void ensureCloudRoomBelongsToOrg(Long cloudRoomId, Long organizationId) {
        CloudRoom room = cloudRoomMapper.selectById(cloudRoomId);
        if (room == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND,
                    "机房不存在: " + cloudRoomId);
        }
        if (!room.getOrganizationId().equals(organizationId)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "机房 " + cloudRoomId + " 不属于组织 " + organizationId);
        }
    }

    private boolean assignmentExists(Long userId, String scopeType, Long orgId, Long roomId) {
        LambdaQueryWrapper<DataScopeAssignment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataScopeAssignment::getUserId, userId)
                .eq(DataScopeAssignment::getScopeType, scopeType);
        if (orgId == null) {
            wrapper.isNull(DataScopeAssignment::getOrganizationId);
        } else {
            wrapper.eq(DataScopeAssignment::getOrganizationId, orgId);
        }
        if (roomId == null) {
            wrapper.isNull(DataScopeAssignment::getCloudRoomId);
        } else {
            wrapper.eq(DataScopeAssignment::getCloudRoomId, roomId);
        }
        return dataScopeAssignmentMapper.selectCount(wrapper) > 0;
    }
}