package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.DataScopeAssignment;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.DataScopeAssignmentMapper;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * 类功能:数据范围服务实现类，负责根据用户ID和角色解析授权的数据范围上下文
 *
 * 【解析逻辑】
 * 1. 查询 SysUser 获取用户角色
 * 2. 如果是 PLATFORM_ADMIN，直接返回平台级范围（organizationIds 和 cloudRoomIds 为空表示无限制）
 * 3. 否则查询 DataScopeAssignment 表获取该用户的所有授权记录
 * 4. 聚合所有组织ID和机房ID到集合中
 * 5. 根据角色和授权记录推断 scopeType
 * */
@Slf4j
@Service
public class DataScopeServiceImpl implements DataScopeService {

    private final SysUserService sysUserService;
    private final DataScopeAssignmentMapper dataScopeAssignmentMapper;

    public DataScopeServiceImpl(SysUserService sysUserService,
                                DataScopeAssignmentMapper dataScopeAssignmentMapper) {
        this.sysUserService = sysUserService;
        this.dataScopeAssignmentMapper = dataScopeAssignmentMapper;
    }

    /*
     * 方法功能:根据用户ID解析数据范围上下文
     * 流程:查询用户角色 → PLATFORM_ADMIN直接返回 → 查询授权表 → 聚合范围 → 构建上下文
     * 参数:userId-用户ID
     * 返回数据:DataScopeContext-数据范围上下文
     * */
    @Override
    public DataScopeContext resolveDataScope(Long userId) {
        // 1. 查询用户角色
        SysUser user = sysUserService.getUserOrThrow(userId);
        String role = user.getRole();

        // 2. PLATFORM_ADMIN 拥有全局权限
        if ("PLATFORM_ADMIN".equals(role)) {
            return DataScopeContext.builder()
                    .userId(userId)
                    .role(role)
                    .scopeType("PLATFORM")
                    .organizationIds(new HashSet<>()) // 空集合表示无限制
                    .cloudRoomIds(new HashSet<>())
                    .build();
        }

        // 3. 查询用户的所有数据范围授权记录
        LambdaQueryWrapper<DataScopeAssignment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataScopeAssignment::getUserId, userId);
        List<DataScopeAssignment> assignments = dataScopeAssignmentMapper.selectList(wrapper);

        // 4. 聚合组织ID和机房ID
        Set<Long> organizationIds = new HashSet<>();
        Set<Long> cloudRoomIds = new HashSet<>();
        String scopeType = inferScopeType(role, assignments);

        for (DataScopeAssignment assignment : assignments) {
            if (assignment.getOrganizationId() != null) {
                organizationIds.add(assignment.getOrganizationId());
            }
            if (assignment.getCloudRoomId() != null) {
                cloudRoomIds.add(assignment.getCloudRoomId());
            }
        }

        // 5. 构建数据范围上下文
        return DataScopeContext.builder()
                .userId(userId)
                .role(role)
                .scopeType(scopeType)
                .organizationIds(organizationIds)
                .cloudRoomIds(cloudRoomIds)
                .build();
    }

    /*
     * 方法功能:推断范围类型
     * 逻辑:
     *   - ORG_ADMIN 默认为 ORG_ALL（管理组织下所有机房）
     *   - OPERATOR/VIEWER 且仅有机房授权为 ROOMS（指定机房列表）
     *   - 其他情况为 READ_ONLY
     * 参数:role-用户角色, assignments-授权记录列表
     * 返回数据:String-范围类型
     * */
    private String inferScopeType(String role, List<DataScopeAssignment> assignments) {
        if ("ORG_ADMIN".equals(role)) {
            return "ORG_ALL";
        }

        if ("OPERATOR".equals(role) || "VIEWER".equals(role)) {
            boolean hasRoomAssignments = assignments.stream()
                    .anyMatch(a -> a.getCloudRoomId() != null);
            return hasRoomAssignments ? "ROOMS" : "READ_ONLY";
        }

        return "READ_ONLY";
    }

    /*
     * 方法功能:校验用户是否有权访问指定组织
     * 参数:userId-用户ID, organizationId-组织ID
     * 返回数据:boolean-有权限返回true
     * */
    @Override
    public boolean canAccessOrganization(Long userId, Long organizationId) {
        DataScopeContext context = resolveDataScope(userId);
        return context.hasOrganizationAccess(organizationId);
    }

    /*
     * 方法功能:校验用户是否有权访问指定机房
     * 参数:userId-用户ID, cloudRoomId-机房ID
     * 返回数据:boolean-有权限返回true
     * */
    @Override
    public boolean canAccessCloudRoom(Long userId, Long cloudRoomId) {
        DataScopeContext context = resolveDataScope(userId);
        return context.hasCloudRoomAccess(cloudRoomId);
    }

    /*
     * 方法功能:校验用户是否有写权限（READ_ONLY 范围类型无写权限）
     * 参数:userId-用户ID
     * 返回数据:boolean-有写权限返回true
     * */
    @Override
    public boolean hasWritePermission(Long userId) {
        DataScopeContext context = resolveDataScope(userId);
        return context.hasWritePermission();
    }
}
