package com.example.platform.task;

import com.example.platform.dto.AlarmTriggerContext;
import com.example.platform.entity.MetricRetentionPolicy;
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.MetricRetentionPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:MetricSamplePartitionTask 单元测试（S8-4）
 * 覆盖：未来分区供给（缺失才建）、供给失败触发 PARTITION_SUPPLY_FAILED 告警、
 *   整月过期分区 DROP 与未过期保留
 * */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MetricSamplePartitionTask 分区供给与回收测试")
class MetricSamplePartitionTaskTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AlarmEventService alarmEventService;
    @Mock
    private MetricRetentionPolicyService metricRetentionPolicyService;

    private MetricSamplePartitionTask task;

    @BeforeEach
    void setUp() {
        task = new MetricSamplePartitionTask(jdbcTemplate, alarmEventService, metricRetentionPolicyService);
        MetricRetentionPolicy global = new MetricRetentionPolicy();
        global.setPointId(null);
        global.setRetentionDays(90);
        when(metricRetentionPolicyService.getGlobalPolicyOrThrow()).thenReturn(global);
    }

    @Test
    @DisplayName("供给未来两个月分区：缺失即 ADD PARTITION（固定 2026-08 基准）")
    void should_addMissingPartitions() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
                .thenReturn(0);

        task.ensureFuturePartitions(YearMonth.of(2026, 8));

        verify(jdbcTemplate).execute(contains("ADD PARTITION (PARTITION p202609 VALUES LESS THAN ('2026-10-01'))"));
        verify(jdbcTemplate).execute(contains("ADD PARTITION (PARTITION p202610 VALUES LESS THAN ('2026-11-01'))"));
    }

    @Test
    @DisplayName("分区已存在：不重复建")
    void should_skipExistingPartitions() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
                .thenReturn(1);

        task.ensureFuturePartitions(YearMonth.of(2026, 8));

        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    @DisplayName("供给失败：触发 PARTITION_SUPPLY_FAILED 告警（设备为空）")
    void should_triggerAlarm_whenSupplyFails() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), anyString(), anyString()))
                .thenReturn(0);
        org.mockito.Mockito.doThrow(new RuntimeException("access denied"))
                .when(jdbcTemplate).execute(anyString());
        var triggerResult = com.example.platform.dto.AlarmTriggerResult.of(
                new com.example.platform.entity.AlarmEvent(),
                com.example.platform.dto.AlarmTriggerResult.KIND_CREATED);
        when(alarmEventService.trigger(any(AlarmTriggerContext.class))).thenReturn(triggerResult);

        task.ensureAndRecycle();

        ArgumentCaptor<AlarmTriggerContext> captor = ArgumentCaptor.forClass(AlarmTriggerContext.class);
        verify(alarmEventService).trigger(captor.capture());
        AlarmTriggerContext context = captor.getValue();
        assertThat(context.getAlarmType()).isEqualTo("PARTITION_SUPPLY_FAILED");
        assertThat(context.getAlarmLevel()).isEqualTo(2);
        assertThat(context.getDeviceId()).isNull();
    }

    @Test
    @DisplayName("整月过期分区 DROP、未过期保留（90 天保留、2026-08-31 基准）")
    void should_dropExpiredPartitionOnly() {
        when(jdbcTemplate.queryForList(anyString(), anyString())).thenReturn(List.of(
                Map.of("PARTITION_NAME", "p202601", "PARTITION_DESCRIPTION", "2026-02-01"),
                Map.of("PARTITION_NAME", "p202608", "PARTITION_DESCRIPTION", "2026-09-01")));

        task.dropExpiredPartitions(LocalDate.of(2026, 8, 31));

        // cutoff = 2026-08-31 - 90d = 2026-06-02：p202601（上界 02-01）过期，p202608（09-01）未过期
        verify(jdbcTemplate).execute(contains("DROP PARTITION p202601"));
        verify(jdbcTemplate, never()).execute(contains("DROP PARTITION p202608"));
    }
}
