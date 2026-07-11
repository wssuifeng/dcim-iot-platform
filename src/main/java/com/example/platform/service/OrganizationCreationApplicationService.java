package com.example.platform.service;

import com.example.platform.dto.OrganizationCreationApplicationDTO;
import com.example.platform.entity.OrganizationCreationApplication;

import java.util.List;

public interface OrganizationCreationApplicationService {
    OrganizationCreationApplication submit(Long userId, OrganizationCreationApplicationDTO dto);
    List<OrganizationCreationApplication> mine(Long userId);
    List<OrganizationCreationApplication> pending();
    void approve(Long id, Long reviewerId, String note);
    void reject(Long id, Long reviewerId, String note);
}
