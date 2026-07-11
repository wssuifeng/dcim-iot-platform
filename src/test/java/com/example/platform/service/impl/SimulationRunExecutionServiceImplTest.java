package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.entity.SimulationRunRecord;
import com.example.platform.entity.SimulationSceneStep;
import com.example.platform.mapper.SimulationRunRecordMapper;
import com.example.platform.mapper.SimulationSceneStepMapper;
import com.example.platform.security.CurrentOrganizationContext;
import com.example.platform.service.SimulationStepExecutionService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationRunExecutionServiceImplTest {

    @Mock
    private SimulationRunRecordMapper runMapper;
    @Mock
    private SimulationSceneStepMapper stepMapper;
    @Mock
    private SimulationStepExecutionService stepExecutionService;
    @Mock
    private CurrentOrganizationContext organizationContext;
    @InjectMocks
    private SimulationRunExecutionServiceImpl service;

    @BeforeEach
    void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant runAssistant = new MapperBuilderAssistant(configuration, "");
        runAssistant.setCurrentNamespace(SimulationRunRecordMapper.class.getName());
        TableInfoHelper.initTableInfo(runAssistant, SimulationRunRecord.class);
        MapperBuilderAssistant stepAssistant = new MapperBuilderAssistant(configuration, "");
        stepAssistant.setCurrentNamespace(SimulationSceneStepMapper.class.getName());
        TableInfoHelper.initTableInfo(stepAssistant, SimulationSceneStep.class);
    }
    @Test
    void cancelledRunMustNotStart() {
        SimulationRunRecord cancelled = run("CANCELLED");
        when(runMapper.selectById(7L)).thenReturn(cancelled);

        service.executeAsync(7L, null, 30L, 1L);

        verify(stepMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verify(stepExecutionService, never()).executeStep(any(), any(), any(), any());
        verify(organizationContext).setOrganizationId(1L);
        verify(organizationContext).clear();
    }

    @Test
    void cancellationBetweenStepsMustStopRemainingSteps() {
        SimulationRunRecord initial = run("PENDING");
        SimulationRunRecord running = run("RUNNING");
        SimulationRunRecord cancelled = run("CANCELLED");
        when(runMapper.selectById(7L)).thenReturn(initial, running, cancelled);
        when(runMapper.update(any(), any())).thenReturn(1);
        SimulationSceneStep first = step(1);
        SimulationSceneStep second = step(2);
        when(stepMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, second));

        service.executeAsync(7L, null, 30L, 1L);

        verify(stepExecutionService, times(1)).executeStep(initial, first, null, 30L);
        verify(stepExecutionService, never()).executeStep(initial, second, null, 30L);
        verify(organizationContext).clear();
    }

    @Test
    void successfulRunMustExecuteAllStepsAndFinishConditionally() {
        SimulationRunRecord initial = run("PENDING");
        SimulationRunRecord running = run("RUNNING");
        when(runMapper.selectById(7L)).thenReturn(initial, running, running);
        when(runMapper.update(any(), any())).thenReturn(1);
        when(stepMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(step(1), step(2)));

        service.executeAsync(7L, null, 30L, 1L);

        verify(stepExecutionService, times(2)).executeStep(any(), any(), any(), any());
        verify(runMapper, times(4)).update(any(), any());
        verify(organizationContext).clear();
    }
    private SimulationRunRecord run(String status) {
        SimulationRunRecord run = new SimulationRunRecord();
        run.setId(7L);
        run.setOrganizationId(1L);
        run.setStatus(status);
        run.setTotalSteps(2);
        run.setSuccessSteps(0);
        return run;
    }

    private SimulationSceneStep step(int order) {
        SimulationSceneStep step = new SimulationSceneStep();
        step.setId((long) order);
        step.setSceneId(1L);
        step.setStepOrder(order);
        step.setPointId(100L);
        step.setAction("SLEEP");
        step.setDelayMs(0);
        return step;
    }
}