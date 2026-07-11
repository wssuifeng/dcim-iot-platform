package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.MembershipRoleUpdateDTO;
import com.example.platform.entity.OrganizationMembership;
import com.example.platform.mapper.OrganizationMembershipMapper;
import com.example.platform.security.CurrentOrganizationContext;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.OrganizationMembershipService;
import com.example.platform.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/organizations/{organizationId}")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ORG_ADMIN')")
public class OrganizationMemberController {
    private final OrganizationMembershipMapper membershipMapper;
    private final OrganizationMembershipService membershipService;
    private final OrganizationService organizationService;
    private final CurrentOrganizationContext organizationContext;
    private final CurrentUserContext currentUserContext;

    @GetMapping("/members")
    public Result<List<OrganizationMembership>> members(@PathVariable Long organizationId) {
        organizationContext.requireMatches(organizationId);
        return Result.success(membershipMapper.selectActiveByOrganization(organizationId));
    }

    @PutMapping("/members/{userId}/role")
    public Result<Void> changeRole(@PathVariable Long organizationId, @PathVariable Long userId,
                                   @Valid @RequestBody MembershipRoleUpdateDTO dto) {
        organizationContext.requireMatches(organizationId);
        membershipService.changeRole(userId, organizationId, dto.getRole(),
                currentUserContext.requireCurrentUser().userId());
        return Result.success();
    }

    @DeleteMapping("/members/{userId}")
    public Result<Void> remove(@PathVariable Long organizationId, @PathVariable Long userId) {
        organizationContext.requireMatches(organizationId);
        membershipService.removeMembership(userId, organizationId,
                currentUserContext.requireCurrentUser().userId());
        return Result.success();
    }

    @PutMapping("/join-setting")
    public Result<Void> updateJoinSetting(@PathVariable Long organizationId,
                                          @RequestParam boolean enabled) {
        organizationContext.requireMatches(organizationId);
        organizationService.updateJoinApplicationEnabled(organizationId, enabled);
        return Result.success();
    }
}
