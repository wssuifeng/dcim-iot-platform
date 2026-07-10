package com.example.platform.service.impl;

import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.entity.DeviceData;
import com.example.platform.mapper.DeviceDataMapper;
import com.example.platform.service.DeviceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceDataService 数据范围单元测试")
class DeviceDataScopedServiceTest {

    @Mock private AlertRuleEngine alertRuleEngine;
    @Mock private DeviceService deviceService;
    @Mock private DeviceDataMapper deviceDataMapper;

    @InjectMocks private DeviceDataServiceImpl deviceDataService;

    @Test
    @DisplayName("越权设备不能上报数据")
    void reportRejectsUnauthorizedDevice() {
        DeviceData data = new DeviceData();
        data.setDeviceId(1);
        when(deviceService.getScopedForWrite(1, 9L))
                .thenThrow(new DataScopeViolationException("Device", "1"));

        assertThatThrownBy(() -> deviceDataService.saveWithAlertCheckScoped(data, 9L))
                .isInstanceOf(DataScopeViolationException.class);
        verify(deviceService, never()).updateLastOnlineTime(1);
    }

    @Test
    @DisplayName("全局分页未指定设备应拒绝")
    void pageRequiresDeviceId() {
        assertThatThrownBy(() -> deviceDataService.pageByConditionScoped(null, 1, 10, 9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须指定 deviceId");
    }
}
