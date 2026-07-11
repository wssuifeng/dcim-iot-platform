package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.OrganizationApplicationReviewDTO;
import com.example.platform.dto.OrganizationJoinApplicationDTO;
import com.example.platform.dto.OrganizationJoinReviewDTO;
import com.example.platform.entity.OrganizationJoinApplication;
import com.example.platform.security.CurrentOrganizationContext;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.OrganizationJoinApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/organizations/{organizationId}/join-applications")
@RequiredArgsConstructor
public class OrganizationJoinApplicationController {
    private final OrganizationJoinApplicationService service;
    private final CurrentUserContext currentUserContext;
    private final CurrentOrganizationContext organizationContext;

    @PostMapping
    public Result<Void> submit(@PathVariable Long organizationId,
                               @Valid @RequestBody OrganizationJoinApplicationDTO dto) {
        service.submit(currentUserContext.requireCurrentUser().userId(), organizationId, dto.getNote());
        return Result.success();
    }

    @GetMapping
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public Result<List<OrganizationJoinApplication>> pending(@PathVariable Long organizationId) {
        organizationContext.requireMatches(organizationId);
        return Result.success(service.listPending(organizationId));
    }

    @PutMapping("/{applicationId}/approve")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public Result<Void> approve(@PathVariable Long organizationId,
                                @PathVariable Long applicationId,
                                @Valid @RequestBody OrganizationJoinReviewDTO dto) {
        organizationContext.requireMatches(organizationId);
        service.approve(applicationId, organizationId, dto.getRole(),
                currentUserContext.requireCurrentUser().userId(), dto.getNote());
        return Result.success();
    }

    @PutMapping("/{applicationId}/reject")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public Result<Void> reject(@PathVariable Long organizationId,
                               @PathVariable Long applicationId,
                               @RequestBody(required = false) OrganizationApplicationReviewDTO dto) {
        organizationContext.requireMatches(organizationId);
        service.reject(applicationId, organizationId,
                currentUserContext.requireCurrentUser().userId(), dto == null ? null : dto.getNote());
        return Result.success();
    }
}
