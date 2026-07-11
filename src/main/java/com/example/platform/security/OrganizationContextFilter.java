package com.example.platform.security;

import com.example.platform.common.exception.BusinessException;
import com.example.platform.entity.OrganizationMembership;
import com.example.platform.service.OrganizationMembershipService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrganizationContextFilter extends OncePerRequestFilter {
    public static final String ORGANIZATION_HEADER = "X-Organization-Id";

    private final OrganizationMembershipService membershipService;
    private final CurrentOrganizationContext organizationContext;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String header = request.getHeader(ORGANIZATION_HEADER);
            if (authentication != null && authentication.isAuthenticated() && StringUtils.hasText(header)) {
                applyOrganization(authentication, header.trim());
            }
            filterChain.doFilter(request, response);
        } finally {
            organizationContext.clear();
        }
    }

    private void applyOrganization(Authentication authentication, String header) {
        Long organizationId;
        try {
            organizationId = Long.valueOf(header);
        } catch (NumberFormatException e) {
            return;
        }
        Long userId = authentication.getPrincipal() instanceof Long id ? id : null;
        if (userId == null) {
            return;
        }

        OrganizationMembership membership;
        try {
            membership = membershipService.requireActiveMembership(userId, organizationId);
        } catch (BusinessException e) {
            return;
        }

        List<GrantedAuthority> authorities = new ArrayList<>(authentication.getAuthorities());
        authorities.removeIf(authority -> isOrganizationRole(authority.getAuthority()));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + membership.getMembershipRole()));
        UsernamePasswordAuthenticationToken scoped = new UsernamePasswordAuthenticationToken(
                authentication.getPrincipal(), null, authorities);
        scoped.setDetails(authentication.getDetails());
        SecurityContextHolder.getContext().setAuthentication(scoped);
        organizationContext.setOrganizationId(organizationId);
    }

    private boolean isOrganizationRole(String authority) {
        return ("ROLE_" + PlatformRole.ORG_ADMIN).equals(authority)
                || ("ROLE_" + PlatformRole.OPERATOR).equals(authority)
                || ("ROLE_" + PlatformRole.VIEWER).equals(authority);
    }
}
