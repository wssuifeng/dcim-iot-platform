package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.SimulationSceneCreateDTO;
import com.example.platform.dto.SimulationSceneStepDTO;
import com.example.platform.dto.SimulationSceneUpdateDTO;
import com.example.platform.dto.SimulationSceneVO;
import com.example.platform.entity.SimulationSceneStep;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.SimulationSceneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模拟场景接口层
 *
 * <p>权限边界：
 * <ul>
 *   <li>创建/更新/删除/启停/步骤写入：ADMIN</li>
 *   <li>查询：ADMIN/OPERATOR/VIEWER</li>
 * </ul>
 * 安全边界由控制器 RBAC 统一保证，前端显隐仅用于交互。
 */
@Tag(name = "模拟场景", description = "模拟场景定义与步骤管理（ADMIN 写，已认证读）")
@RestController
@RequestMapping("/simulation/scenes")
@Validated
public class SimulationSceneController {

    private final SimulationSceneService simulationSceneService;
    private final CurrentUserContext currentUserContext;

    public SimulationSceneController(SimulationSceneService simulationSceneService,
                                    CurrentUserContext currentUserContext) {
        this.simulationSceneService = simulationSceneService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "创建模拟场景", description = "ADMIN；可同时携带步骤列表")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "CREATE_SIMULATION_SCENE", detail = "sceneCode:#dto.sceneCode")
    public Result<SimulationSceneVO> create(@Valid @RequestBody SimulationSceneCreateDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(simulationSceneService.createScene(dto, user.userId()));
    }

    @Operation(summary = "更新模拟场景", description = "ADMIN；sceneCode 不可变")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "UPDATE_SIMULATION_SCENE", detail = "sceneId:#id")
    public Result<SimulationSceneVO> update(@Parameter(description = "场景ID", required = true)
                                            @PathVariable Long id,
                                            @Valid @RequestBody SimulationSceneUpdateDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(simulationSceneService.updateScene(id, dto, user.userId()));
    }

    @Operation(summary = "删除模拟场景", description = "ADMIN；级联逻辑删除步骤")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "DELETE_SIMULATION_SCENE", detail = "sceneId:#id")
    public Result<Void> delete(@Parameter(description = "场景ID", required = true)
                                @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        simulationSceneService.deleteScene(id, user.userId());
        return Result.success();
    }

    @Operation(summary = "查询场景详情（含步骤列表）", description = "BUSINESS_READ")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public Result<SimulationSceneVO> detail(@Parameter(description = "场景ID", required = true)
                                             @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(simulationSceneService.getSceneDetail(id, user.userId()));
    }

    @Operation(summary = "分页查询模拟场景", description = "BUSINESS_READ")
    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public Result<Page<SimulationSceneVO>> page(
            @Parameter(description = "当前页码", example = "1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "机房ID（可选）") @RequestParam(required = false) Long cloudRoomId,
            @Parameter(description = "状态：0-启用 1-停用") @RequestParam(required = false) Integer status,
            @Parameter(description = "关键字（场景编码或名称）") @RequestParam(required = false) String keyword) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(simulationSceneService.pageScenes(
                user.userId(), current, size, cloudRoomId, status, keyword));
    }

    @Operation(summary = "启停模拟场景", description = "ADMIN；状态 0-启用 1-停用")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "SET_SIMULATION_SCENE_STATUS", detail = "sceneId:#id, status:#status")
    public Result<SimulationSceneVO> setStatus(@Parameter(description = "场景ID", required = true)
                                               @PathVariable Long id,
                                               @Parameter(description = "状态：0-启用 1-停用", required = true)
                                               @RequestParam Integer status) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(simulationSceneService.setSceneStatus(id, status, user.userId()));
    }

    @Operation(summary = "添加场景步骤", description = "ADMIN；步骤顺序 stepOrder 不可与其他步骤重复")
    @PostMapping("/{id}/steps")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "ADD_SIMULATION_SCENE_STEP", detail = "sceneId:#id")
    public Result<SimulationSceneStep> addStep(@Parameter(description = "场景ID", required = true)
                                               @PathVariable Long id,
                                               @Valid @RequestBody SimulationSceneStepDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(simulationSceneService.addStep(id, dto, user.userId()));
    }

    @Operation(summary = "查询场景步骤列表", description = "BUSINESS_READ")
    @GetMapping("/{id}/steps")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public Result<List<SimulationSceneStep>> listSteps(@Parameter(description = "场景ID", required = true)
                                                       @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(simulationSceneService.listSteps(id, user.userId()));
    }

    @Operation(summary = "删除场景步骤", description = "ADMIN")
    @DeleteMapping("/steps/{stepId}")
    @PreAuthorize("hasRole('ADMIN')")
    @AuditLog(operation = "DELETE_SIMULATION_SCENE_STEP", detail = "stepId:#stepId")
    public Result<Void> deleteStep(@Parameter(description = "步骤ID", required = true)
                                   @PathVariable Long stepId) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        simulationSceneService.deleteStep(stepId, user.userId());
        return Result.success();
    }
}