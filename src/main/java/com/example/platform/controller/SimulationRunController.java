package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.SimulationGeneratedDataRefVO;
import com.example.platform.dto.SimulationRunStartDTO;
import com.example.platform.dto.SimulationRunVO;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.SimulationRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模拟运行接口层
 *
 * <p>权限边界：
 * <ul>
 *   <li>启动/取消：ADMIN/OPERATOR（按场景与设备范围校验）</li>
 *   <li>查询运行记录/事件：ADMIN/OPERATOR/VIEWER（按授权组织范围过滤）</li>
 * </ul>
 * 安全边界由 Service 层统一校验，前端显隐仅用于交互。
 *
 * <p>生产门控：写入受 simulation.write-enabled 开关控制，生产环境由 ProductionSafetyGuard 阻断。
 */
@Tag(name = "模拟运行", description = "模拟运行启动/取消/查询，生成数据可追溯（ADMIN/OPERATOR 启动，已认证按范围读）")
@RestController
@RequestMapping("/simulation/runs")
@Validated
public class SimulationRunController {

    private final SimulationRunService simulationRunService;
    private final CurrentUserContext currentUserContext;

    public SimulationRunController(SimulationRunService simulationRunService,
                                  CurrentUserContext currentUserContext) {
        this.simulationRunService = simulationRunService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "启动模拟运行", description = "ADMIN/OPERATOR；受 simulation.write-enabled 开关控制")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @AuditLog(operation = "START_SIMULATION_RUN", detail = "sceneId:#dto.sceneId")
    public Result<SimulationRunVO> start(@Valid @RequestBody SimulationRunStartDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(simulationRunService.startRun(dto, user.userId()));
    }

    @Operation(summary = "取消模拟运行", description = "ADMIN/OPERATOR；仅未结束的运行可取消")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @AuditLog(operation = "CANCEL_SIMULATION_RUN", detail = "runId:#id")
    public Result<SimulationRunVO> cancel(@Parameter(description = "运行ID", required = true)
                                          @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(simulationRunService.cancelRun(id, user.userId()));
    }

    @Operation(summary = "查询运行记录详情", description = "BUSINESS_READ；按授权范围过滤")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public Result<SimulationRunVO> detail(@Parameter(description = "运行ID", required = true)
                                          @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(simulationRunService.getRunDetail(id, user.userId()));
    }

    @Operation(summary = "分页查询运行记录", description = "BUSINESS_READ；按授权范围过滤")
    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public Result<Page<SimulationRunVO>> page(
            @Parameter(description = "当前页码", example = "1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "场景ID（可选）") @RequestParam(required = false) Long sceneId,
            @Parameter(description = "状态（可选）") @RequestParam(required = false) String status) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(simulationRunService.pageRuns(
                user.userId(), current, size, sceneId, status));
    }

    @Operation(summary = "查询运行生成数据引用事件", description = "BUSINESS_READ；生成数据可追溯到运行记录与 metric_sample")
    @GetMapping("/{id}/events")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public Result<List<SimulationGeneratedDataRefVO>> events(@Parameter(description = "运行ID", required = true)
                                                             @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(simulationRunService.listRunEvents(id, user.userId()));
    }
}