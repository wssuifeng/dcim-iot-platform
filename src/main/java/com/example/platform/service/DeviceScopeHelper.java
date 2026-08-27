package com.example.platform.service;

import com.example.platform.entity.Device;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/*
 * 类功能:设备访问校验工具，供点位/指标/绑定等模块共享调用
 *
 * 单租户收束后不再有组织级数据范围，本工具退化为设备存在性检查；
 * 保留类名以减少调用方改动，访问控制由控制器 RBAC 角色保证。
 * */
@Component
@RequiredArgsConstructor
public class DeviceScopeHelper {

    private final DeviceService deviceService;

    /*
     * 方法功能:确保目标设备存在并返回实体
     * 不存在抛 DeviceNotFoundException（由 GlobalExceptionHandler 统一处理）
     * */
    public Device ensureDeviceAccess(Integer deviceId, Long currentUserId) {
        return deviceService.getDeviceOrThrow(deviceId);
    }
}
