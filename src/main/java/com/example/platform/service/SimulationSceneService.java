package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.SimulationSceneCreateDTO;
import com.example.platform.dto.SimulationSceneStepDTO;
import com.example.platform.dto.SimulationSceneUpdateDTO;
import com.example.platform.dto.SimulationSceneVO;
import com.example.platform.entity.SimulationScene;
import com.example.platform.entity.SimulationSceneStep;

import java.util.List;

/*
 * 类功能:模拟场景服务接口
 *
 * 【角色与范围】
 *   - 场景管理（创建/更新/删除/启停/步骤写入）：ORG_ADMIN（按 organizationId 范围）
 *   - 查询：ORG_ADMIN/OPERATOR/VIEWER（按授权组织范围过滤）
 *
 * 安全边界由服务层 ensureSceneScope 校验，不依赖前端显隐。
 * */
public interface SimulationSceneService extends IService<SimulationScene> {

    SimulationSceneVO createScene(SimulationSceneCreateDTO dto, Long currentUserId);

    SimulationSceneVO updateScene(Long sceneId, SimulationSceneUpdateDTO dto, Long currentUserId);

    void deleteScene(Long sceneId, Long currentUserId);

    SimulationSceneVO getSceneDetail(Long sceneId, Long currentUserId);

    Page<SimulationSceneVO> pageScenes(Long currentUserId, Integer current, Integer size,
                                      Long organizationId, Long cloudRoomId,
                                      Integer status, String keyword);

    SimulationSceneVO setSceneStatus(Long sceneId, Integer status, Long currentUserId);

    SimulationSceneStep addStep(Long sceneId, SimulationSceneStepDTO dto, Long currentUserId);

    void deleteStep(Long stepId, Long currentUserId);

    List<SimulationSceneStep> listSteps(Long sceneId, Long currentUserId);

    /*
     * 方法功能:校验当前用户对目标场景有权访问（含 PLATFORM_ADMIN 放行），
     * 返回场景实体便于上游调用。未授权抛 DataScopeViolationException。
     * */
    SimulationScene getSceneOrThrow(Long sceneId, Long currentUserId);
}