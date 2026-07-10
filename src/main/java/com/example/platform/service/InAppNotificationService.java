package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.InAppNotificationVO;
import com.example.platform.entity.InAppNotification;

import java.util.Set;

/*
 * 类功能:站内通知服务接口，承担用户已读状态管理
 *
 * 【契约】共享契约 §5：InAppNotification 管读取状态，
 *   NotificationLog 仅作为发送日志，不承担已读状态。
 *
 * 【角色与范围】
 *   - 通知归属于接收用户；VIEWER 可读自己的通知
 *   - 标记已读：仅接收用户本人
 * */
public interface InAppNotificationService extends IService<InAppNotification> {

    InAppNotification createForUser(Long recipientUserId, String title, String content,
                                    String sourceType, Long sourceId);

    Page<InAppNotificationVO> pageMyNotifications(Long currentUserId, Integer current,
                                                   Integer size, Integer isRead);

    InAppNotification markAsRead(Long notificationId, Long currentUserId);

    int markAllAsRead(Long currentUserId);

    int countUnread(Long currentUserId);

    /*
     * 方法功能:为已按告警组织和机房范围解析的接收用户创建通知
     * */
    void notifyAlarmEvent(Long alarmEventId, Set<Long> recipientUserIds,
                          String title, String content);
}
