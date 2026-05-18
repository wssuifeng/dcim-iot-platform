package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.entity.DeviceData;
import com.example.platform.mapper.DeviceDataMapper;
import com.example.platform.service.DeviceDataService;
import com.example.platform.service.DeviceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * 类功能:设备数据服务实现类，负责设备数据的持久化、查询、统计以及结合告警引擎的保存与告警触发
 * */
@Service
public class DeviceDataServiceImpl extends ServiceImpl<DeviceDataMapper, DeviceData> implements DeviceDataService {

    private final AlertRuleEngine alertRuleEngine;
    private final DeviceService deviceService;

    public DeviceDataServiceImpl(AlertRuleEngine alertRuleEngine,
                                 DeviceService deviceService) {
        this.alertRuleEngine = alertRuleEngine;
        this.deviceService = deviceService;
    }

    /*
     * 方法功能:根据设备ID查询该设备的所有历史数据，按采集时间降序排列
     * 参数:deviceId-设备ID
     * 返回数据:List<DeviceData>-设备数据列表，无数据时为空列表
     * */
    public List<DeviceData> findByDeviceId(Integer deviceId) {
        LambdaQueryWrapper<DeviceData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceData::getDeviceId, deviceId)
                .orderByDesc(DeviceData::getCollectTime);
        return list(wrapper);
    }

    /*
     * 方法功能:查询指定设备最新的一条采集数据
     * 参数:deviceId-设备ID
     * 返回数据:DeviceData-最新的设备数据对象；如果该设备无任何数据则返回null
     * */
    public DeviceData findLatestByDeviceId(Integer deviceId) {
        LambdaQueryWrapper<DeviceData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceData::getDeviceId, deviceId)
                .orderByDesc(DeviceData::getCollectTime)
                .last("LIMIT 1");
        return getOne(wrapper);
    }

    /*
     * 方法功能:根据设备ID和时间范围查询采集数据，结果按采集时间升序排列
     * 参数:deviceId-设备ID, startTime-起始时间（包含）, endTime-结束时间（包含）
     * 返回数据:List<DeviceData>-符合条件的数据列表，无数据时为空列表
     * */
    @Override
    public List<DeviceData> findByDeviceIdAndTimeRange(Integer deviceId,
                                                       LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<DeviceData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceData::getDeviceId, deviceId)
                .between(DeviceData::getCollectTime, startTime, endTime)
                .orderByAsc(DeviceData::getCollectTime);
        return list(wrapper);
    }

    /*
     * 方法功能:获取指定设备在某个时间段内的数据统计信息
     * 参数:deviceId-设备ID, startTime-开始时间（包含）, endTime-结束时间（包含）
     * 返回数据:Map<String, Object>-统计结果，包含count/minVal/maxVal/avgVal
     * */
    @Override
    public Map<String, Object> getStatistics(Integer deviceId,
                                             LocalDateTime startTime, LocalDateTime endTime) {
        // 先校验设备存在性
        deviceService.getDeviceOrThrow(deviceId);

        Map<String, Object> stats = baseMapper.getStatisticsByDeviceIdAndTimeRange(
                deviceId, startTime, endTime);
        // 无数据时返回空统计结构，避免前端收到null
        if (stats == null) {
            stats = new HashMap<>();
            stats.put("count", 0);
            stats.put("minVal", null);
            stats.put("maxVal", null);
            stats.put("avgVal", null);
        }
        return stats;
    }

    /*
     * 方法功能:保存单条设备数据并触发告警规则评估
     * 参数:deviceData-待保存的设备数据对象
     * 返回数据:无
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveWithAlertCheck(DeviceData deviceData) {
        // 1. 校验设备存在（同时取出 typeId 供告警引擎使用）
        deviceService.getDeviceOrThrow(deviceData.getDeviceId());

        // 2. 保存数据（collectTime 由 MyBatis-Plus 自动填充，前端不传则自动填当前时间）
        save(deviceData);

        // 3. 触发告警规则评估
        alertRuleEngine.evaluate(deviceData.getDeviceId(), deviceData);
    }

    /*
     * 方法功能:批量保存设备数据并逐条触发告警规则评估，整体在同一事务内
     * 参数:dataList-待保存的设备数据列表
     * 返回数据:无
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveBatchWithAlertCheck(List<DeviceData> dataList) {
        for (DeviceData data : dataList) {
            saveWithAlertCheck(data);
        }
    }

    /*
     * 方法功能:根据设备ID分页查询设备数据，按采集时间倒序排列
     * 参数:deviceId-设备ID, current-当前页码, size-每页大小
     * 返回数据:Page<DeviceData>-分页设备数据
     * */
    @Override
    public Page<DeviceData> pageByDeviceId(Integer deviceId, Integer current, Integer size) {
        Page<DeviceData> page = new Page<>(current, size);
        LambdaQueryWrapper<DeviceData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceData::getDeviceId, deviceId)
                .orderByDesc(DeviceData::getCollectTime);
        return page(page, wrapper);
    }

    /*
     * 方法功能:按可选设备ID条件分页查询设备数据，按采集时间倒序排列
     * 参数:deviceId-设备ID（可选，为null时不筛选）, current-当前页码, size-每页大小
     * 返回数据:Page<DeviceData>-分页设备数据
     * */
    @Override
    public Page<DeviceData> pageByCondition(Integer deviceId, Integer current, Integer size) {
        Page<DeviceData> page = new Page<>(current, size);
        LambdaQueryWrapper<DeviceData> wrapper = new LambdaQueryWrapper<>();
        if (deviceId != null) {
            wrapper.eq(DeviceData::getDeviceId, deviceId);
        }
        wrapper.orderByDesc(DeviceData::getCollectTime);
        return page(page, wrapper);
    }
}
