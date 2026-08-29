package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.dto.MetricLatestVO;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.MetricLatestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 指标最新值接口层
 *
 * <p>任何已认证用户可读，须以 deviceId 入参；服务层按授权设备范围过滤。
 * 跨设备聚合（看板）由后续 BIZ-TRAE-02 看板模块实现。</p>
 */
@Tag(name = "指标最新值", description = "设备指标最新值快照查询（已认证用户按范围读）")
@RestController
@RequestMapping("/metric-latest")
@Validated
public class MetricLatestController {

    private final MetricLatestService metricLatestService;
    private final CurrentUserContext currentUserContext;

    public MetricLatestController(MetricLatestService metricLatestService,
                                 CurrentUserContext currentUserContext) {
        this.metricLatestService = metricLatestService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "查询某设备全部指标最新值", description = "按授权设备范围过滤")
    @GetMapping("/device/{deviceId}")
    public Result<List<MetricLatestVO>> listByDevice(
            @Parameter(description = "设备ID", required = true) @PathVariable Integer deviceId) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(metricLatestService.listByDevice(deviceId, user.userId()));
    }

    @Operation(summary = "按条件分页查询指标最新值", description = "必须指定 deviceId")
    @GetMapping("/page")
    public Result<Page<MetricLatestVO>> page(
            @Parameter(description = "当前页码", example = "1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "设备ID", required = true) @RequestParam Integer deviceId,
            @Parameter(description = "指标ID（可选）") @RequestParam(required = false) Long pointId) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(metricLatestService.pageLatest(
                user.userId(), current, size, deviceId, pointId));
    }
}