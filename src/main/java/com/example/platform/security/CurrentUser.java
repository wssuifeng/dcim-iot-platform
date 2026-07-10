package com.example.platform.security;

import java.util.Set;

/*
 * 类功能:当前认证用户的最小安全上下文，不承载密码、Token或业务敏感数据
 * */
public record CurrentUser(Long userId, String username, String role, Set<String> roles) {

    public CurrentUser {
        roles = Set.copyOf(roles);
    }

    public boolean hasRole(String expectedRole) {
        return roles.contains(PlatformRole.normalize(expectedRole));
    }
}
