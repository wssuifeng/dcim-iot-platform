package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.entity.Organization;
import com.example.platform.entity.OrganizationCreationApplication;
import com.example.platform.mapper.OrganizationCreationApplicationMapper;
import com.example.platform.mapper.OrganizationMapper;
import com.example.platform.service.OrganizationMembershipService;
import com.example.platform.service.OrganizationProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrganizationProvisioningServiceImpl implements OrganizationProvisioningService {
    private final OrganizationCreationApplicationMapper applicationMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationMembershipService membershipService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long provision(Long applicationId, Long reviewerId, String reviewNote) {
        OrganizationCreationApplication application = applicationMapper.selectByIdForUpdate(applicationId);
        if (application == null || !"PENDING".equals(application.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "组织申请状态不可审批");
        }
        requireOrganizationIdentityAvailable(application.getProposedOrgName(), application.getProposedOrgCode());
        Organization organization = new Organization();
        organization.setOrgName(application.getProposedOrgName());
        organization.setOrgCode(application.getProposedOrgCode());
        organization.setDescription(application.getDescription());
        organization.setStatus(0);
        organization.setJoinApplicationEnabled(true);
        organizationMapper.insert(organization);
        membershipService.activateMembership(application.getApplicantUserId(), organization.getId(),
                "ORG_ADMIN", reviewerId);
        application.setStatus("APPROVED");
        application.setReviewerUserId(reviewerId);
        application.setReviewNote(reviewNote);
        application.setReviewTime(LocalDateTime.now());
        application.setResultingOrganizationId(organization.getId());
        applicationMapper.updateById(application);
        return organization.getId();
    }

    private void requireOrganizationIdentityAvailable(String name, String code) {
        long count = organizationMapper.selectCount(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getOrgName, name).or().eq(Organization::getOrgCode, code));
        if (count > 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "组织名称或编码已存在");
        }
    }
}
