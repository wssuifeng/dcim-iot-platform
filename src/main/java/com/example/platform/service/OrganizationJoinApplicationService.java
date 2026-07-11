package com.example.platform.service;

import com.example.platform.entity.OrganizationJoinApplication;

import java.util.List;

public interface OrganizationJoinApplicationService {
    void submit(Long userId, Long organizationId, String note);
    List<OrganizationJoinApplication> listPending(Long organizationId);
    void approve(Long applicationId, Long organizationId, String role, Long reviewerId, String note);
    void reject(Long applicationId, Long organizationId, Long reviewerId, String note);
}
