package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.SimulationSceneCreateDTO;
import com.example.platform.dto.SimulationSceneStepDTO;
import com.example.platform.dto.SimulationSceneUpdateDTO;
import com.example.platform.dto.SimulationSceneVO;
import com.example.platform.entity.CloudRoom;
import com.example.platform.entity.Device;
import com.example.platform.entity.PointDefinition;
import com.example.platform.entity.SimulationScene;
import com.example.platform.entity.SimulationSceneStep;
import com.example.platform.mapper.SimulationSceneMapper;
import com.example.platform.mapper.SimulationSceneStepMapper;
import com.example.platform.service.CloudRoomService;
import com.example.platform.service.DeviceScopeHelper;
import com.example.platform.service.DevicePointMetricContract;
import com.example.platform.service.PointDefinitionService;
import com.example.platform.service.SimulationSceneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 * 类功能:模拟场景服务实现类
 *
 * 【契约依据】共享契约 §6 模拟端契约：场景由后端服务管理，前端不能直接构造核心事实。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationSceneServiceImpl
        extends ServiceImpl<SimulationSceneMapper, SimulationScene>
        implements SimulationSceneService {

    private final SimulationSceneStepMapper simulationSceneStepMapper;
    private final CloudRoomService cloudRoomService;
    private final DeviceScopeHelper deviceScopeHelper;
    private final PointDefinitionService pointDefinitionService;

    private static final int STATUS_ENABLED = 0;
    private static final int STATUS_DISABLED = 1;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimulationSceneVO createScene(SimulationSceneCreateDTO dto, Long currentUserId) {
        if (dto.getCloudRoomId() != null) {
            ensureCloudRoomExists(dto.getCloudRoomId());
        }
        validateDeviceTypeCode(dto.getDeviceTypeCode());

        checkSceneCodeUnique(dto.getSceneCode());

        SimulationScene scene = new SimulationScene();
        scene.setSceneCode(dto.getSceneCode());
        scene.setSceneName(dto.getSceneName());
        scene.setDescription(dto.getDescription());
        scene.setCloudRoomId(dto.getCloudRoomId());
        scene.setDeviceTypeCode(dto.getDeviceTypeCode());
        scene.setStatus(STATUS_ENABLED);
        scene.setCreatedBy(currentUserId);
        if (!save(scene)) {
            throw new BusinessException("创建模拟场景失败");
        }

        if (dto.getSteps() != null && !dto.getSteps().isEmpty()) {
            int order = 0;
            for (SimulationSceneStepDTO stepDto : dto.getSteps()) {
                order++;
                addStepInternal(scene, stepDto, order, currentUserId);
            }
        }

        log.info("模拟场景创建成功 sceneId={} sceneCode={}", scene.getId(), scene.getSceneCode());
        return getSceneDetail(scene.getId(), currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimulationSceneVO updateScene(Long sceneId, SimulationSceneUpdateDTO dto, Long currentUserId) {
        SimulationScene scene = getSceneOrThrow(sceneId, currentUserId);
        if (StringUtils.hasText(dto.getSceneName())) {
            scene.setSceneName(dto.getSceneName());
        }
        if (dto.getDescription() != null) {
            scene.setDescription(dto.getDescription());
        }
        if (dto.getCloudRoomId() != null) {
            ensureCloudRoomExists(dto.getCloudRoomId());
            scene.setCloudRoomId(dto.getCloudRoomId());
        }
        if (StringUtils.hasText(dto.getDeviceTypeCode())) {
            validateDeviceTypeCode(dto.getDeviceTypeCode());
            scene.setDeviceTypeCode(dto.getDeviceTypeCode());
        }
        if (!updateById(scene)) {
            throw new BusinessException("更新模拟场景失败");
        }
        return getSceneDetail(sceneId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteScene(Long sceneId, Long currentUserId) {
        SimulationScene scene = getSceneOrThrow(sceneId, currentUserId);
        // 级联逻辑删除步骤
        LambdaQueryWrapper<SimulationSceneStep> stepWrapper = new LambdaQueryWrapper<>();
        stepWrapper.eq(SimulationSceneStep::getSceneId, sceneId);
        List<SimulationSceneStep> steps = simulationSceneStepMapper.selectList(stepWrapper);
        for (SimulationSceneStep step : steps) {
            simulationSceneStepMapper.deleteById(step.getId());
        }
        if (!removeById(sceneId)) {
            throw new BusinessException("删除模拟场景失败");
        }
        log.info("模拟场景删除成功 sceneId={}", sceneId);
    }

    @Override
    public SimulationSceneVO getSceneDetail(Long sceneId, Long currentUserId) {
        SimulationScene scene = getSceneOrThrow(sceneId, currentUserId);
        SimulationSceneVO vo = SimulationSceneVO.fromEntity(scene);
        vo.setSteps(listSteps(sceneId, currentUserId));
        return vo;
    }

    @Override
    public Page<SimulationSceneVO> pageScenes(Long currentUserId, Integer current, Integer size,
                                              Long cloudRoomId,
                                              Integer status, String keyword) {
        int[] params = PaginationUtils.validate(current, size);

        Page<SimulationScene> page = new Page<>(params[0], params[1]);
        LambdaQueryWrapper<SimulationScene> wrapper = new LambdaQueryWrapper<>();

        if (cloudRoomId != null) {
            ensureCloudRoomExists(cloudRoomId);
            wrapper.eq(SimulationScene::getCloudRoomId, cloudRoomId);
        }
        if (status != null) {
            wrapper.eq(SimulationScene::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String safe = keyword.trim();
            wrapper.and(w -> w.like(SimulationScene::getSceneCode, safe)
                    .or().like(SimulationScene::getSceneName, safe));
        }
        wrapper.orderByDesc(SimulationScene::getCreateTime);

        Page<SimulationScene> resultPage = page(page, wrapper);
        return mapToVOPage(resultPage, params[0], params[1]);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimulationSceneVO setSceneStatus(Long sceneId, Integer status, Long currentUserId) {
        if (status == null || (status != STATUS_ENABLED && status != STATUS_DISABLED)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "场景状态仅允许 0-启用 或 1-停用");
        }
        SimulationScene scene = getSceneOrThrow(sceneId, currentUserId);
        scene.setStatus(status);
        if (!updateById(scene)) {
            throw new BusinessException("更新场景状态失败");
        }
        return getSceneDetail(sceneId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimulationSceneStep addStep(Long sceneId, SimulationSceneStepDTO dto, Long currentUserId) {
        SimulationScene scene = getSceneOrThrow(sceneId, currentUserId);
        if (dto.getStepOrder() == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "步骤顺序不能为空");
        }
        return addStepInternal(scene, dto, dto.getStepOrder(), currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStep(Long stepId, Long currentUserId) {
        SimulationSceneStep step = simulationSceneStepMapper.selectById(stepId);
        if (step == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "步骤不存在: " + stepId);
        }
        getSceneOrThrow(step.getSceneId(), currentUserId);
        simulationSceneStepMapper.deleteById(stepId);
        log.info("模拟场景步骤删除成功 stepId={} sceneId={}", stepId, step.getSceneId());
    }

    @Override
    public List<SimulationSceneStep> listSteps(Long sceneId, Long currentUserId) {
        getSceneOrThrow(sceneId, currentUserId);
        LambdaQueryWrapper<SimulationSceneStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SimulationSceneStep::getSceneId, sceneId)
                .orderByAsc(SimulationSceneStep::getStepOrder);
        return simulationSceneStepMapper.selectList(wrapper);
    }

    @Override
    public SimulationScene getSceneOrThrow(Long sceneId, Long currentUserId) {
        SimulationScene scene = getById(sceneId);
        if (scene == null) {
            throw new BusinessException(ResultCode.SIMULATION_SCENE_NOT_FOUND,
                    "模拟场景不存在: " + sceneId);
        }
        return scene;
    }

    // ===================== 内部工具 =====================

    private SimulationSceneStep addStepInternal(SimulationScene scene, SimulationSceneStepDTO dto,
                                                 Integer stepOrder, Long currentUserId) {
        PointDefinition point = pointDefinitionService.getPointDefinitionOrThrow(dto.getPointId(), currentUserId);
        validateSampleValue(point, dto.getSampleValue());
        if (dto.getTargetDeviceId() != null) {
            Device device = deviceScopeHelper.ensureDeviceAccess(dto.getTargetDeviceId(), currentUserId);
            if (scene.getCloudRoomId() != null
                    && !Objects.equals(scene.getCloudRoomId(), device.getCloudRoomId())) {
                throw new BusinessException(ResultCode.INVALID_PARAMETER,
                        "目标设备与场景机房不一致: deviceId=" + device.getId());
            }
        }

        Long sceneId = scene.getId();

        String action = StringUtils.hasText(dto.getAction()) ? dto.getAction().trim().toUpperCase() : "WRITE_SAMPLE";
        if (!"WRITE_SAMPLE".equals(action) && !"SLEEP".equals(action)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "步骤动作仅允许 WRITE_SAMPLE 或 SLEEP");
        }

        LambdaQueryWrapper<SimulationSceneStep> dup = new LambdaQueryWrapper<>();
        dup.eq(SimulationSceneStep::getSceneId, sceneId)
                .eq(SimulationSceneStep::getStepOrder, stepOrder);
        if (simulationSceneStepMapper.selectCount(dup) > 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "步骤顺序已存在：sceneId=" + sceneId + ", stepOrder=" + stepOrder);
        }

        SimulationSceneStep step = new SimulationSceneStep();
        step.setSceneId(sceneId);
        step.setStepOrder(stepOrder);
        step.setPointId(dto.getPointId());
        step.setTargetDeviceId(dto.getTargetDeviceId());
        step.setSampleValue(dto.getSampleValue());
        step.setAction(action);
        step.setDelayMs(dto.getDelayMs() != null ? dto.getDelayMs() : 0);
        step.setDescription(dto.getDescription());
        if (simulationSceneStepMapper.insert(step) <= 0) {
            throw new BusinessException("写入场景步骤失败");
        }
        return step;
    }

    private CloudRoom ensureCloudRoomExists(Long cloudRoomId) {
        return cloudRoomService.getCloudRoomOrThrow(cloudRoomId, null);
    }

    private void validateDeviceTypeCode(String typeCode) {
        if (!StringUtils.hasText(typeCode)) {
            return;
        }
        if (!DevicePointMetricContract.isSupportedDeviceType(typeCode)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "设备类型编码不在五类支持范围: " + typeCode);
        }
    }

    private void validateSampleValue(PointDefinition point, java.math.BigDecimal value) {
        if (value == null) {
            return;
        }
        String dataType = point.getDataType();
        if ("STRING".equals(dataType)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "STRING 点位不能写入数值型采样值");
        }
        if ("BOOLEAN".equals(dataType)) {
            java.math.BigDecimal zero = java.math.BigDecimal.ZERO;
            java.math.BigDecimal one = java.math.BigDecimal.ONE;
            if (value.compareTo(zero) != 0 && value.compareTo(one) != 0) {
                throw new BusinessException(ResultCode.DATA_VALUE_OUT_OF_RANGE,
                        "BOOLEAN 点位采样值仅允许 0 或 1");
            }
        }
    }

    private void checkSceneCodeUnique(String sceneCode) {
        LambdaQueryWrapper<SimulationScene> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SimulationScene::getSceneCode, sceneCode);
        if (count(wrapper) > 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "场景编码已存在: " + sceneCode);
        }
    }

    private Page<SimulationSceneVO> mapToVOPage(Page<SimulationScene> source, int current, int size) {
        Page<SimulationSceneVO> result = new Page<>(current, size);
        result.setTotal(source.getTotal());
        List<SimulationSceneVO> records = new ArrayList<>();
        for (SimulationScene scene : source.getRecords()) {
            SimulationSceneVO vo = SimulationSceneVO.fromEntity(scene);
            // 列表接口不返回步骤详情，避免 N+1
            vo.setSteps(null);
            records.add(vo);
        }
        result.setRecords(records);
        return result;
    }
}