package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.entity.Alert;
import com.example.platform.entity.Device;
import com.example.platform.mapper.AlertMapper;
import com.example.platform.service.AlertService;
import com.example.platform.service.DeviceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * 类功能:告警服务实现类，负责告警的查询、批量处理、统计及离线告警生成
 * */
@Service
public class AlertServiceImpl extends ServiceImpl<AlertMapper, Alert> implements AlertService {

    private static final int MAX_BATCH_HANDLE_SIZE = 50;

    private final DeviceService deviceService;

    public AlertServiceImpl(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /*
     * 方法功能:查询所有未处理的告警记录
     * 参数:无
     * 返回数据:List<Alert>-未处理告警集合
     * */
    @Override
    public List<Alert> findUnhandled() {
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Alert::getIsHandled, 0);
        return list(wrapper);
    }

    /*
     * 方法功能:根据设备ID查询该设备的所有告警记录
     * 参数:deviceId-设备ID
     * 返回数据:List<Alert>-告警集合，deviceId为null时返回空列表
     * */
    @Override
    public List<Alert> findByDeviceId(Integer deviceId) {
        if (deviceId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Alert::getDeviceId, deviceId);
        return list(wrapper);
    }

    /*
     * 方法功能:批量处理指定ID的告警，标记为已处理并记录处理人和处理时间
     * 参数:ids-待处理的告警ID列表, handleUser-处理人姓名
     * 返回数据:int-成功处理的告警条数
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int batchHandle(List<Integer> ids, String handleUser) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        if (ids.size() > MAX_BATCH_HANDLE_SIZE) {
            throw new IllegalArgumentException("批量处理单次上限" + MAX_BATCH_HANDLE_SIZE + "条，当前" + ids.size() + "条");
        }
        LambdaUpdateWrapper<Alert> wrapper = new LambdaUpdateWrapper<Alert>()
                .in(Alert::getId, ids)
                .eq(Alert::getIsHandled, 0)
                .set(Alert::getIsHandled, 1)
                .set(Alert::getHandleTime, LocalDateTime.now())
                .set(Alert::getHandleUser, handleUser);
        return baseMapper.update(null, wrapper);
    }

    /*
     * 方法功能:获取指定设备的告警统计信息
     * 参数:deviceId-设备ID
     * 返回数据:Map<String, Object>-统计结果
     * */
    @Override
    public Map<String, Object> getStatsByDeviceId(Integer deviceId) {
        deviceService.getDeviceOrThrow(deviceId);
        return baseMapper.getStatsByDeviceId(deviceId);
    }

    /*
     * 方法功能:获取指定时间段内的全局告警统计信息
     * 参数:startTime-开始时间, endTime-结束时间
     * 返回数据:Map<String, Object>-统计结果
     * */
    @Override
    public Map<String, Object> getStatsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return baseMapper.getStatsByTimeRange(startTime, endTime);
    }

    /*
     * 方法功能:按时间段统计各设备的告警数量
     * 参数:startTime-开始时间, endTime-结束时间
     * 返回数据:List<Map<String, Object>>-每个Map包含设备ID和告警数量
     * */
    @Override
    public List<Map<String, Object>> listDeviceAlertCountByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return baseMapper.listDeviceAlertCountByTimeRange(startTime, endTime);
    }

    /*
     * 方法功能:为离线设备批量创建离线告警，跳过已有未处理离线告警的设备
     * 参数:offlineDevices-离线设备列表, offlineThresholdMinutes-离线阈值（分钟）
     * 返回数据:int-新生成的告警条数
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int createOfflineAlertsForDevices(List<Device> offlineDevices, int offlineThresholdMinutes) {
        if (offlineDevices == null || offlineDevices.isEmpty()) {
            return 0;
        }

        // 提取离线设备ID列表
        List<Integer> deviceIds = offlineDevices.stream()
                .map(Device::getId)
                .toList();

        // 查询这些设备中已有未处理离线告警的设备ID，避免重复生成
        Set<Integer> alreadyAlertedDeviceIds = list(new LambdaQueryWrapper<Alert>()
                .in(Alert::getDeviceId, deviceIds)
                .eq(Alert::getAlertType, "离线告警")
                .eq(Alert::getIsHandled, 0))
                .stream()
                .map(Alert::getDeviceId)
                .collect(Collectors.toSet());

        // 为未报过的离线设备生成告警实体
        List<Alert> newAlerts = offlineDevices.stream()
                .filter(d -> !alreadyAlertedDeviceIds.contains(d.getId()))
                .map(d -> {
                    Alert alert = new Alert();
                    alert.setDeviceId(d.getId());
                    alert.setAlertType("离线告警");
                    alert.setAlertLevel(1);
                    alert.setAlertMessage("设备已离线超过" + offlineThresholdMinutes + "分钟");
                    alert.setIsHandled(0);
                    return alert;
                })
                .collect(Collectors.toList());

        if (!newAlerts.isEmpty()) {
            saveBatch(newAlerts);
        }
        return newAlerts.size();
    }

    @Override
    public Page<Alert> pageByCondition(Integer current, Integer size, Integer deviceId,
                                        Integer isHandled, Integer alertLevel,
                                        LocalDateTime startTime, LocalDateTime endTime) {
        Page<Alert> page = new Page<>(current, size);
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        if (deviceId != null) {
            wrapper.eq(Alert::getDeviceId, deviceId);
        }
        if (isHandled != null) {
            wrapper.eq(Alert::getIsHandled, isHandled);
        }
        if (alertLevel != null) {
            wrapper.eq(Alert::getAlertLevel, alertLevel);
        }
        if (startTime != null && endTime != null) {
            wrapper.between(Alert::getCreateTime, startTime, endTime);
        }
        wrapper.orderByDesc(Alert::getCreateTime);
        return page(page, wrapper);
    }
}
