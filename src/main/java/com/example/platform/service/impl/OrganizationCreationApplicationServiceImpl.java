package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.OrganizationCreationApplicationDTO;
import com.example.platform.entity.OrganizationCreationApplication;
import com.example.platform.entity.PlatformRegistrationSettings;
import com.example.platform.entity.SysUser;
import com.example.platform.security.PlatformRole;
import com.example.platform.mapper.OrganizationCreationApplicationMapper;
import com.example.platform.mapper.OrganizationMembershipMapper;
import com.example.platform.mapper.SysUserMapper;
import com.example.platform.service.OrganizationCreationApplicationService;
import com.example.platform.service.OrganizationProvisioningService;
import com.example.platform.service.PlatformRegistrationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganizationCreationApplicationServiceImpl implements OrganizationCreationApplicationService {
    private final OrganizationCreationApplicationMapper mapper;
    private final OrganizationMembershipMapper membershipMapper;
    private final SysUserMapper userMapper;
    private final PlatformRegistrationSettingsService settingsService;
    private final OrganizationProvisioningService provisioningService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrganizationCreationApplication submit(Long userId, OrganizationCreationApplicationDTO dto) {
        SysUser user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }
        if (PlatformRole.PLATFORM_ADMIN.equals(user.getPlatformRole())) {
            throw new BusinessException(ResultCode.MEMBERSHIP_CONFLICT,
                    "平台管理员不能提交组织创建申请");
        }
        if (!membershipMapper.selectActiveByUser(userId).isEmpty()) {
            throw new BusinessException(ResultCode.MEMBERSHIP_CONFLICT,
                    "创建组织前必须退出其他组织");
        }
        if (mapper.countPendingByApplicant(userId) > 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "已有待审核的组织申请");
        }
        PlatformRegistrationSettings settings = settingsService.getSettings();
        String mode = settings.getOrganizationRegistrationMode();
        if ("CLOSED".equals(mode)) {
            throw new BusinessException(ResultCode.REGISTRATION_CLOSED, "组织注册已关闭");
        }
        OrganizationCreationApplication application = new OrganizationCreationApplication();
        application.setApplicantUserId(userId);
        application.setProposedOrgName(dto.getOrgName().trim());
        application.setProposedOrgCode(dto.getOrgCode().trim());
        application.setDescription(dto.getDescription());
        application.setStatus("PENDING");
        application.setCreateTime(LocalDateTime.now());
        application.setUpdateTime(LocalDateTime.now());
        mapper.insert(application);
        if ("OPEN".equals(mode)) {
            provisioningService.provision(application.getId(), userId, "开放注册自动通过");
            return mapper.selectById(application.getId());
        }
        return application;
    }

    @Override
    public List<OrganizationCreationApplication> mine(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<OrganizationCreationApplication>()
                .eq(OrganizationCreationApplication::getApplicantUserId, userId)
                .orderByDesc(OrganizationCreationApplication::getCreateTime));
    }

    @Override
    public List<OrganizationCreationApplication> pending() {
        return mapper.selectList(new LambdaQueryWrapper<OrganizationCreationApplication>()
                .eq(OrganizationCreationApplication::getStatus, "PENDING")
                .orderByAsc(OrganizationCreationApplication::getCreateTime));
    }

    @Override
    public void approve(Long id, Long reviewerId, String note) {
        provisioningService.provision(id, reviewerId, note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, Long reviewerId, String note) {
        OrganizationCreationApplication application = mapper.selectByIdForUpdate(id);
        if (application == null || !"PENDING".equals(application.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "组织申请状态不可拒绝");
        }
        application.setStatus("REJECTED");
        application.setReviewerUserId(reviewerId);
        application.setReviewNote(note);
        application.setReviewTime(LocalDateTime.now());
        mapper.updateById(application);
    }
}
