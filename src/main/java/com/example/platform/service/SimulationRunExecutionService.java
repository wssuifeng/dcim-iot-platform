package com.example.platform.service;

public interface SimulationRunExecutionService {
    void executeAsync(Long runId, Integer overrideDeviceId, Long userId);
}
