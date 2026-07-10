package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.entity.Alert;
import com.example.platform.mapper.AlertMapper;
import com.example.platform.service.DeviceService;
import com.example.platform.service.NotificationDedupService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlertService 数据范围单元测试")
class AlertScopedServiceTest {

    @Mock private AlertMapper alertMapper;
    @Mock private DeviceService deviceService;
    @Mock private NotificationDedupService notificationDedupService;

    @InjectMocks private AlertServiceImpl alertService;

    @BeforeEach
    void setUp() throws Exception {
        Field field = null;
        Class<?> type = AlertServiceImpl.class;
        while (type != null && field == null) {
            try { field = type.getDeclaredField("baseMapper"); }
            catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        assertThat(field).isNotNull();
        field.setAccessible(true);
        field.set(alertService, alertMapper);

        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(AlertMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, Alert.class);
    }

    @Test
    @DisplayName("空设备范围查询未处理告警返回空且 SQL 带空条件")
    void emptyScopeReturnsEmptyAlerts() {
        when(deviceService.authorizedDeviceIds(9L)).thenReturn(Set.of());
        when(alertMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThat(alertService.findUnhandledScoped(9L)).isEmpty();
        verify(alertMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("批量处理混入越权告警ID时整批拒绝")
    void batchRejectsMixedUnauthorizedAlertIds() {
        when(deviceService.authorizedDeviceIds(9L)).thenReturn(Set.of(1));
        Alert authorized = new Alert();
        authorized.setId(10);
        authorized.setDeviceId(1);
        authorized.setIsHandled(0);
        when(alertMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(authorized));

        assertThatThrownBy(() -> alertService.batchHandleScoped(List.of(10, 11), "operator", 9L))
                .isInstanceOf(DataScopeViolationException.class);
        verify(alertMapper, never()).update(any(), any());
    }
}
