package com.example.platform.config;

import com.example.platform.security.PlatformRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigRoleContractTest {

    @Test
    void platformAdminDoesNotDefaultToBusinessReadRole() {
        assertThat(SecurityConfig.BUSINESS_READ_ROLES)
                .containsExactly(PlatformRole.ORG_ADMIN, PlatformRole.OPERATOR, PlatformRole.VIEWER)
                .doesNotContain(PlatformRole.PLATFORM_ADMIN);
    }

    @Test
    void businessWriteRolesExcludeViewerAndPlatformAdmin() {
        assertThat(SecurityConfig.BUSINESS_WRITE_ROLES)
                .containsExactly(PlatformRole.ORG_ADMIN, PlatformRole.OPERATOR)
                .doesNotContain(PlatformRole.VIEWER, PlatformRole.PLATFORM_ADMIN);
    }

    @Test
    void deviceTypeReadRolesIncludePlatformAdminAndBusinessReaders() {
        assertThat(SecurityConfig.DEVICE_TYPE_READ_ROLES)
                .containsExactly(
                        PlatformRole.PLATFORM_ADMIN,
                        PlatformRole.ORG_ADMIN,
                        PlatformRole.OPERATOR,
                        PlatformRole.VIEWER);
    }
}
