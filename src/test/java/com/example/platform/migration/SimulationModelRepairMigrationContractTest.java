package com.example.platform.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationModelRepairMigrationContractTest {

    @Test
    void v8MustCreateAllSimulationTablesAfterBaselineReset() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V8__repair_simulation_model.sql"));

        // 二次基线后旧 notification_log 迁移已删除，模拟模型固定在 V8
        assertThat(Path.of("src/main/resources/db/migration/V9__repair_simulation_model.sql"))
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
