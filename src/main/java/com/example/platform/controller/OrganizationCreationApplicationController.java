package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.OrganizationApplicationReviewDTO;
import com.example.platform.dto.OrganizationCreationApplicationDTO;
import com.example.platform.entity.OrganizationCreationApplication;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.OrganizationCreationApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrganizationCreationApplicationController {
    private final OrganizationCreationApplicationService service;
    private final CurrentUserContext currentUserContext;

    @PostMapping("/organization-applications")
    public Result<OrganizationCreationApplication> submit(
            @Valid @RequestBody OrganizationCreationApplicationDTO dto) {
        return Result.success(service.submit(currentUserContext.requireCurrentUser().userId(), dto));
    }

    @GetMapping("/organization-applications/mine")
    public Result<List<OrganizationCreationApplication>> mine() {
        return Result.success(service.mine(currentUserContext.requireCurrentUser().userId()));
    }

    @GetMapping("/platform/organization-applications")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Result<List<OrganizationCreationApplication>> pending() {
        return Result.success(service.pending());
    }

    @PutMapping("/platform/organization-applications/{id}/approve")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Result<Void> approve(@PathVariable Long id,
                                @RequestBody(required = false) OrganizationApplicationReviewDTO dto) {
        service.approve(id, currentUserContext.requireCurrentUser().userId(),
                dto == null ? null : dto.getNote());
        return Result.success();
    }

    @PutMapping("/platform/organization-applications/{id}/reject")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Result<Void> reject(@PathVariable Long id,
                               @RequestBody(required = false) OrganizationApplicationReviewDTO dto) {
        service.reject(id, currentUserContext.requireCurrentUser().userId(),
                dto == null ? null : dto.getNote());
        return Result.success();
    }
}
