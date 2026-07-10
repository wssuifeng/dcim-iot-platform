package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.InAppNotificationVO;
import com.example.platform.entity.InAppNotification;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.InAppNotificationMapper;
import com.example.platform.service.InAppNotificationService;
import com.example.platform.service.SysUserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:InAppNotificationServiceImpl 单元测试
 * 覆盖 BIZ-TRAE-02：通知已读状态独立于发送日志、接收用户本人校验、管理员派发、未读计数
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("InAppNotificationService 单元测试")
class InAppNotificationServiceImplTest {

    @Mock
    private InAppNotificationMapper inAppNotificationMapper;
    @Mock
    private SysUserService sysUserService;

    @InjectMocks
    private InAppNotificationServiceImpl inAppNotificationService;

    private static final Long CURRENT_USER_ID = 50L;
    private static final Long OTHER_USER_ID = 99L;

    @BeforeEach
    void setUp() throws Exception {
        initBaseMapper();
        initLambdaCache();
    }

    private void initBaseMapper() throws Exception {
        Class<?> clazz = InAppNotificationServiceImpl.class;
        Field field = null;
        while (clazz != null) {
            try { field = clazz.getDeclaredField("baseMapper"); break; }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        assertThat(field).as("应存在 baseMapper 字段").isNotNull();
        field.setAccessible(true);
        field.set(inAppNotificationService, inAppNotificationMapper);
    }

    private void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(InAppNotificationMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, InAppNotification.class);
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @Nested
    @DisplayName("createForUser - 创建通知")
    class CreateForUser {

        @Test
        @DisplayName("接收用户ID为空拒绝")
        void should_reject_when_recipientNull() {
            assertThatThrownBy(() -> inAppNotificationService.createForUser(
                    null, "标题", "内容", "ALARM_EVENT", 1L))
                    .isInstanceOf(BusinessException.class);
            verify(inAppNotificationMapper, times(0)).insert((InAppNotification) any());
        }

        @Test
        @DisplayName("成功创建通知")
        void should_create_notification() {
            when(inAppNotificationMapper.insert((InAppNotification) any())).thenReturn(1);
            InAppNotification result = inAppNotificationService.createForUser(
                    10L, "告警通知", "高温告警", "ALARM_EVENT", 100L);
            assertThat(result.getRecipientUserId()).isEqualTo(10L);
            assertThat(result.getIsRead()).isZero();
            verify(inAppNotificationMapper, times(1)).insert((InAppNotification) any());
        }
    }

    @Nested
    @DisplayName("markAsRead - 已读状态")
    class MarkAsRead {

        @Test
        @DisplayName("非接收用户标记已读拒绝")
        void should_reject_when_notRecipient() {
            InAppNotification n = new InAppNotification();
            n.setId(1L);
            n.setRecipientUserId(OTHER_USER_ID);
            n.setIsRead(0);
            when(inAppNotificationMapper.selectById(1L)).thenReturn(n);

            assertThatThrownBy(() -> inAppNotificationService.markAsRead(1L, CURRENT_USER_ID))
                    .isInstanceOf(DataScopeViolationException.class);
            verify(inAppNotificationMapper, times(0)).updateById((InAppNotification) any());
        }

        @Test
        @DisplayName("已读通知重复标记幂等返回")
        void should_idempotent_when_alreadyRead() {
            InAppNotification n = new InAppNotification();
            n.setId(1L);
            n.setRecipientUserId(CURRENT_USER_ID);
            n.setIsRead(1);
            when(inAppNotificationMapper.selectById(1L)).thenReturn(n);

            InAppNotification result = inAppNotificationService.markAsRead(1L, CURRENT_USER_ID);
            assertThat(result.getIsRead()).isEqualTo(1);
            verify(inAppNotificationMapper, times(0)).updateById((InAppNotification) any());
        }

        @Test
        @DisplayName("成功标记已读")
        void should_markAsRead() {
            InAppNotification n = new InAppNotification();
            n.setId(1L);
            n.setRecipientUserId(CURRENT_USER_ID);
            n.setIsRead(0);
            when(inAppNotificationMapper.selectById(1L)).thenReturn(n);
            when(inAppNotificationMapper.updateById((InAppNotification) any())).thenReturn(1);

            InAppNotification result = inAppNotificationService.markAsRead(1L, CURRENT_USER_ID);
            assertThat(result.getIsRead()).isEqualTo(1);
            assertThat(result.getReadTime()).isNotNull();
            verify(inAppNotificationMapper, times(1)).updateById((InAppNotification) any());
        }

        @Test
        @DisplayName("通知不存在抛异常")
        void should_throw_when_notFound() {
            when(inAppNotificationMapper.selectById(99L)).thenReturn(null);
            assertThatThrownBy(() -> inAppNotificationService.markAsRead(99L, CURRENT_USER_ID))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("notifyAlarmEvent - 管理员派发")
    class NotifyAlarmEvent {

        @Test
        @DisplayName("无可用管理员跳过派发")
        void should_skip_when_noAdmins() {
            when(sysUserService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            inAppNotificationService.notifyAlarmEvent(1L, ORG_ID, null, "告警", "内容");
            verify(inAppNotificationMapper, times(0)).insert((InAppNotification) any());
        }

        @Test
        @DisplayName("有管理员时派发通知")
        void should_dispatch_toAdmins() {
            SysUser admin = new SysUser();
            admin.setId(20L);
            admin.setUsername("org_admin");
            admin.setRole("ORG_ADMIN");
            admin.setStatus(0);
            when(sysUserService.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(admin));
            // saveBatch 内部调用 insert，每个实体一次
            when(inAppNotificationMapper.insert((InAppNotification) any())).thenReturn(1);

            inAppNotificationService.notifyAlarmEvent(1L, ORG_ID, null, "告警", "内容");
            verify(inAppNotificationMapper, times(1)).insert((InAppNotification) any());
        }
    }

    @Nested
    @DisplayName("countUnread - 未读计数")
    class CountUnread {

        @Test
        @DisplayName("返回当前用户未读数")
        void should_returnUnreadCount() {
            when(inAppNotificationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
            int count = inAppNotificationService.countUnread(CURRENT_USER_ID);
            assertThat(count).isEqualTo(3);
        }
    }

    private static final Long ORG_ID = 1L;
}