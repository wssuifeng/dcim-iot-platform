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
 * 【角色与范围】写操作由控制器 RBAC（ADMIN）保证；查询任何已认证用户。
 * */
public interface SimulationSceneService extends IService<SimulationScene> {

    SimulationSceneVO createScene(SimulationSceneCreateDTO dto, Long currentUserId);

    SimulationSceneVO updateScene(Long sceneId, SimulationSceneUpdateDTO dto, Long currentUserId);

    void deleteScene(Long sceneId, Long currentUserId);

    SimulationSceneVO getSceneDetail(Long sceneId, Long currentUserId);

    Page<SimulationSceneVO> pageScenes(Long currentUserId, Integer current, Integer size,
                                      Long cloudRoomId,
                                      Integer status, String keyword);

    SimulationSceneVO setSceneStatus(Long sceneId, Integer status, Long currentUserId);

    SimulationSceneStep addStep(Long sceneId, SimulationSceneStepDTO dto, Long currentUserId);

    void deleteStep(Long stepId, Long currentUserId);

    List<SimulationSceneStep> listSteps(Long sceneId, Long currentUserId);

    /*
     * 方法功能:查询目标场景，不存在抛业务异常，
     * 返回场景实体便于上游调用。
     * */
    SimulationScene getSceneOrThrow(Long sceneId, Long currentUserId);
}
