package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.dto.AlarmNotificationLogVO;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.AlarmNotificationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 告警发送日志接口层（发送结果状态追溯）
 *
 * <p>权限边界：任何已认证用户按数据范围只读；status=1 对 WEBSOCKET 通道表示
 * "已投递到用户队列"，已读状态由站内通知承担（D28）。</p>
 */
@Tag(name = "告警发送日志", description = "告警 WS/邮件发送结果分页查询（按数据范围）")
@RestController
@RequestMapping("/alarm-notification-logs")
public class AlarmNotificationLogController {

    private final AlarmNotificationLogService logService;
    private final CurrentUserContext currentUserContext;

    public AlarmNotificationLogController(AlarmNotificationLogService logService,
                                          CurrentUserContext currentUserContext) {
        this.logService = logService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "分页查询告警发送日志", description = "按告警事件/通道/状态筛选")
    @GetMapping("/page")
    public Result<Page<AlarmNotificationLogVO>> page(
            @Parameter(description = "当前页码", example = "1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "告警事件ID") @RequestParam(required = false) Long alarmEventId,
            @Parameter(description = "通道：WEBSOCKET/EMAIL") @RequestParam(required = false) String channel,
            @Parameter(description = "状态：0待发 1已投递 2失败") @RequestParam(required = false) Integer status) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(logService.pageLogs(user.userId(), current, size,
                alarmEventId, channel, status));
    }
}
