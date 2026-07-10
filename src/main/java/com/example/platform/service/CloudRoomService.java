package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.CloudRoomCreateDTO;
import com.example.platform.dto.CloudRoomUpdateDTO;
import com.example.platform.entity.CloudRoom;

/*
 * 类功能:云机房服务接口，定义机房生命周期管理与服务层数据范围校验
 *
 * 【角色与范围】
 *   - PLATFORM_ADMIN：默认不穿透组织业务数据；分页查询返回空集（除非显式按组织查询也不绕过范围）；
 *     仍可按 ID 读取单条机房（用于平台运维场景），但需通过 CurrentUserContext 注入范围上下文
 *   - ORG_ADMIN：管理本组织机房（CRUD），分页查询按本组织过滤
 *   - OPERATOR/VIEWER：按授权机房/组织过滤查询；VIEWER 不可写
 * */
public interface CloudRoomService extends IService<CloudRoom> {

    /*
     * 方法功能:创建机房（ORG_ADMIN 专用，须校验 organizationId 落在当前用户范围内）
     * 参数:createDTO-机房创建请求, currentUserId-当前操作用户ID
     * 返回数据:CloudRoom-创建后的机房
     * */
    CloudRoom createCloudRoom(CloudRoomCreateDTO createDTO, Long currentUserId);

    /*
     * 方法功能:更新机房基本信息（ORG_ADMIN 专用，含范围校验）
     * 参数:id-机房ID, updateDTO-机房更新请求, currentUserId-当前操作用户ID
     * 返回数据:更新后的 CloudRoom
     * */
    CloudRoom updateCloudRoom(Long id, CloudRoomUpdateDTO updateDTO, Long currentUserId);

    /*
     * 方法功能:启用/停用机房（ORG_ADMIN 专用，含范围校验）
     * 参数:id-机房ID, status-目标状态(0正常 1停用), currentUserId-当前操作用户ID
     * 返回数据:更新后的 CloudRoom
     * */
    CloudRoom updateStatus(Long id, Integer status, Long currentUserId);

    /*
     * 方法功能:按 ID 查询机房，并校验当前用户范围
     * 参数:id-机房ID, currentUserId-当前操作用户ID
     * 返回数据:CloudRoom
     * */
    CloudRoom getCloudRoomOrThrow(Long id, Long currentUserId);

    /*
     * 方法功能:分页查询机房，按当前用户范围过滤
     * 参数:currentUserId-当前操作用户ID, current-页码, size-页大小,
     *      organizationId-组织筛选(可选), keyword-名称关键字(可选), status-状态筛选(可选)
     * 返回数据:Page<CloudRoom>-范围过滤后的机房分页
     * */
    Page<CloudRoom> pageCloudRooms(Long currentUserId, Integer current, Integer size,
                                   Long organizationId, String keyword, Integer status);

    CloudRoom findByOrganizationAndCode(Long organizationId, String roomCode);
}
