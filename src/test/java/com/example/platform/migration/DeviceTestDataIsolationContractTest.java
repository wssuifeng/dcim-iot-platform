package com.example.platform.migration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceTestDataIsolationContractTest {

    @Test
    void deviceFixturesMustNotOverwriteProductData() throws Exception {
        String fixture = Files.readString(Path.of("src/test/resources/test-data.sql"));

        assertThat(fixture)
                .contains("id = -1001")
                .contains("TEST_TEMP_SENSOR")
                .contains("TEST-TEMP-001")
                .doesNotContain("id = 1")
                .doesNotContain("SET FOREIGN_KEY_CHECKS");

        for (String source : List.of(
                "src/test/java/com/example/platform/mapper/DeviceMapperTest.java",
                "src/test/java/com/example/platform/service/impl/DeviceServiceImplTest.java",
                "src/test/java/com/example/platform/service/impl/DeviceServiceTest.java")) {
            String testSource = Files.readString(Path.of(source));
            assertThat(testSource)
                    .contains("@Transactional")
                    .contains("@SqlConfig(encoding = \"UTF-8\")");
        }
    }

    @Test
    void v10MustRepairOnlyRecognizableFixturePollution() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V10__repair_device_test_data_pollution.sql"));

        assertThat(sql)
                .contains("device_code = 'TEMP-001'")
                .contains("organization_id IS NULL")
                .contains("cloud_room_id IS NULL")
                .contains("type_code = 'TEMP_SENSOR'")
                .contains("type_code = 'UPS'")
                .contains("WHERE NOT EXISTS");
    }
}
