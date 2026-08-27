package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.DevicePointBindingDTO;
import com.example.platform.entity.DevicePointBinding;

import java.util.List;

/*
 * 类功能:设备点位绑定服务接口
 *
 * 【角色与范围】ADMIN/OPERATOR 可管理绑定；所有已认证用户可查询，
 *   安全边界由控制器 RBAC 与 Service 层设备存在性校验保证，不依赖前端显隐
 * */
public interface DevicePointBindingService extends IService<DevicePointBinding> {

    DevicePointBinding bindPoint(DevicePointBindingDTO dto, Long currentUserId);

    DevicePointBinding updateStatus(Long bindingId, Integer status, Long currentUserId);

    void unbindPoint(Long bindingId, Long currentUserId);

    List<DevicePointBinding> listByDevice(Integer deviceId, Long currentUserId);

    /*
     * 方法功能:校验设备是否已启用指定点位绑定；采样写入前必须调用
     * */
    DevicePointBinding getActiveBindingOrThrow(Integer deviceId, Long pointId, Long currentUserId);

    Page<DevicePointBinding> pageByConditions(Long currentUserId, Integer current, Integer size,
                                              Integer deviceId, Long pointId, Integer status);

    /*
     * 方法功能:按设备类型模板初始化某设备的点位绑定（用于设备创建后批量绑定）
     * 复用已存在的绑定跳过，仅插入未绑定的模板点位
     * */
    List<DevicePointBinding> initBindingsFromTemplate(Integer deviceId, Integer typeId, Long currentUserId);
}
