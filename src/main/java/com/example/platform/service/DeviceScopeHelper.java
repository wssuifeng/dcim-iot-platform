package com.example.platform.service;

import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.Device;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/*
 * 类功能:设备数据范围校验工具，供点位/指标/绑定等模块共享调用
 *
 * 共享契约 03：Device.location 只能作为展示字段，不能作为数据范围依据；
 * 数据范围必须依赖 organizationId 和 cloudRoomId。
 *
 * 【判定规则】
 *   - PLATFORM_ADMIN：在 SecurityConfig 已阻断业务 GET；此处当 PLATFORM_ADMIN 出现在
 *     service 调用路径时（例如内部编排），默认放行单设备访问，不做穿透列表写入权限放宽
 *   - ORG_ADMIN：context.organizationIds 含 device.organizationId
 *   - OPERATOR/VIEWER：
 *     * scopeType=ROOMS：context.cloudRoomIds 含 device.cloudRoomId
 *     * scopeType=ORG_ALL：context.organizationIds 含 device.organizationId
 *     * 其余范围（含空授权）：拒绝
 * */
@Component
@RequiredArgsConstructor
public class DeviceScopeHelper {

    private final DeviceService deviceService;
    private final DataScopeService dataScopeService;

    /**
     * 方法功能:确保当前用户有权访问目标设备
     * 未授权抛出 DataScopeViolationException（由 GlobalExceptionHandler 统一处理）
     */
    public Device ensureDeviceAccess(Integer deviceId, Long currentUserId) {
        Device device = deviceService.getDeviceOrThrow(deviceId);
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);
        ensureDeviceScope(device, context);
        return device;
    }

    private void ensureDeviceScope(Device device, DataScopeContext context) {
        if (context.isPlatformAdmin()) {
            return;
        }
        Long orgId = device.getOrganizationId();
        Long roomId = device.getCloudRoomId();

        if ("ROOMS".equals(context.getScopeType())) {
            if (roomId != null && context.getCloudRoomIds().contains(roomId)) {
                return;
            }
            throw new DataScopeViolationException("Device", String.valueOf(device.getId()));
        }
        if (orgId != null && context.getOrganizationIds().contains(orgId)) {
            return;
        }
        throw new DataScopeViolationException("Device", String.valueOf(device.getId()));
    }
}
