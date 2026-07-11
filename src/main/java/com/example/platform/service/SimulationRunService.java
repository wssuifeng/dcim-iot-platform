package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.SimulationGeneratedDataRefVO;
import com.example.platform.dto.SimulationRunStartDTO;
import com.example.platform.dto.SimulationRunVO;
import com.example.platform.entity.SimulationRunRecord;

import java.util.List;

/*
 * 类功能:模拟运行服务接口
 *
 * 【角色与范围】
 *   - 启动/取消运行：ORG_ADMIN/OPERATOR（按场景组织/机房范围）
 *   - 查询运行记录/事件：ORG_ADMIN/OPERATOR/VIEWER（按授权范围过滤）
 *
 * 【执行模型】启动请求先持久化 PENDING 记录，事务提交后由后台执行器运行步骤。
 *
 * 【生产门控】
 *   - 写入必须经 simulation.write-enabled 开关放行，生产环境由 ProductionSafetyGuard 阻断；
 *   - 禁用状态下启动运行抛 BusinessException(SIMULATION_WRITE_DISABLED)。
 * */
public interface SimulationRunService extends IService<SimulationRunRecord> {

    SimulationRunVO startRun(SimulationRunStartDTO dto, Long currentUserId);

    SimulationRunVO cancelRun(Long runId, Long currentUserId);

    SimulationRunVO getRunDetail(Long runId, Long currentUserId);

    Page<SimulationRunVO> pageRuns(Long currentUserId, Integer current, Integer size,
                                   Long sceneId, String status);

    List<SimulationGeneratedDataRefVO> listRunEvents(Long runId, Long currentUserId);
}