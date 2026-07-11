package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.entity.Organization;
import com.example.platform.entity.OrganizationJoinApplication;
import com.example.platform.mapper.OrganizationJoinApplicationMapper;
import com.example.platform.mapper.OrganizationMapper;
import com.example.platform.mapper.OrganizationMembershipMapper;
import com.example.platform.mapper.SysUserMapper;
import com.example.platform.service.OrganizationJoinApplicationService;
import com.example.platform.service.OrganizationMembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganizationJoinApplicationServiceImpl implements OrganizationJoinApplicationService {
    private final OrganizationJoinApplicationMapper mapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationMembershipMapper membershipMapper;
    private final OrganizationMembershipService membershipService;
    private final SysUserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long userId, Long organizationId, String note) {
        userMapper.selectByIdForUpdate(userId);
        Organization organization = organizationMapper.selectById(organizationId);
        if (organization == null || !Boolean.TRUE.equals(organization.getJoinApplicationEnabled())) {
            throw new BusinessException(ResultCode.FEATURE_DISABLED, "该组织未开放加入申请");
        }
        if (membershipMapper.countActiveAdminMemberships(userId) > 0) {
            throw new BusinessException(ResultCode.MEMBERSHIP_CONFLICT,
                    "组织管理员不能加入其他组织");
        }
        if (membershipMapper.selectActiveByUserAndOrganization(userId, organizationId) != null) {
            throw new BusinessException(ResultCode.MEMBERSHIP_CONFLICT, "已是该组织成员");
        }
        if (mapper.countPending(userId, organizationId) > 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "已有待审核加入申请");
        }
        OrganizationJoinApplication application = new OrganizationJoinApplication();
        application.setApplicantUserId(userId);
        application.setOrganizationId(organizationId);
        application.setRequestNote(note);
        application.setStatus("PENDING");
        application.setCreateTime(LocalDateTime.now());
        application.setUpdateTime(LocalDateTime.now());
        mapper.insert(application);
    }

    @Override
    public List<OrganizationJoinApplication> listPending(Long organizationId) {
        return mapper.selectList(new LambdaQueryWrapper<OrganizationJoinApplication>()
                .eq(OrganizationJoinApplication::getOrganizationId, organizationId)
                .eq(OrganizationJoinApplication::getStatus, "PENDING")
                .orderByAsc(OrganizationJoinApplication::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long applicationId, Long organizationId, String role,
                        Long reviewerId, String note) {
        OrganizationJoinApplication application = requirePending(applicationId, organizationId);
        membershipService.activateMembership(application.getApplicantUserId(), organizationId,
                role, reviewerId);
        finish(application, "APPROVED", role, reviewerId, note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long applicationId, Long organizationId, Long reviewerId, String note) {
        finish(requirePending(applicationId, organizationId), "REJECTED", null, reviewerId, note);
    }

    private OrganizationJoinApplication requirePending(Long id, Long organizationId) {
        OrganizationJoinApplication application = mapper.selectByIdForUpdate(id);
        if (application == null || !organizationId.equals(application.getOrganizationId())
                || !"PENDING".equals(application.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "加入申请状态不可处理");
        }
        return application;
    }

    private void finish(OrganizationJoinApplication application, String status, String role,
                        Long reviewerId, String note) {
        application.setStatus(status);
        application.setAssignedMembershipRole(role);
        application.setReviewerUserId(reviewerId);
        application.setReviewNote(note);
        application.setReviewTime(LocalDateTime.now());
        mapper.updateById(application);
    }
}
