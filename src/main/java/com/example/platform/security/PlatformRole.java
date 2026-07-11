package com.example.platform.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/*
 * 类功能:第四阶段角色模型工具，集中处理新角色和旧 ADMIN 角色兼容策略
 * */
public final class PlatformRole {

    public static final String AUTHENTICATED_USER = "AUTHENTICATED_USER";
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String ORG_ADMIN = "ORG_ADMIN";
    public static final String OPERATOR = "OPERATOR";
    public static final String VIEWER = "VIEWER";
    public static final String LEGACY_ADMIN = "ADMIN";

    private static final Set<String> VALID_DATABASE_ROLES = Set.of(
            PLATFORM_ADMIN, ORG_ADMIN, OPERATOR, VIEWER, LEGACY_ADMIN);

    private PlatformRole() {
    }

    public static String normalize(String role) {
        if (role == null || role.isBlank()) {
            return VIEWER;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (LEGACY_ADMIN.equals(normalized)) {
            return ORG_ADMIN;
        }
        return normalized;
    }

    public static boolean isValidDatabaseRole(String role) {
        return role != null && VALID_DATABASE_ROLES.contains(role.trim().toUpperCase(Locale.ROOT));
    }

    public static List<SimpleGrantedAuthority> authoritiesFor(String role) {
        String normalized = normalize(role);
        if (LEGACY_ADMIN.equalsIgnoreCase(role)) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_" + ORG_ADMIN),
                    new SimpleGrantedAuthority("ROLE_" + LEGACY_ADMIN)
            );
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + normalized));
    }

    public static SimpleGrantedAuthority authenticatedUserAuthority() {
        return new SimpleGrantedAuthority("ROLE_" + AUTHENTICATED_USER);
    }

}
