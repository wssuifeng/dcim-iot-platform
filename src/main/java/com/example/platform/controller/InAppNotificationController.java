package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.InAppNotificationVO;
import com.example.platform.entity.InAppNotification;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.InAppNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 站内通知接口层
 *
 * <p>契约：InAppNotification 管读取状态；NotificationLog 仅作发送日志。</p>
 *
 * <p>权限边界：
 * <ul>
 *   <li>查询/标记已读：仅接收用户本人（VIEWER 可读自己的通知）</li>
 * </ul>
 */
@Tag(name = "站内通知", description = "用户站内通知查询与已读状态管理")
@RestController
@RequestMapping("/in-app-notifications")
@Validated
public class InAppNotificationController {

    private final InAppNotificationService inAppNotificationService;
    private final CurrentUserContext currentUserContext;

    public InAppNotificationController(InAppNotificationService inAppNotificationService,
                                       CurrentUserContext currentUserContext) {
        this.inAppNotificationService = inAppNotificationService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "分页查询当前用户的通知", description = "默认按创建时间倒序；可按未读状态过滤")
    @GetMapping("/page")
    public Result<Page<InAppNotificationVO>> page(
            @Parameter(description = "当前页码", example = "1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "已读状态：0-未读 1-已读") @RequestParam(required = false) Integer isRead) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(inAppNotificationService.pageMyNotifications(
                user.userId(), current, size, isRead));
    }

    @Operation(summary = "标记单条通知已读", description = "仅接收用户本人可标记")
    @PostMapping("/{id}/read")
    @AuditLog(operation = "MARK_NOTIFICATION_READ", detail = "notificationId:#id")
    public Result<InAppNotification> markAsRead(@Parameter(description = "通知ID", required = true)
                                                @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(inAppNotificationService.markAsRead(id, user.userId()));
    }

    @Operation(summary = "一键标记全部未读通知为已读", description = "返回成功标记的条数")
    @PostMapping("/read-all")
    @AuditLog(operation = "MARK_ALL_NOTIFICATIONS_READ", detail = "")
    public Result<Integer> markAllAsRead() {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(inAppNotificationService.markAllAsRead(user.userId()));
    }

    @Operation(summary = "查询当前用户未读通知数量", description = "用于导航提醒未读数")
    @GetMapping("/unread/count")
    public Result<Integer> countUnread() {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(inAppNotificationService.countUnread(user.userId()));
    }
}