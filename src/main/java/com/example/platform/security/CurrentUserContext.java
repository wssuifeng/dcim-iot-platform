package com.example.platform.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/*
 * 类功能:统一读取 HTTP SecurityContext 或 WebSocket Principal 中的当前用户
 * */
@Component
public class CurrentUserContext {

    public Optional<CurrentUser> currentUser() {
        return fromAuthentication(SecurityContextHolder.getContext().getAuthentication());
    }

    public Optional<CurrentUser> currentUser(Principal principal) {
        if (principal instanceof Authentication authentication) {
            return fromAuthentication(authentication);
        }
        return Optional.empty();
    }

    public CurrentUser requireCurrentUser() {
        return currentUser().orElseThrow(this::unauthenticated);
    }

    public CurrentUser requireCurrentUser(Principal principal) {
        return currentUser(principal).orElseThrow(this::unauthenticated);
    }

    private Optional<CurrentUser> fromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Long userId = extractUserId(authentication.getPrincipal());
        if (userId == null) {
            return Optional.empty();
        }

        Set<String> roles = new LinkedHashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (value != null && value.startsWith("ROLE_")) {
                String role = value.substring("ROLE_".length());
                if (PlatformRole.isValidDatabaseRole(role)
                        || PlatformRole.AUTHENTICATED_USER.equals(role)) {
                    roles.add(PlatformRole.normalize(role));
                }
            }
        }
        if (roles.isEmpty()) {
            return Optional.empty();
        }

        String username = authentication.getDetails() instanceof String name ? name : null;
        String primaryRole = roles.stream()
                .filter(role -> !PlatformRole.AUTHENTICATED_USER.equals(role))
                .findFirst()
                .orElse(PlatformRole.AUTHENTICATED_USER);
        return Optional.of(new CurrentUser(userId, username, primaryRole, roles));
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

    private AuthenticationCredentialsNotFoundException unauthenticated() {
        return new AuthenticationCredentialsNotFoundException("当前用户未认证");
    }
}
