package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.dto.SimulationSceneCreateDTO;
import com.example.platform.dto.SimulationSceneStepDTO;
import com.example.platform.dto.SimulationSceneUpdateDTO;
import com.example.platform.dto.SimulationSceneVO;
import com.example.platform.entity.PointDefinition;
import com.example.platform.entity.SimulationScene;
import com.example.platform.entity.SimulationSceneStep;
import com.example.platform.mapper.SimulationSceneMapper;
import com.example.platform.mapper.SimulationSceneStepMapper;
import com.example.platform.service.DataScopeService;
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

/*
 * 类功能:模拟场景服务实现类
 *
 * 【数据范围依据】共享契约 §3：数据范围必须依赖 organizationId 和 cloudRoomId，
 *   不依赖 Device.location。所有查询/写入必须经过 ensureSceneScope。
 *
 * 【PLATFORM_ADMIN】平台管理员可跨组织管理场景（用于平台级运维场景模板）；
 *   但不放宽业务级越权写入。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationSceneServiceImpl
        extends ServiceImpl<SimulationSceneMapper, SimulationScene>
        implements SimulationSceneService {

    private final SimulationSceneStepMapper simulationSceneStepMapper;
    private final DataScopeService dataScopeService;
    private final PointDefinitionService pointDefinitionService;

    private static final int STATUS_ENABLED = 0;
    private static final int STATUS_DISABLED = 1;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimulationSceneVO createScene(SimulationSceneCreateDTO dto, Long currentUserId) {
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        Long organizationId = dto.getOrganizationId();
        if (organizationId == null) {
            // 默认绑定调用方当前组织（ORG_ADMIN 单组织授权场景）
            if (context.isPlatformAdmin()) {
                throw new BusinessException(ResultCode.INVALID_PARAMETER,
                        "PLATFORM_ADMIN 创建场景必须显式指定 organizationId");
            }
            if (context.getOrganizationIds().isEmpty()) {
                throw new BusinessException(ResultCode.DATA_SCOPE_NOT_ASSIGNED,
                        "当前用户未授权任何组织");
            }
            organizationId = context.getOrganizationIds().iterator().next();
        }
        ensureOrganizationAccess(context, organizationId);
        if (dto.getCloudRoomId() != null) {
            ensureCloudRoomAccess(context, dto.getCloudRoomId());
        }
        validateDeviceTypeCode(dto.getDeviceTypeCode());

        checkSceneCodeUnique(organizationId, dto.getSceneCode());

        SimulationScene scene = new SimulationScene();
        scene.setSceneCode(dto.getSceneCode());
        scene.setSceneName(dto.getSceneName());
        scene.setDescription(dto.getDescription());
        scene.setOrganizationId(organizationId);
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
                addStepInternal(scene.getId(), stepDto, order);
            }
        }

        log.info("模拟场景创建成功 sceneId={} sceneCode={} orgId={}",
                scene.getId(), scene.getSceneCode(), organizationId);
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
            DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
            ensureCloudRoomAccess(context, dto.getCloudRoomId());
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
                                              Long organizationId, Long cloudRoomId,
                                              Integer status, String keyword) {
        int[] params = PaginationUtils.validate(current, size);
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);

        Page<SimulationScene> page = new Page<>(params[0], params[1]);
        LambdaQueryWrapper<SimulationScene> wrapper = new LambdaQueryWrapper<>();

        if (!context.isPlatformAdmin()) {
            if (context.getOrganizationIds().isEmpty()) {
                return emptyVOPage(params[0], params[1]);
            }
            wrapper.in(SimulationScene::getOrganizationId, context.getOrganizationIds());
        }
        if (organizationId != null) {
            ensureOrganizationAccess(context, organizationId);
            wrapper.eq(SimulationScene::getOrganizationId, organizationId);
        }
        if (cloudRoomId != null) {
            ensureCloudRoomAccess(context, cloudRoomId);
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
        return addStepInternal(scene.getId(), dto, dto.getStepOrder());
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
        if (currentUserId != null) {
            DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
            ensureSceneScope(scene, context);
        }
        return scene;
    }

    // ===================== 内部工具 =====================

    private SimulationSceneStep addStepInternal(Long sceneId, SimulationSceneStepDTO dto, Integer stepOrder) {
        PointDefinition point = pointDefinitionService.getPointDefinitionOrThrow(dto.getPointId());
        validateSampleValue(point, dto.getSampleValue());

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

    private void ensureSceneScope(SimulationScene scene, DataScopeContext context) {
        if (context.isPlatformAdmin()) {
            return;
        }
        Long orgId = scene.getOrganizationId();
        if (!context.getOrganizationIds().contains(orgId)) {
            throw new DataScopeViolationException("SimulationScene", String.valueOf(scene.getId()));
        }
        Long roomId = scene.getCloudRoomId();
        if (roomId != null && "ROOMS".equals(context.getScopeType())
                && !context.getCloudRoomIds().contains(roomId)) {
            throw new DataScopeViolationException("SimulationScene", String.valueOf(scene.getId()));
        }
    }

    private void ensureOrganizationAccess(DataScopeContext context, Long organizationId) {
        if (context.isPlatformAdmin()) {
            return;
        }
        if (!context.hasOrganizationAccess(organizationId)) {
            throw new DataScopeViolationException("Organization", String.valueOf(organizationId));
        }
    }

    private void ensureCloudRoomAccess(DataScopeContext context, Long cloudRoomId) {
        if (context.isPlatformAdmin()) {
            return;
        }
        if (!context.hasCloudRoomAccess(cloudRoomId)) {
            throw new DataScopeViolationException("CloudRoom", String.valueOf(cloudRoomId));
        }
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

    private void checkSceneCodeUnique(Long organizationId, String sceneCode) {
        LambdaQueryWrapper<SimulationScene> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SimulationScene::getOrganizationId, organizationId)
                .eq(SimulationScene::getSceneCode, sceneCode);
        if (count(wrapper) > 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "场景编码在同一组织内已存在: " + sceneCode);
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

    private Page<SimulationSceneVO> emptyVOPage(int current, int size) {
        Page<SimulationSceneVO> empty = new Page<>(current, size);
        empty.setRecords(new ArrayList<>());
        empty.setTotal(0);
        return empty;
    }
}