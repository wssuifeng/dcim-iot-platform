package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.PointDefinitionCreateDTO;
import com.example.platform.dto.PointDefinitionUpdateDTO;
import com.example.platform.entity.PointDefinition;
import com.example.platform.service.PointDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 点位定义接口层（全局共享的点位元数据字典）
 *
 * <p>写操作仅 PLATFORM_ADMIN；任何已认证用户可读（点位字典是构建设备点位的数据源）。
 * 前端仅用于查询点位分类与展示名称/单位模板；安全边界仍以服务层为准。</p>
 */
@Tag(name = "点位定义", description = "全局点位字典管理（PLATFORM_ADMIN 写，已认证用户只读）")
@RestController
@RequestMapping("/point-definitions")
@Validated
public class PointDefinitionController {

    private final PointDefinitionService pointDefinitionService;

    public PointDefinitionController(PointDefinitionService pointDefinitionService) {
        this.pointDefinitionService = pointDefinitionService;
    }

    @Operation(summary = "分页查询点位定义", description = "支持按 pointCode/pointName 关键字与数据类型过滤")
    @GetMapping("/page")
    public Result<Page<PointDefinition>> page(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "关键字（可选）") @RequestParam(required = false) String keyword,
            @Parameter(description = "数据类型 NUMBER/BOOLEAN/STRING（可选）") @RequestParam(required = false) String dataType) {
        return Result.success(pointDefinitionService.pagePointDefinitions(current, size, keyword, dataType));
    }

    @Operation(summary = "查询点位详情")
    @GetMapping("/{id}")
    public Result<PointDefinition> getById(
            @Parameter(description = "点位ID", required = true) @PathVariable Long id) {
        return Result.success(pointDefinitionService.getPointDefinitionOrThrow(id));
    }

    @Operation(summary = "新增点位定义", description = "PLATFORM_ADMIN 专用；pointCode 全局唯一")
    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @AuditLog(operation = "CREATE_POINT_DEFINITION", detail = "编码:#createDTO.pointCode")
    public Result<PointDefinition> create(@Valid @RequestBody PointDefinitionCreateDTO createDTO) {
        return Result.success(pointDefinitionService.createPointDefinition(createDTO));
    }

    @Operation(summary = "更新点位定义", description = "PLATFORM_ADMIN 专用；pointCode 不可改")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @AuditLog(operation = "UPDATE_POINT_DEFINITION", detail = "点位ID:#id")
    public Result<PointDefinition> update(
            @Parameter(description = "点位ID", required = true) @PathVariable Long id,
            @Valid @RequestBody PointDefinitionUpdateDTO updateDTO) {
        return Result.success(pointDefinitionService.updatePointDefinition(id, updateDTO));
    }

}
