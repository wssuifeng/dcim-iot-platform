package com.example.platform.service;

import com.example.platform.dto.DataScopeAssignmentDTO;
import com.example.platform.entity.DataScopeAssignment;

import java.util.List;

/*
 * 类功能:用户数据范围分配服务接口，负责管理 /users/{id}/data-scopes 端点的业务逻辑
 *
 * 【角色与范围】
 *   - PLATFORM_ADMIN：可分配任意 scopeType；可管理任意用户的范围
 *   - ORG_ADMIN：仅可分配/管理自身组织内的范围（ORG_ALL/ROOMS/READ_ONLY），不可分配 PLATFORM 范围
 *   - OPERATOR/VIEWER：不可调用此接口（Controller 层 @PreAuthorize 兜底）
 * */
public interface UserDataScopeService {

    /*
     * 方法功能:列出指定用户的数据范围分配
     * PLATFORM_ADMIN 可见全部；ORG_ADMIN 仅可见自身组织内的分配
     * */
    List<DataScopeAssignment> listAssignments(Long targetUserId, Long currentUserId);

    /*
     * 方法功能:为指定用户新增一条数据范围分配
     * 校验目标用户存在、scopeType 合法、organizationId/cloudRoomId 与 scopeType 对应、范围落在管理员可管辖范围内
     * */
    DataScopeAssignment assignScope(Long targetUserId, DataScopeAssignmentDTO dto, Long currentUserId);

    /*
     * 方法功能:移除指定用户的一条数据范围分配
     * 校验分配记录存在、属于管理员可管辖范围
     * */
    void removeAssignment(Long targetUserId, Long assignmentId, Long currentUserId);
}