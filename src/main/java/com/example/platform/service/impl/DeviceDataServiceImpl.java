package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.entity.DeviceData;
import com.example.platform.mapper.DeviceDataMapper;
import com.example.platform.service.DeviceDataService;
import com.example.platform.service.DeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * 类功能:设备数据服务实现类，负责设备数据的持久化、查询、统计以及结合告警引擎的保存与告警触发
 * */
@Service
@Slf4j
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
        validateTimeRange(startTime, endTime);
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
        validateTimeRange(startTime, endTime);
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

    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_TIME_RANGE_DAYS = 30;

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("起始时间和结束时间不能为空");
        }
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("起始时间不能晚于结束时间");
        }
        if (java.time.Duration.between(startTime, endTime).toDays() > MAX_TIME_RANGE_DAYS) {
            throw new IllegalArgumentException("查询时间范围不能超过" + MAX_TIME_RANGE_DAYS + "天");
        }
    }

    /*
     * 方法功能:保存单条设备数据，校验设备存在性后保存数据、更新在线时间并触发告警评估
     * 参数:deviceData-待保存的设备数据对象
     * 返回数据:无
     *
     * 【死锁兜底】@Retryable 对整个方法重试（maxAttempts=3，指数退避：20ms→40ms→80ms），
     *           每次重试都开启全新事务，避免旧事务同步失效（Transaction synchronization is not active）。
     *
     * 【死锁根治】
     *   a) 隔离级别降级 → READ_COMMITTED：消除 InnoDB gap/next-key 锁
     *   b) 锁顺序统一  → UPDATE devices 在 INSERT device_data 之前
     *   c) 告警评估后置 → 注册到事务提交后执行：避免 alerts 表操作参与事务内锁竞争
     * */
    @Retryable(
            retryFor = { org.springframework.dao.DeadlockLoserDataAccessException.class,
                         org.springframework.dao.CannotAcquireLockException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 20, multiplier = 2.0, random = true)
    )
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @Override
    public void saveWithAlertCheck(DeviceData deviceData) {
        deviceService.getDeviceOrThrow(deviceData.getDeviceId());

        // 先获取 devices 行锁，统一加锁顺序
        deviceService.updateLastOnlineTime(deviceData.getDeviceId());

        // 再插入数据，此时已持有 devices 行锁，不会与其他事务交叉等待
        save(deviceData);

        Integer deviceId = deviceData.getDeviceId();
        DeviceData saved = deviceData;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    alertRuleEngine.evaluate(deviceId, saved);
                } catch (Exception e) {
                    log.error("告警评估失败，设备ID={}，数据ID={}：{}", deviceId, saved.getId(), e.getMessage(), e);
                }
            }
        });
    }

    /*
     * @Recover: 当 @Retryable 耗尽全部重试次数后，记录最终失败日志并传播异常，
     *          由 GlobalExceptionHandler 统一兜底为 5000 响应。
     * */
    @Recover
    public void recoverSaveWithAlertCheck(
            org.springframework.dao.DataAccessException e,
            DeviceData deviceData) {
        log.error("saveWithAlertCheck 重试耗尽, deviceId={}, 最终异常",
                deviceData != null ? deviceData.getDeviceId() : null, e);
        throw e;
    }

    /*
     * 方法功能:批量保存设备数据，逐条校验设备存在性、保存并触发告警评估
     * 参数:dataList-待保存的设备数据列表
     * 返回数据:无
     * */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @Override
    public void saveBatchWithAlertCheck(List<DeviceData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }
        if (dataList.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("批量上报单次上限" + MAX_BATCH_SIZE + "条，当前" + dataList.size() + "条");
        }
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

    /*
     * 方法功能:按时间粒度聚合统计设备数据（支持按小时/天/周聚合）
     * 参数:deviceId-设备ID, startTime-开始时间, endTime-结束时间, granularity-聚合粒度(hour/day/week)
     * 返回数据:List<Map<String, Object>>-聚合统计数据列表
     * */
    @Override
    public List<Map<String, Object>> aggregate(Integer deviceId, LocalDateTime startTime,
                                                LocalDateTime endTime, String granularity) {
        validateTimeRange(startTime, endTime);
        deviceService.getDeviceOrThrow(deviceId);
        return switch (granularity.toLowerCase()) {
            case "hour" -> baseMapper.aggregateByHour(deviceId, startTime, endTime);
            case "day" -> baseMapper.aggregateByDay(deviceId, startTime, endTime);
            case "week" -> baseMapper.aggregateByWeek(deviceId, startTime, endTime);
            default -> throw new IllegalArgumentException("不支持的聚合粒度: " + granularity + "，可选值: hour/day/week");
        };
    }

    /*
     * 方法功能:清理指定时间之前的过期设备数据（TTL 数据保留策略）
     * 参数:beforeTime-保留截止时间
     * 返回数据:int-删除的记录条数
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int cleanDataBefore(LocalDateTime beforeTime) {
        LambdaQueryWrapper<DeviceData> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(DeviceData::getCollectTime, beforeTime);
        return baseMapper.delete(wrapper);
    }
}
