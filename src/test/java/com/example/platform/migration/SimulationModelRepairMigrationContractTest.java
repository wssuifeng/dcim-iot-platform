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

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS simulation_scene")
                .contains("CREATE TABLE IF NOT EXISTS simulation_scene_step")
                .contains("CREATE TABLE IF NOT EXISTS simulation_run_record")
                .contains("CREATE TABLE IF NOT EXISTS simulation_generated_data_ref")
                .contains("MODIFY COLUMN device_id INT NULL");
    }
}