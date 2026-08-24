package com.example.platform.security;

import com.example.platform.common.Result;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.entity.OrganizationMembership;
import com.example.platform.service.OrganizationMembershipService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/* 类功能:解析并校验当前请求的组织上下文，拒绝非法组织头并隔离组织角色 */
@Component
@RequiredArgsConstructor
public class OrganizationContextFilter extends OncePerRequestFilter {
    public static final String ORGANIZATION_HEADER = "X-Organization-Id";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OrganizationMembershipService membershipService;
    private final CurrentOrganizationContext organizationContext;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String header = request.getHeader(ORGANIZATION_HEADER);
            if (header != null) {
                if (authentication == null || !authentication.isAuthenticated()) {
                    writeForbidden(response, new DataScopeViolationException("组织上下文需要已认证用户"));
                    return;
                }
                try {
                    applyOrganization(authentication, header.trim());
                } catch (BusinessException e) {
                    writeForbidden(response, e);
                    return;
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            organizationContext.clear();
        }
    }

    private void applyOrganization(Authentication authentication, String header) {
        if (!StringUtils.hasText(header)) {
            throw new DataScopeViolationException("X-Organization-Id 不能为空");
        }

        Long organizationId;
        try {
            organizationId = Long.valueOf(header);
        } catch (NumberFormatException e) {
            throw new DataScopeViolationException("X-Organization-Id 必须是正整数");
        }
        if (organizationId <= 0) {
            throw new DataScopeViolationException("X-Organization-Id 必须是正整数");
        }

        Long userId = extractUserId(authentication.getPrincipal());
        if (userId == null) {
            throw new DataScopeViolationException("组织上下文用户标识无效");
        }

        OrganizationMembership membership = membershipService.requireActiveMembership(userId, organizationId);
        if (membership == null || !"ACTIVE".equals(membership.getStatus())
                || !isOrganizationRole(membership.getMembershipRole())) {
            throw new DataScopeViolationException("组织成员关系无效");
        }

        List<GrantedAuthority> authorities = new ArrayList<>(authentication.getAuthorities());
        authorities.removeIf(authority -> isOrganizationRole(authority.getAuthority()));
        if (!hasPlatformAdminAuthority(authentication)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + membership.getMembershipRole()));
        }
        setScopedAuthentication(authentication, authorities, organizationId);
    }

    private void setScopedAuthentication(Authentication authentication,
                                         List<GrantedAuthority> authorities, Long organizationId) {
        UsernamePasswordAuthenticationToken scoped = new UsernamePasswordAuthenticationToken(
                authentication.getPrincipal(), null, authorities);
        scoped.setDetails(authentication.getDetails());
        SecurityContextHolder.getContext().setAuthentication(scoped);
        organizationContext.setOrganizationId(organizationId);
    }

    private boolean isOrganizationRole(String value) {
        return PlatformRole.ORG_ADMIN.equals(value)
                || PlatformRole.OPERATOR.equals(value)
                || PlatformRole.VIEWER.equals(value)
                || ("ROLE_" + PlatformRole.ORG_ADMIN).equals(value)
                || ("ROLE_" + PlatformRole.OPERATOR).equals(value)
                || ("ROLE_" + PlatformRole.VIEWER).equals(value);
    }

    private boolean hasPlatformAdminAuthority(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("ROLE_" + PlatformRole.PLATFORM_ADMIN)::equals);
    }

    private Long extractUserId(Object principal) {
        if (principal instanceof Long id) {
            return id;
        }
        if (principal instanceof String value) {
            try {
                return Long.valueOf(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void writeForbidden(HttpServletResponse response, BusinessException exception) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(
                Result.error(exception.getCode(), exception.getMessage())));
    }
}
