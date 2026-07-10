package com.example.platform.service;

import com.example.platform.dto.DataScopeContext;

/*
 * 类功能:数据范围服务接口，负责根据用户ID和角色解析授权的数据范围上下文
 *
 * 【数据范围模型】
 * - PLATFORM_ADMIN: 平台管理员，全局访问权限（organizationIds 为空表示无限制）
 * - ORG_ADMIN: 组织管理员，管理指定组织下的所有机房和用户
 * - OPERATOR: 运维人员，访问指定机房的设备和数据
 * - VIEWER: 查看者，只读访问指定范围的数据
 *
 * 【范围类型 scopeType】
 * - PLATFORM: 平台级（仅 PLATFORM_ADMIN）
 * - ORG_ALL: 组织全部机房（ORG_ADMIN 默认）
 * - ROOMS: 指定机房列表（OPERATOR/VIEWER）
 * - READ_ONLY: 只读范围（任何角色可附加，禁止写操作）
 * */
public interface DataScopeService {

    /*
     * 方法功能:根据用户ID解析数据范围上下文
     * 流程:查询 SysUser 获取角色 → 查询 DataScopeAssignment 获取授权范围 → 构建 DataScopeContext
     * 参数:userId-用户ID
     * 返回数据:DataScopeContext-数据范围上下文
     * */
    DataScopeContext resolveDataScope(Long userId);

    /*
     * 方法功能:校验用户是否有权访问指定组织
     * 参数:userId-用户ID, organizationId-组织ID
     * 返回数据:boolean-有权限返回true
     * */
    boolean canAccessOrganization(Long userId, Long organizationId);

    /*
     * 方法功能:校验用户是否有权访问指定机房
     * 参数:userId-用户ID, cloudRoomId-机房ID
     * 返回数据:boolean-有权限返回true
     * */
    boolean canAccessCloudRoom(Long userId, Long cloudRoomId);

    /*
     * 方法功能:校验用户是否有写权限（READ_ONLY 范围类型无写权限）
     * 参数:userId-用户ID
     * 返回数据:boolean-有写权限返回true
     * */
    boolean hasWritePermission(Long userId);
}
