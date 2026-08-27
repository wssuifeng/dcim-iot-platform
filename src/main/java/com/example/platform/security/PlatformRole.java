package com.example.platform.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/*
 * 类功能:单租户三角色模型（ADMIN/OPERATOR/VIEWER），集中处理历史角色归一化
 *
 * 【历史兼容】多组织 SaaS 时期的 PLATFORM_ADMIN/ORG_ADMIN 及 v1.0 的 ADMIN
 *   在认证时统一归一化为 ADMIN，存量数据与登录态不失效。
 * */
public final class PlatformRole {

    public static final String AUTHENTICATED_USER = "AUTHENTICATED_USER";
    public static final String ADMIN = "ADMIN";
    public static final String OPERATOR = "OPERATOR";
    public static final String VIEWER = "VIEWER";

    private static final Set<String> VALID_DATABASE_ROLES = Set.of(ADMIN, OPERATOR, VIEWER);

    private PlatformRole() {
    }

    public static String normalize(String role) {
        if (role == null || role.isBlank()) {
            return VIEWER;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if ("ORG_ADMIN".equals(normalized) || "PLATFORM_ADMIN".equals(normalized)
                || "ADMIN".equals(normalized)) {
            return ADMIN;
        }
        return normalized;
    }

    public static boolean isValidDatabaseRole(String role) {
        return role != null && VALID_DATABASE_ROLES.contains(role.trim().toUpperCase(Locale.ROOT));
    }

    public static List<SimpleGrantedAuthority> authoritiesFor(String role) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + normalize(role)));
    }

    public static SimpleGrantedAuthority authenticatedUserAuthority() {
        return new SimpleGrantedAuthority("ROLE_" + AUTHENTICATED_USER);
    }

}
