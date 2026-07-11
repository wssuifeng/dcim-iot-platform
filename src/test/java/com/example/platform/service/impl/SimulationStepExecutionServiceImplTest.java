package com.example.platform.service.impl;

import com.example.platform.dto.MetricSampleDTO;
import com.example.platform.entity.MetricSample;
import com.example.platform.entity.SimulationGeneratedDataRef;
import com.example.platform.entity.SimulationRunRecord;
import com.example.platform.entity.SimulationSceneStep;
import com.example.platform.mapper.SimulationGeneratedDataRefMapper;
import com.example.platform.service.MetricSampleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationStepExecutionServiceImplTest {

    @Mock
    private MetricSampleService metricSampleService;
    @Mock
    private SimulationGeneratedDataRefMapper generatedDataRefMapper;
    @InjectMocks
    private SimulationStepExecutionServiceImpl service;

    @Test
    void writeSampleMustPersistTraceWithMetricSampleId() {
        SimulationRunRecord run = run();
        SimulationSceneStep step = step("WRITE_SAMPLE", 5);
        MetricSample sample = new MetricSample();
        sample.setId(900L);
        when(metricSampleService.saveSample(any(MetricSampleDTO.class), eq(30L))).thenReturn(sample);

        service.executeStep(run, step, null, 30L);

        ArgumentCaptor<SimulationGeneratedDataRef> captor =
                ArgumentCaptor.forClass(SimulationGeneratedDataRef.class);
        verify(generatedDataRefMapper).insert(captor.capture());
        assertThat(captor.getValue().getMetricSampleId()).isEqualTo(900L);
        assertThat(captor.getValue().getDeviceId()).isEqualTo(5);
    }

    @Test
    void sleepMustAllowTraceWithoutDevice() {
        SimulationRunRecord run = run();
        SimulationSceneStep step = step("SLEEP", null);

        service.executeStep(run, step, null, 30L);

        verify(metricSampleService, never()).saveSample(any(), any());
        ArgumentCaptor<SimulationGeneratedDataRef> captor =
                ArgumentCaptor.forClass(SimulationGeneratedDataRef.class);
        verify(generatedDataRefMapper).insert(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("SLEEP");
        assertThat(captor.getValue().getDeviceId()).isNull();
    }

    private SimulationRunRecord run() {
        SimulationRunRecord run = new SimulationRunRecord();
        run.setId(7L);
        return run;
    }

    private SimulationSceneStep step(String action, Integer deviceId) {
        SimulationSceneStep step = new SimulationSceneStep();
        step.setId(10L);
        step.setPointId(100L);
        step.setTargetDeviceId(deviceId);
        step.setSampleValue(new BigDecimal("220.5"));
        step.setAction(action);
        return step;
    }
}