package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.platform.entity.SimulationRunRecord;
import com.example.platform.entity.SimulationSceneStep;
import com.example.platform.mapper.SimulationRunRecordMapper;
import com.example.platform.mapper.SimulationSceneStepMapper;
import com.example.platform.service.SimulationRunExecutionService;
import com.example.platform.service.SimulationStepExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationRunExecutionServiceImpl implements SimulationRunExecutionService {
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final long CANCELLATION_POLL_MILLIS = 200L;

    private final SimulationRunRecordMapper runMapper;
    private final SimulationSceneStepMapper stepMapper;
    private final SimulationStepExecutionService stepExecutionService;

    @Async
    @Override
    public void executeAsync(Long runId, Integer overrideDeviceId, Long userId) {
        try {
            SimulationRunRecord run = runMapper.selectById(runId);
            if (run == null || STATUS_CANCELLED.equals(run.getStatus())) {
                return;
            }
            if (STATUS_PENDING.equals(run.getStatus()) && !markRunning(runId)) {
                return;
            }

            List<SimulationSceneStep> steps = stepMapper.selectList(
                    new LambdaQueryWrapper<SimulationSceneStep>()
                            .eq(SimulationSceneStep::getSceneId, run.getSceneId())
                            .orderByAsc(SimulationSceneStep::getStepOrder));
            int successCount = 0;
            for (SimulationSceneStep step : steps) {
                if (isCancelled(runId) || !waitForDelay(runId, step.getDelayMs())) {
                    return;
                }
                try {
                    stepExecutionService.executeStep(run, step, overrideDeviceId, userId);
                    successCount++;
                    updateProgress(runId, successCount);
                } catch (Exception ex) {
                    markFailed(runId, successCount, truncate(ex.getMessage(), 500));
                    log.error("模拟运行步骤失败 runId={} stepId={} error={}",
                            runId, step.getId(), ex.getMessage());
                    return;
                }
            }
            if (markSuccess(runId, successCount)) {
                log.info("模拟运行结束 runId={} status={} success/total={}/{}",
                        runId, STATUS_SUCCESS, successCount, steps.size());
            } else {
                log.info("模拟运行结束前状态已变化 runId={}，保留并发更新后的状态", runId);
            }
        } catch (Exception ex) {
            markFailed(runId, 0, truncate(ex.getMessage(), 500));
            log.error("模拟运行编排失败 runId={} error={}", runId, ex.getMessage(), ex);
        }
    }

    private boolean markRunning(Long runId) {
        return runMapper.update(null, new LambdaUpdateWrapper<SimulationRunRecord>()
                .eq(SimulationRunRecord::getId, runId)
                .eq(SimulationRunRecord::getStatus, STATUS_PENDING)
                .set(SimulationRunRecord::getStatus, STATUS_RUNNING)
                .set(SimulationRunRecord::getStartTime, LocalDateTime.now())) == 1;
    }

    private boolean isCancelled(Long runId) {
        SimulationRunRecord latest = runMapper.selectById(runId);
        return latest == null || STATUS_CANCELLED.equals(latest.getStatus());
    }

    private boolean waitForDelay(Long runId, Integer delayMillis) {
        long remaining = delayMillis == null ? 0L : delayMillis.longValue();
        while (remaining > 0L) {
            long sleepMillis = Math.min(remaining, CANCELLATION_POLL_MILLIS);
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                markFailed(runId, 0, "模拟运行线程被中断");
                return false;
            }
            remaining -= sleepMillis;
            if (isCancelled(runId)) {
                return false;
            }
        }
        return true;
    }

    private void updateProgress(Long runId, int successCount) {
        runMapper.update(null, new LambdaUpdateWrapper<SimulationRunRecord>()
                .eq(SimulationRunRecord::getId, runId)
                .eq(SimulationRunRecord::getStatus, STATUS_RUNNING)
                .set(SimulationRunRecord::getSuccessSteps, successCount));
    }

    private boolean markSuccess(Long runId, int successCount) {
        return runMapper.update(null, new LambdaUpdateWrapper<SimulationRunRecord>()
                .eq(SimulationRunRecord::getId, runId)
                .eq(SimulationRunRecord::getStatus, STATUS_RUNNING)
                .set(SimulationRunRecord::getSuccessSteps, successCount)
                .set(SimulationRunRecord::getStatus, STATUS_SUCCESS)
                .set(SimulationRunRecord::getEndTime, LocalDateTime.now())) == 1;
    }

    private void markFailed(Long runId, int successCount, String message) {
        runMapper.update(null, new LambdaUpdateWrapper<SimulationRunRecord>()
                .eq(SimulationRunRecord::getId, runId)
                .in(SimulationRunRecord::getStatus, STATUS_PENDING, STATUS_RUNNING)
                .set(SimulationRunRecord::getSuccessSteps, successCount)
                .set(SimulationRunRecord::getStatus, STATUS_FAILED)
                .set(SimulationRunRecord::getErrorMessage, message)
                .set(SimulationRunRecord::getEndTime, LocalDateTime.now()));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
