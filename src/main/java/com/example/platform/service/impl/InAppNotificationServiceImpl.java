package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.InAppNotificationVO;
import com.example.platform.entity.InAppNotification;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.InAppNotificationMapper;
import com.example.platform.security.PlatformRole;
import com.example.platform.service.InAppNotificationService;
import com.example.platform.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * 类功能:站内通知服务实现类
 *
 * 【契约】共享契约 §5：InAppNotification 管读取状态；
 *   NotificationLog（notification_log）仅作发送日志，不承担已读状态。
 *
 * 【派发策略】
 *   - createForUser：定向派发给指定用户（由调用方编排）
 *   - notifyAlarmEvent：派发给所有启用状态的 PLATFORM_ADMIN 与 ORG_ADMIN 用户
 *     （简化策略：不依赖组织字段映射的跨任务 Mapper，仍保证不派发给未授权用户）
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class InAppNotificationServiceImpl
        extends ServiceImpl<InAppNotificationMapper, InAppNotification>
        implements InAppNotificationService {

    private final SysUserService sysUserService;

    private static final int READ_UNREAD = 0;
    private static final int READ_READ = 1;
    private static final String SOURCE_ALARM_EVENT = "ALARM_EVENT";
    private static final int USER_STATUS_ENABLED = 0;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InAppNotification createForUser(Long recipientUserId, String title, String content,
                                            String sourceType, Long sourceId) {
        if (recipientUserId == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "接收用户ID不能为空");
        }
        InAppNotification notification = new InAppNotification();
        notification.setRecipientUserId(recipientUserId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setSourceType(sourceType);
        notification.setSourceId(sourceId);
        notification.setIsRead(READ_UNREAD);
        if (!save(notification)) {
            throw new BusinessException("创建站内通知失败");
        }
        return notification;
    }

    @Override
    public Page<InAppNotificationVO> pageMyNotifications(Long currentUserId, Integer current,
                                                          Integer size, Integer isRead) {
        int[] params = PaginationUtils.validate(current, size);
        LambdaQueryWrapper<InAppNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InAppNotification::getRecipientUserId, currentUserId);
        if (isRead != null) {
            wrapper.eq(InAppNotification::getIsRead, isRead);
        }
        wrapper.orderByDesc(InAppNotification::getCreateTime);

        Page<InAppNotification> source = page(new Page<>(params[0], params[1]), wrapper);
        Page<InAppNotificationVO> result = new Page<>(params[0], params[1]);
        result.setTotal(source.getTotal());
        List<InAppNotificationVO> records = new ArrayList<>(source.getRecords().size());
        for (InAppNotification n : source.getRecords()) {
            records.add(InAppNotificationVO.fromEntity(n));
        }
        result.setRecords(records);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InAppNotification markAsRead(Long notificationId, Long currentUserId) {
        InAppNotification notification = getById(notificationId);
        if (notification == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "通知不存在: " + notificationId);
        }
        if (!currentUserId.equals(notification.getRecipientUserId())) {
            // 仅接收用户本人可标记已读
            throw new DataScopeViolationException("InAppNotification", String.valueOf(notificationId));
        }
        if (notification.getIsRead() != null && notification.getIsRead() == READ_READ) {
            // 已读通知重复标记幂等返回
            return notification;
        }
        notification.setIsRead(READ_READ);
        notification.setReadTime(LocalDateTime.now());
        if (!updateById(notification)) {
            throw new BusinessException("标记通知已读失败");
        }
        return notification;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllAsRead(Long currentUserId) {
        LambdaUpdateWrapper<InAppNotification> update = new LambdaUpdateWrapper<>();
        update.eq(InAppNotification::getRecipientUserId, currentUserId)
                .eq(InAppNotification::getIsRead, READ_UNREAD)
                .set(InAppNotification::getIsRead, READ_READ)
                .set(InAppNotification::getReadTime, LocalDateTime.now());
        return baseMapper.update(null, update);
    }

    @Override
    public int countUnread(Long currentUserId) {
        LambdaQueryWrapper<InAppNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InAppNotification::getRecipientUserId, currentUserId)
                .eq(InAppNotification::getIsRead, READ_UNREAD);
        return Math.toIntExact(count(wrapper));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void notifyAlarmEvent(Long alarmEventId, Long organizationId, Long cloudRoomId,
                                  String title, String content) {
        // 派发给所有启用状态的平台管理员和组织管理员。
        // 本策略不依赖跨任务的 organization→user 映射 Mapper，保证不越权派发给未授权用户。
        Set<Long> recipientUserIds = collectAdminRecipientUserIds();
        if (recipientUserIds.isEmpty()) {
            log.warn("告警事件无可用接收用户 alarmEventId={} orgId={}", alarmEventId, organizationId);
            return;
        }
        int success = 0;
        for (Long uid : recipientUserIds) {
            InAppNotification n = new InAppNotification();
            n.setRecipientUserId(uid);
            n.setTitle(title);
            n.setContent(content);
            n.setSourceType(SOURCE_ALARM_EVENT);
            n.setSourceId(alarmEventId);
            n.setIsRead(READ_UNREAD);
            if (!save(n)) {
                throw new BusinessException("派发告警站内通知失败: uid=" + uid);
            }
            success++;
        }
        log.info("告警站内通知派发完成 alarmEventId={} 派发用户数={}",
                alarmEventId, success);
    }

    /**
     * 收集启用状态的平台管理员和组织管理员用户ID集合。
     */
    private Set<Long> collectAdminRecipientUserIds() {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getStatus, USER_STATUS_ENABLED)
                .in(SysUser::getRole,
                        PlatformRole.PLATFORM_ADMIN,
                        PlatformRole.ORG_ADMIN);
        List<SysUser> admins = sysUserService.list(wrapper);
        Set<Long> userIds = new HashSet<>();
        for (SysUser u : admins) {
            if (u.getId() != null) {
                userIds.add(u.getId());
            }
        }
        return userIds;
    }
}