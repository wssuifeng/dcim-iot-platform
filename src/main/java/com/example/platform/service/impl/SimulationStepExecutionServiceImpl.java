package com.example.platform.service.impl;

import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.MetricSampleDTO;
import com.example.platform.entity.SimulationGeneratedDataRef;
import com.example.platform.entity.SimulationRunRecord;
import com.example.platform.entity.SimulationSceneStep;
import com.example.platform.mapper.SimulationGeneratedDataRefMapper;
import com.example.platform.service.MetricSampleService;
import com.example.platform.service.SimulationStepExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/*
 * 类功能:模拟场景步骤执行服务实现类，按步骤生成指标采样并记录数据引用
 *
 * 【告警开关（D06）】模拟写入默认不触发告警评估，由 simulation.alarm-trigger-enabled
 *   控制；开启后模拟数据走完整告警链路（用于校验规则生效）。
 * */
@Service
@RequiredArgsConstructor
public class SimulationStepExecutionServiceImpl implements SimulationStepExecutionService {
    private static final String ACTION_WRITE_SAMPLE = "WRITE_SAMPLE";
    private static final String ACTION_SLEEP = "SLEEP";

    private final MetricSampleService metricSampleService;
    private final SimulationGeneratedDataRefMapper generatedDataRefMapper;

    @Value("${simulation.alarm-trigger-enabled:false}")
    private boolean alarmTriggerEnabled;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeStep(SimulationRunRecord run, SimulationSceneStep step,
                            Integer overrideDeviceId, Long currentUserId) {
        String action = StringUtils.hasText(step.getAction())
                ? step.getAction().toUpperCase() : ACTION_WRITE_SAMPLE;
        Integer deviceId = step.getTargetDeviceId() != null
                ? step.getTargetDeviceId() : overrideDeviceId;

        if (ACTION_SLEEP.equals(action)) {
            generatedDataRefMapper.insert(buildRef(run, step, deviceId, null));
            return;
        }
        if (!ACTION_WRITE_SAMPLE.equals(action)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "不支持的步骤动作: " + action);
        }
        if (deviceId == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "步骤未指定目标设备，也无法从运行启动参数继承: stepOrder=" + step.getStepOrder());
        }

        MetricSampleDTO sampleDTO = new MetricSampleDTO();
        sampleDTO.setDeviceId(deviceId);
        sampleDTO.setPointId(step.getPointId());
        sampleDTO.setSampleValue(step.getSampleValue());
        sampleDTO.setSampleTime(LocalDateTime.now());
        var savedSample = metricSampleService.saveSample(sampleDTO, currentUserId, alarmTriggerEnabled);

        generatedDataRefMapper.insert(buildRef(run, step, deviceId,
                savedSample == null ? null : savedSample.getId()));
    }

    private SimulationGeneratedDataRef buildRef(SimulationRunRecord run,
                                                  SimulationSceneStep step,
                                                  Integer deviceId,
                                                  Long metricSampleId) {
        SimulationGeneratedDataRef ref = new SimulationGeneratedDataRef();
        ref.setRunId(run.getId());
        ref.setStepId(step.getId());
        ref.setDeviceId(deviceId);
        ref.setPointId(step.getPointId());
        ref.setMetricSampleId(metricSampleId);
        ref.setAction(StringUtils.hasText(step.getAction())
                ? step.getAction().toUpperCase() : ACTION_WRITE_SAMPLE);
        ref.setSampleValue(step.getSampleValue());
        ref.setGeneratedAt(LocalDateTime.now());
        return ref;
    }
}