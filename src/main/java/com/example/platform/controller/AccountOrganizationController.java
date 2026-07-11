package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.entity.OrganizationMembership;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.OrganizationMembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AccountOrganizationController {
    private final CurrentUserContext currentUserContext;
    private final OrganizationMembershipService membershipService;

    @GetMapping("/auth/memberships")
    public Result<List<OrganizationMembership>> memberships() {
        return Result.success(membershipService.listActiveMemberships(
                currentUserContext.requireCurrentUser().userId()));
    }
}
