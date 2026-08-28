package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.RoomComparisonVO;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.RoomComparisonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 机房对比接口层（跨机房检索对比页数据源）
 *
 * <p>权限边界：BUSINESS_READ；roomIds 逗号分隔 2-4 个，机房不存在直接 4040。</p>
 */
@Tag(name = "机房对比", description = "多机房设备/告警对比聚合（跨机房检索对比页数据源）")
@RestController
@RequestMapping("/dashboard/comparison")
@Validated
public class DashboardComparisonController {

    private final RoomComparisonService roomComparisonService;
    private final CurrentUserContext currentUserContext;

    public DashboardComparisonController(RoomComparisonService roomComparisonService,
                                         CurrentUserContext currentUserContext) {
        this.roomComparisonService = roomComparisonService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "多机房对比聚合", description = "roomIds 逗号分隔（2-4 个）；可选 typeId/deviceName 过滤设备；"
            + "start/end 为告警统计时间范围（ISO 时间，必填）；返回每机房设备统计+在线率+活动告警+级别分布")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public Result<List<RoomComparisonVO>> compare(
            @Parameter(description = "机房ID列表，逗号分隔", example = "4,5", required = true)
            @NotBlank(message = "roomIds 不能为空") @RequestParam String roomIds,
            @Parameter(description = "设备类型ID（可选）") @RequestParam(required = false) Integer typeId,
            @Parameter(description = "设备名称模糊（可选）") @RequestParam(required = false) String deviceName,
            @Parameter(description = "告警统计开始时间（ISO）", example = "2026-08-28T00:00:00", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "告警统计结束时间（ISO）", example = "2026-08-29T00:00:00", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        List<Long> ids = Arrays.stream(roomIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
        return Result.success(roomComparisonService.compareRooms(
                user.userId(), ids, typeId, deviceName, startTime, endTime));
    }
}
