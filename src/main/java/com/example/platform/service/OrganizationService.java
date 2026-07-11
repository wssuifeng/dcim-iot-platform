package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.OrganizationCreateDTO;
import com.example.platform.dto.OrganizationUpdateDTO;
import com.example.platform.entity.Organization;
import com.example.platform.dto.PublicOrganizationVO;

import java.util.List;

/*
 * 类功能:组织服务接口，定义组织生命周期管理与服务层数据范围校验
 *
 * 【角色与范围】
 *   - PLATFORM_ADMIN：组织 CRUD 全权限；分页查询返回所有组织
 *   - ORG_ADMIN：仅可查询自身被授权的组织，不可创建/修改组织
 *   - OPERATOR/VIEWER：仅可查询自身被授权的组织（范围过滤）
 * */
public interface OrganizationService extends IService<Organization> {

    /*
     * 方法功能:创建组织（PLATFORM_ADMIN 专用）
     * 参数:createDTO-组织创建请求
     * 返回数据:Organization-创建后的组织（含自动生成的主键）
     * */
    Organization createOrganization(OrganizationCreateDTO createDTO);

    /*
     * 方法功能:更新组织基本信息（PLATFORM_ADMIN 专用，不允许修改 orgCode）
     * 参数:id-组织ID, updateDTO-组织更新请求
     * 返回数据:Organization-更新后的组织
     * */
    Organization updateOrganization(Long id, OrganizationUpdateDTO updateDTO);

    /*
     * 方法功能:启用/停用组织（PLATFORM_ADMIN 专用，软停用，对应状态 0/1）
     * 参数:id-组织ID, status-目标状态(0正常 1停用)
     * 返回数据:更新后的 Organization
     * */
    Organization updateStatus(Long id, Integer status);

    /*
     * 方法功能:按 ID 查询组织，并校验当前用户范围；未授权抛出 DataScopeViolationException
     * 参数:id-组织ID, currentUserId-当前操作用户ID
     * 返回数据:Organization
     * */
    Organization getOrganizationOrThrow(Long id, Long currentUserId);

    /*
     * 方法功能:分页查询组织，按当前用户范围过滤
     * 参数:currentUserId-当前操作用户ID, current-页码, size-页大小, keyword-名称关键字(可选), status-状态筛选(可选)
     * 返回数据:Page<Organization>-范围过滤后的组织分页
     * */
    Page<Organization> pageOrganizations(Long currentUserId, Integer current, Integer size,
                                         String keyword, Integer status);

    Organization findByCode(String organizationCode);

    List<PublicOrganizationVO> listPublicOrganizations(String keyword);

    void updateJoinApplicationEnabled(Long organizationId, boolean enabled);
}
