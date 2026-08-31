package com.example.platform.task;

import com.example.platform.mapper.MetricSampleHourlyMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:MetricSampleHourlyAggregationTask 单元测试（S8-4）
 * 覆盖：聚合窗口为最近 2 个整点小时 [now-2h, now)；聚合异常不外抛
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("MetricSampleHourlyAggregationTask 小时聚合任务测试")
class MetricSampleHourlyAggregationTaskTest {

    @Mock
    private MetricSampleHourlyMapper metricSampleHourlyMapper;

    @InjectMocks
    private MetricSampleHourlyAggregationTask task;

    @Test
    void should_aggregateLastTwoCompletedHours() {
        when(metricSampleHourlyMapper.aggregateWindow(any(), any())).thenReturn(42);

        task.aggregateRecentHours();

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(metricSampleHourlyMapper).aggregateWindow(startCaptor.capture(), endCaptor.capture());
        LocalDateTime start = startCaptor.getValue();
        LocalDateTime end = endCaptor.getValue();
        // 窗口端点均为整点，跨度恰为 2 小时（即最近 2 个完整小时）
        assertThat(start.getMinute()).isZero();
        assertThat(end.getMinute()).isZero();
        assertThat(start).isEqualTo(end.minusHours(2));
        assertThat(end).isAfter(LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).minusMinutes(1));
    }

    @Test
    void should_swallowException_onAggregationFailure() {
        when(metricSampleHourlyMapper.aggregateWindow(any(), any()))
                .thenThrow(new RuntimeException("db down"));

        task.aggregateRecentHours();

        verify(metricSampleHourlyMapper).aggregateWindow(any(), any());
    }
}
