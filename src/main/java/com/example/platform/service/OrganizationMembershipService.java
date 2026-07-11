package com.example.platform.service;

import com.example.platform.entity.OrganizationMembership;

import java.util.List;

public interface OrganizationMembershipService {
    OrganizationMembership requireActiveMembership(Long userId, Long organizationId);

    List<OrganizationMembership> listActiveMemberships(Long userId);

    void activateMembership(Long userId, Long organizationId, String role, Long approverId);

    void changeRole(Long userId, Long organizationId, String role, Long operatorId);

    void removeMembership(Long userId, Long organizationId, Long operatorId);
}
