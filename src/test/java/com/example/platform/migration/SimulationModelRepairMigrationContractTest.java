package com.example.platform.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationModelRepairMigrationContractTest {

    @Test
    void v9MustRepairAllSimulationTablesWithoutReusingV6() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V9__repair_simulation_model.sql"));

        assertThat(Path.of("src/main/resources/db/migration/V6__add_notification_log.sql"))
                .exists();
        assertThat(Path.of("src/main/resources/db/migration/V6__simulation_model.sql"))
                .doesNotExist();
        String applicationConfig = Files.readString(Path.of("src/main/resources/application.yaml"));
        assertThat(applicationConfig).contains("validate-on-migrate: true");

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS simulation_scene")
                .contains("CREATE TABLE IF NOT EXISTS simulation_scene_step")
                .contains("CREATE TABLE IF NOT EXISTS simulation_run_record")
                .contains("CREATE TABLE IF NOT EXISTS simulation_generated_data_ref")
                .contains("MODIFY COLUMN device_id INT NULL");
    }
}