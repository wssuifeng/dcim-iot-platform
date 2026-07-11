package com.example.platform.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationIdentityMigrationContractTest {

    @Test
    void v7MustAddIdentitySettingsAndOrganizationConstraints() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V7__registration_identity_and_settings.sql"));

        assertThat(sql)
                .contains("ADD COLUMN email VARCHAR(254)")
                .contains("ADD COLUMN platform_role VARCHAR(20)")
                .contains("CREATE TABLE platform_registration_settings")
                .contains("ADD COLUMN join_application_enabled")
                .contains("UNIQUE KEY uk_org_name (org_name)");
    }
}
