package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.dto.OrganizationCreateDTO;
import com.example.platform.dto.OrganizationUpdateDTO;
import com.example.platform.entity.Organization;
import com.example.platform.mapper.OrganizationMapper;
import com.example.platform.security.PlatformRole;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.OrganizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/*
 * 类功能:组织服务实现类，负责组织 CRUD、编码唯一性校验与服务层数据范围过滤
 *
 * 【范围过滤策略】
 *   - PLATFORM_ADMIN：scopeType=PLATFORM，organizationIds 为空集合表示无限制 → 查询返回全部
 *   - ORG_ADMIN/OPERATOR/VIEWER：按 context.organizationIds 过滤，未授权组织不可查询/操作
 *
 * 【业务约束】
 *   - orgCode 全局唯一
 *   - status 仅允许 0(正常)/1(停用)
 *   - 写操作（创建/更新/状态变更）仅 PLATFORM_ADMIN 可执行（Controller 层 @PreAuthorize 兜底）
 * */
@Slf4j
@Service
public class OrganizationServiceImpl extends ServiceImpl<OrganizationMapper, Organization> implements OrganizationService {

    private final DataScopeService dataScopeService;

    public OrganizationServiceImpl(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Organization createOrganization(OrganizationCreateDTO createDTO) {
        checkOrgCodeUnique(createDTO.getOrgCode());
        Organization organization = new Organization();
        organization.setOrgName(createDTO.getOrgName());
        organization.setOrgCode(createDTO.getOrgCode());
        organization.setDescription(createDTO.getDescription());
        organization.setStatus(0);
        if (!save(organization)) {
            throw new BusinessException("创建组织失败，请稍后重试");
        }
        log.info("组织创建成功 orgCode={} orgName={}", organization.getOrgCode(), organization.getOrgName());
        return organization;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Organization updateOrganization(Long id, OrganizationUpdateDTO updateDTO) {
        Organization organization = getById(id);
        if (organization == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "组织不存在: " + id);
        }
        if (updateDTO.getOrgName() != null) {
            organization.setOrgName(updateDTO.getOrgName());
        }
        if (updateDTO.getDescription() != null) {
            organization.setDescription(updateDTO.getDescription());
        }
        if (!updateById(organization)) {
            throw new BusinessException("更新组织失败，数据可能已被他人修改");
        }
        return organization;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Organization updateStatus(Long id, Integer status) {
        if (status == null || status < 0 || status > 1) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "状态值无效，允许值：0-正常，1-停用");
        }
        Organization organization = getById(id);
        if (organization == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "组织不存在: " + id);
        }
        organization.setStatus(status);
        if (!updateById(organization)) {
            throw new BusinessException("更新组织状态失败");
        }
        return organization;
    }

    @Override
    public Organization getOrganizationOrThrow(Long id, Long currentUserId) {
        Organization organization = getById(id);
        if (organization == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "组织不存在: " + id);
        }
        ensureOrganizationScope(currentUserId, organization.getId());
        return organization;
    }

    @Override
    public Page<Organization> pageOrganizations(Long currentUserId, Integer current, Integer size,
                                                String keyword, Integer status) {
        int[] params = PaginationUtils.validate(current, size);
        Page<Organization> page = new Page<>(params[0], params[1]);
        LambdaQueryWrapper<Organization> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(Organization::getOrgName, keyword.trim());
        }
        if (status != null) {
            wrapper.eq(Organization::getStatus, status);
        }

        // 范围过滤：非 PLATFORM_ADMIN 仅可看到自己被授权的组织
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        if (!context.isPlatformAdmin()) {
            Set<Long> orgIds = context.getOrganizationIds();
            if (orgIds.isEmpty()) {
                return new Page<>(params[0], params[1]);
            }
            wrapper.in(Organization::getId, orgIds);
        }
        wrapper.orderByDesc(Organization::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public Organization findByCode(String organizationCode) {
        if (organizationCode == null || organizationCode.isBlank()) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getOrgCode, organizationCode.trim()), false);
    }

    /*
     * 方法功能:校验当前用户是否有权访问目标组织
     * PLATFORM_ADMIN 全通过；其余角色按 context.organizationIds 比对
     * 未授权抛出 DataScopeViolationException 记录拒绝审计
     * */
    private void ensureOrganizationScope(Long currentUserId, Long organizationId) {
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        if (!context.hasOrganizationAccess(organizationId)) {
            throw new DataScopeViolationException("Organization", String.valueOf(organizationId));
        }
    }

    private void checkOrgCodeUnique(String orgCode) {
        LambdaQueryWrapper<Organization> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Organization::getOrgCode, orgCode);
        if (count(wrapper) > 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "组织编码已存在: " + orgCode);
        }
    }

    /**
     * 判断角色是否为 PLATFORM_ADMIN（保留为静态工具，供 Controller 侧使用）
     */
    public static boolean isPlatformAdminRole(String role) {
        return PlatformRole.PLATFORM_ADMIN.equalsIgnoreCase(role);
    }
}
