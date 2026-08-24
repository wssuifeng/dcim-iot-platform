package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.dto.SimulationGeneratedDataRefVO;
import com.example.platform.dto.SimulationRunStartDTO;
import com.example.platform.dto.SimulationRunVO;
import com.example.platform.entity.CloudRoom;
import com.example.platform.entity.Device;
import com.example.platform.entity.PointDefinition;
import com.example.platform.entity.SimulationGeneratedDataRef;
import com.example.platform.entity.SimulationRunRecord;
import com.example.platform.entity.SimulationScene;
import com.example.platform.entity.SimulationSceneStep;
import com.example.platform.mapper.SimulationGeneratedDataRefMapper;
import com.example.platform.mapper.SimulationRunRecordMapper;
import com.example.platform.mapper.SimulationSceneStepMapper;
import com.example.platform.service.CloudRoomService;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.DeviceScopeHelper;
import com.example.platform.service.PointDefinitionService;
import com.example.platform.service.SimulationRunExecutionService;
import com.example.platform.service.SimulationRunService;
import com.example.platform.service.SimulationSceneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/*
 * 类功能:模拟运行服务实现类
 *
 * 【生产门控】模拟写入必须经 simulation.write-enabled 开关放行，
 *   生产环境由 ProductionSafetyGuard 强制阻断启用。
 *   开关关闭时启动运行抛 BusinessException(SIMULATION_WRITE_DISABLED)。
 *
 * 【数据生成】运行提交后由异步执行服务按步骤顺序调用 MetricSampleService 写入指标采样，
 *   并把生成的 metric_sample.id 写入 simulation_generated_data_ref。
 *   前端与脚本不能直接伪造核心事实——所有写入路径必须经过本服务。
 *
 * 【数据可追溯】SimulationGeneratedDataRef.metric_sample_id 是生成数据可追溯的核心依据，
 *   保留策略由 MetricRetentionPolicy 统一回收；本服务不做物理删除。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationRunServiceImpl
        extends ServiceImpl<SimulationRunRecordMapper, SimulationRunRecord>
        implements SimulationRunService {

    private final SimulationSceneService simulationSceneService;
    private final SimulationSceneStepMapper simulationSceneStepMapper;
    private final SimulationGeneratedDataRefMapper simulationGeneratedDataRefMapper;
    private final DataScopeService dataScopeService;
    private final DeviceScopeHelper deviceScopeHelper;
    private final PointDefinitionService pointDefinitionService;
    private final SimulationRunExecutionService runExecutionService;
    private final CloudRoomService cloudRoomService;
    private final ObjectMapper objectMapper;

    @Value("${simulation.write-enabled:false}")
    private boolean writeEnabled;

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimulationRunVO startRun(SimulationRunStartDTO dto, Long currentUserId) {
        if (!writeEnabled) {
            log.warn("模拟写入开关关闭，启动运行被拒绝 userId={} sceneId={}",
                    currentUserId, dto.getSceneId());
            throw new BusinessException(ResultCode.SIMULATION_WRITE_DISABLED,
                    "模拟写入已禁用，请联系平台管理员开启 simulation.write-enabled");
        }

        SimulationScene scene = simulationSceneService.getSceneOrThrow(dto.getSceneId(), currentUserId);
        if (scene.getStatus() != null && scene.getStatus() != 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "场景已停用，无法启动运行: " + dto.getSceneId());
        }

        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        Long organizationId = scene.getOrganizationId();
        Long cloudRoomId = dto.getCloudRoomId() != null ? dto.getCloudRoomId() : scene.getCloudRoomId();

        // 目标机房校验：请求覆盖的机房必须属于场景组织，且当前用户有权访问
        if (cloudRoomId != null) {
            CloudRoom targetRoom = cloudRoomService.getCloudRoomOrThrow(cloudRoomId, currentUserId);
            if (!targetRoom.getOrganizationId().equals(organizationId)) {
                throw new DataScopeViolationException("CloudRoom", String.valueOf(cloudRoomId));
            }
            if (scene.getCloudRoomId() != null && !scene.getCloudRoomId().equals(cloudRoomId)) {
                throw new BusinessException(ResultCode.INVALID_PARAMETER,
                        "运行目标机房与场景机房不一致: sceneRoom=" + scene.getCloudRoomId()
                                + " targetRoom=" + cloudRoomId);
            }
            if ("ROOMS".equals(context.getScopeType())
                    && !context.getCloudRoomIds().contains(cloudRoomId)) {
                throw new DataScopeViolationException("CloudRoom", String.valueOf(cloudRoomId));
            }
        }

        // 加载场景步骤
        LambdaQueryWrapper<SimulationSceneStep> stepWrapper = new LambdaQueryWrapper<>();
        stepWrapper.eq(SimulationSceneStep::getSceneId, scene.getId())
                .orderByAsc(SimulationSceneStep::getStepOrder);
        List<SimulationSceneStep> steps = simulationSceneStepMapper.selectList(stepWrapper);
        if (steps.isEmpty()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "场景不包含任何步骤，无法启动运行: " + scene.getId());
        }

        // 目标设备范围校验（如指定 targetDeviceId）
        if (dto.getTargetDeviceId() != null) {
            Device targetDevice = deviceScopeHelper.ensureDeviceAccess(
                    dto.getTargetDeviceId(), currentUserId);
            if (targetDevice.getOrganizationId() != null
                    && !targetDevice.getOrganizationId().equals(organizationId)
                    && !context.isPlatformAdmin()) {
                throw new DataScopeViolationException("Device",
                        String.valueOf(dto.getTargetDeviceId()));
            }
        }

        // 创建运行记录
        SimulationRunRecord run = new SimulationRunRecord();
        run.setSceneId(scene.getId());
        run.setTriggerUserId(currentUserId);
        run.setOrganizationId(organizationId);
        run.setCloudRoomId(cloudRoomId);
        run.setStatus(STATUS_PENDING);
        run.setTotalSteps(steps.size());
        run.setSuccessSteps(0);
        run.setSceneSnapshot(buildSceneSnapshot(scene, steps));
        if (!save(run)) {
            throw new BusinessException("创建模拟运行记录失败");
        }

        scheduleExecutionAfterCommit(run.getId(), dto.getTargetDeviceId(), currentUserId, organizationId);
        return SimulationRunVO.fromEntity(run);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimulationRunVO cancelRun(Long runId, Long currentUserId) {
        SimulationRunRecord run = getRunOrThrowInternal(runId, currentUserId);
        if (STATUS_SUCCESS.equals(run.getStatus()) || STATUS_CANCELLED.equals(run.getStatus())
                || STATUS_FAILED.equals(run.getStatus())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "运行已结束，不能取消: status=" + run.getStatus());
        }
        LocalDateTime endTime = LocalDateTime.now();
        int updated = baseMapper.update(null, new LambdaUpdateWrapper<SimulationRunRecord>()
                .eq(SimulationRunRecord::getId, runId)
                .in(SimulationRunRecord::getStatus, STATUS_PENDING, STATUS_RUNNING)
                .set(SimulationRunRecord::getStatus, STATUS_CANCELLED)
                .set(SimulationRunRecord::getEndTime, endTime));
        if (updated != 1) {
            SimulationRunRecord latest = getById(runId);
            if (latest != null && STATUS_CANCELLED.equals(latest.getStatus())) {
                return SimulationRunVO.fromEntity(latest);
            }
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "运行状态已变化，取消失败");
        }
        run.setStatus(STATUS_CANCELLED);
        run.setEndTime(endTime);
        log.info("模拟运行取消 runId={} userId={}", runId, currentUserId);
        return SimulationRunVO.fromEntity(run);
    }

    @Override
    public SimulationRunVO getRunDetail(Long runId, Long currentUserId) {
        return SimulationRunVO.fromEntity(getRunOrThrowInternal(runId, currentUserId));
    }

    @Override
    public Page<SimulationRunVO> pageRuns(Long currentUserId, Integer current, Integer size,
                                         Long sceneId, String status) {
        int[] params = PaginationUtils.validate(current, size);
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);

        Page<SimulationRunRecord> page = new Page<>(params[0], params[1]);
        LambdaQueryWrapper<SimulationRunRecord> wrapper = new LambdaQueryWrapper<>();
        if (context.isPlatformAdmin()) {
            // 平台管理员不穿透组织业务数据
            Page<SimulationRunVO> empty = new Page<>(params[0], params[1]);
            empty.setRecords(new ArrayList<>());
            empty.setTotal(0);
            return empty;
        }
        if (context.getOrganizationIds().isEmpty()) {
            Page<SimulationRunVO> empty = new Page<>(params[0], params[1]);
            empty.setRecords(new ArrayList<>());
            empty.setTotal(0);
            return empty;
        }
        wrapper.in(SimulationRunRecord::getOrganizationId, context.getOrganizationIds());
        if ("ROOMS".equals(context.getScopeType())) {
            if (context.getCloudRoomIds() == null || context.getCloudRoomIds().isEmpty()) {
                Page<SimulationRunVO> empty = new Page<>(params[0], params[1]);
                empty.setRecords(new ArrayList<>());
                empty.setTotal(0);
                return empty;
            }
            wrapper.in(SimulationRunRecord::getCloudRoomId, context.getCloudRoomIds());
        }
        if (sceneId != null) {
            wrapper.eq(SimulationRunRecord::getSceneId, sceneId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SimulationRunRecord::getStatus, status.trim().toUpperCase());
        }
        wrapper.orderByDesc(SimulationRunRecord::getCreateTime);

        Page<SimulationRunRecord> result = page(page, wrapper);
        Page<SimulationRunVO> voPage = new Page<>(params[0], params[1]);
        voPage.setTotal(result.getTotal());
        List<SimulationRunVO> records = new ArrayList<>();
        for (SimulationRunRecord record : result.getRecords()) {
            records.add(SimulationRunVO.fromEntity(record));
        }
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public List<SimulationGeneratedDataRefVO> listRunEvents(Long runId, Long currentUserId) {
        getRunOrThrowInternal(runId, currentUserId);
        LambdaQueryWrapper<SimulationGeneratedDataRef> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SimulationGeneratedDataRef::getRunId, runId)
                .orderByAsc(SimulationGeneratedDataRef::getGeneratedAt);
        List<SimulationGeneratedDataRef> refs = simulationGeneratedDataRefMapper.selectList(wrapper);
        List<SimulationGeneratedDataRefVO> result = new ArrayList<>(refs.size());
        for (SimulationGeneratedDataRef ref : refs) {
            SimulationGeneratedDataRefVO vo = SimulationGeneratedDataRefVO.fromEntity(ref);
            PointDefinition point = pointDefinitionService.getById(ref.getPointId());
            if (point != null) {
                vo.setPointCode(point.getPointCode());
                vo.setPointName(point.getPointName());
                vo.setUnit(point.getUnit());
            }
            result.add(vo);
        }
        return result;
    }

    // ===================== 内部辅助 =====================

    private SimulationRunRecord getRunOrThrowInternal(Long runId, Long currentUserId) {
        SimulationRunRecord run = getById(runId);
        if (run == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "模拟运行不存在: " + runId);
        }
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        ensureRunScope(run, context);
        return run;
    }

    private void ensureRunScope(SimulationRunRecord run, DataScopeContext context) {
        if (context.isPlatformAdmin()) {
            throw new DataScopeViolationException("SimulationRunRecord", "platform-no-business-penetration");
        }
        if (!context.getOrganizationIds().contains(run.getOrganizationId())) {
            throw new DataScopeViolationException("SimulationRunRecord", String.valueOf(run.getId()));
        }
        if (run.getCloudRoomId() != null && "ROOMS".equals(context.getScopeType())
                && !context.getCloudRoomIds().contains(run.getCloudRoomId())) {
            throw new DataScopeViolationException("SimulationRunRecord", String.valueOf(run.getId()));
        }
    }

    private void scheduleExecutionAfterCommit(Long runId, Integer overrideDeviceId,
                                              Long currentUserId, Long organizationId) {
        Runnable schedule = () -> runExecutionService.executeAsync(
                runId, overrideDeviceId, currentUserId, organizationId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            schedule.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                schedule.run();
            }
        });
    }

    private String buildSceneSnapshot(SimulationScene scene, List<SimulationSceneStep> steps) {
        try {
            return objectMapper.writeValueAsString(new SceneSnapshot(scene, steps));
        } catch (Exception ex) {
            log.warn("场景快照序列化失败 sceneId={}：{}", scene.getId(), ex.getMessage());
            return null;
        }
    }

    /**
     * 场景快照记录，便于运行复现
     */
    private record SceneSnapshot(SimulationScene scene, List<SimulationSceneStep> steps) {
    }
}
