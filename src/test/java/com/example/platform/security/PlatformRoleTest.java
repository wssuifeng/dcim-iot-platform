package com.example.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformRoleTest {

    @Test
    void normalizeLegacyAdminToOrgAdmin() {
        assertThat(PlatformRole.normalize("ADMIN")).isEqualTo(PlatformRole.ORG_ADMIN);
        assertThat(PlatformRole.normalize(" admin ")).isEqualTo(PlatformRole.ORG_ADMIN);
    }

    @Test
    void normalizeKeepsStage4Roles() {
        assertThat(PlatformRole.normalize("PLATFORM_ADMIN")).isEqualTo(PlatformRole.PLATFORM_ADMIN);
        assertThat(PlatformRole.normalize("ORG_ADMIN")).isEqualTo(PlatformRole.ORG_ADMIN);
        assertThat(PlatformRole.normalize("OPERATOR")).isEqualTo(PlatformRole.OPERATOR);
        assertThat(PlatformRole.normalize("VIEWER")).isEqualTo(PlatformRole.VIEWER);
    }

    @Test
    void legacyAdminReceivesCompatibilityAuthorities() {
        assertThat(PlatformRole.authoritiesFor("ADMIN"))
                .containsExactly(
                        new SimpleGrantedAuthority("ROLE_" + PlatformRole.ORG_ADMIN),
                        new SimpleGrantedAuthority("ROLE_" + PlatformRole.LEGACY_ADMIN));
    }

    @Test
    void databaseRolesAcceptStage4AndLegacyValues() {
        assertThat(PlatformRole.isValidDatabaseRole(PlatformRole.PLATFORM_ADMIN)).isTrue();
        assertThat(PlatformRole.isValidDatabaseRole(PlatformRole.ORG_ADMIN)).isTrue();
        assertThat(PlatformRole.isValidDatabaseRole(PlatformRole.OPERATOR)).isTrue();
        assertThat(PlatformRole.isValidDatabaseRole(PlatformRole.VIEWER)).isTrue();
        assertThat(PlatformRole.isValidDatabaseRole(PlatformRole.LEGACY_ADMIN)).isTrue();
        assertThat(PlatformRole.isValidDatabaseRole("ROOT")).isFalse();
    }
}
