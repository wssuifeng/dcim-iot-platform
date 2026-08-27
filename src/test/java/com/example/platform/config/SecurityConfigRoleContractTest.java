package com.example.platform.config;

import com.example.platform.security.PlatformRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 类功能:SecurityConfig 三角色契约测试，验证 ADMIN/OPERATOR/VIEWER 端点矩阵
 * */
class SecurityConfigRoleContractTest {

    @Test
    void adminOnlyRolesAreExactlyAdmin() {
        assertThat(SecurityConfig.ADMIN_ONLY_ROLES)
                .containsExactly(PlatformRole.ADMIN);
    }

    @Test
    void businessReadRolesCoverAllThreeRoles() {
        assertThat(SecurityConfig.BUSINESS_READ_ROLES)
                .containsExactly(PlatformRole.ADMIN, PlatformRole.OPERATOR, PlatformRole.VIEWER);
    }

    @Test
    void businessWriteRolesExcludeViewer() {
        assertThat(SecurityConfig.BUSINESS_WRITE_ROLES)
                .containsExactly(PlatformRole.ADMIN, PlatformRole.OPERATOR)
                .doesNotContain(PlatformRole.VIEWER);
    }

    @Test
    void auditReadRolesAllowAdminAndOperatorOnly() {
        assertThat(SecurityConfig.AUDIT_READ_ROLES)
                .containsExactly(PlatformRole.ADMIN, PlatformRole.OPERATOR)
                .doesNotContain(PlatformRole.VIEWER);
    }
}
