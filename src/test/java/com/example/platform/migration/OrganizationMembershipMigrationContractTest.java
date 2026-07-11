package com.example.platform.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationMembershipMigrationContractTest {

    @Test
    void v8MustCreateMembershipApplicationsAndBackfill() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V8__organization_membership_and_applications.sql"));

        assertThat(sql)
                .contains("CREATE TABLE organization_membership")
                .contains("UNIQUE KEY uk_membership_user_org (user_id, organization_id)")
                .contains("CREATE TABLE organization_creation_application")
                .contains("CREATE TABLE organization_join_application")
                .contains("INSERT INTO organization_membership");
    }
}
