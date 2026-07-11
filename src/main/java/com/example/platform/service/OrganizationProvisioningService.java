package com.example.platform.service;

public interface OrganizationProvisioningService {
    Long provision(Long applicationId, Long reviewerId, String reviewNote);
}
