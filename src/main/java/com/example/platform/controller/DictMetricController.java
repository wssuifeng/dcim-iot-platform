package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.entity.DictMetric;
import com.example.platform.service.DictMetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 指标字典接口层，提供指标字典的分页查询、增删改及缓存刷新等RESTful接口
 */
@Tag(name = "指标字典", description = "指标元数据 CRUD 与缓存刷新")
@RestController
@RequestMapping("/metric")
@RequiredArgsConstructor
@Validated
public class DictMetricController {

    private final DictMetricService dictMetricService;

    @Operation(summary = "分页查询指标字典", description = "返回指标元数据的分页结果")
    @GetMapping("/page")
    public Result<Page<DictMetric>> page(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") Integer size) {
        int[] pageParams = PaginationUtils.validate(current, size);
        return Result.success(dictMetricService.page(new Page<>(pageParams[0], pageParams[1])));
    }

    @Operation(summary = "新增指标", description = "新增指标元数据并维护 Redis 缓存一致性")
    @PostMapping
    @AuditLog(operation = "CREATE_METRIC", detail = "指标编码:#metric.metricCode, 指标名称:#metric.metricName")
    public Result<Void> add(@Validated @RequestBody DictMetric metric) {
        dictMetricService.save(metric);
        return Result.success();
    }

    @Operation(summary = "更新指标", description = "更新指标元数据并维护 Redis 缓存一致性")
    @PutMapping
    @AuditLog(operation = "UPDATE_METRIC", detail = "指标ID:#metric.id")
    public Result<Void> update(@Validated @RequestBody DictMetric metric) {
        dictMetricService.updateById(metric);
        return Result.success();
    }

    @Operation(summary = "删除指标", description = "删除指标元数据并维护 Redis 缓存一致性")
    @DeleteMapping("/{id}")
    @AuditLog(operation = "DELETE_METRIC", detail = "指标ID:#id")
    public Result<Void> delete(
            @Parameter(description = "指标ID", required = true) @PathVariable Integer id) {
        dictMetricService.removeById(id);
        return Result.success();
    }

    @Operation(summary = "手动刷新指标缓存", description = "强制重新加载 Redis 中的指标字典缓存")
    @PostMapping("/cache/refresh")
    @AuditLog(operation = "REFRESH_METRIC_CACHE", detail = "手动刷新指标字典缓存")
    public Result<Void> refreshCache() {
        dictMetricService.refreshCache();
        return Result.success();
    }
}
