package com.example.platform.service;

import com.example.platform.entity.SimulationRunRecord;
import com.example.platform.entity.SimulationSceneStep;

public interface SimulationStepExecutionService {
    void executeStep(SimulationRunRecord run, SimulationSceneStep step,
                     Integer overrideDeviceId, Long currentUserId);
}