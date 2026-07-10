package com.example.platform.service;

import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.Device;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceScopeHelper 单元测试")
class DeviceScopeHelperTest {

    @Mock
    private DeviceService deviceService;
    @Mock
    private DataScopeService dataScopeService;

    @InjectMocks
    private DeviceScopeHelper deviceScopeHelper;

    @Test
    @DisplayName("ROOMS 范围不能因同组织而访问未授权机房设备")
    void roomsScopeMustRejectUnassignedRoomEvenInSameOrganization() {
        Device device = device(1, 100L, 200L);
        when(deviceService.getDeviceOrThrow(1)).thenReturn(device);
        when(dataScopeService.resolveDataScope(9L)).thenReturn(DataScopeContext.builder()
                .userId(9L)
                .role("OPERATOR")
                .scopeType("ROOMS")
                .organizationIds(Set.of(100L))
                .cloudRoomIds(Set.of(201L))
                .build());

        assertThatThrownBy(() -> deviceScopeHelper.ensureDeviceAccess(1, 9L))
                .isInstanceOf(DataScopeViolationException.class);
    }

    @Test
    @DisplayName("ROOMS 范围允许访问已授权机房设备")
    void roomsScopeAllowsAssignedRoom() {
        Device device = device(1, 100L, 200L);
        when(deviceService.getDeviceOrThrow(1)).thenReturn(device);
        when(dataScopeService.resolveDataScope(9L)).thenReturn(DataScopeContext.builder()
                .userId(9L)
                .role("OPERATOR")
                .scopeType("ROOMS")
                .organizationIds(Set.of(100L))
                .cloudRoomIds(Set.of(200L))
                .build());

        assertThat(deviceScopeHelper.ensureDeviceAccess(1, 9L)).isSameAs(device);
    }

    @Test
    @DisplayName("ORG_ALL 范围按组织访问设备")
    void orgAllScopeAllowsOrganizationDevice() {
        Device device = device(1, 100L, 200L);
        when(deviceService.getDeviceOrThrow(1)).thenReturn(device);
        when(dataScopeService.resolveDataScope(8L)).thenReturn(DataScopeContext.builder()
                .userId(8L)
                .role("ORG_ADMIN")
                .scopeType("ORG_ALL")
                .organizationIds(Set.of(100L))
                .cloudRoomIds(Set.of())
                .build());

        assertThat(deviceScopeHelper.ensureDeviceAccess(1, 8L)).isSameAs(device);
    }

    private Device device(Integer id, Long organizationId, Long cloudRoomId) {
        Device device = new Device();
        device.setId(id);
        device.setOrganizationId(organizationId);
        device.setCloudRoomId(cloudRoomId);
        return device;
    }
}
