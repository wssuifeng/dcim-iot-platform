package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.entity.DeviceData;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/*
 * 类功能:设备数据服务接口，定义设备数据的查询、统计、保存及告警触发等业务方法
 * */
public interface DeviceDataService extends IService<DeviceData> {

    /*
     * 方法功能:根据设备ID查询该设备的所有历史数据
     * 参数:deviceId-设备ID
     * 返回数据:List<DeviceData>-设备数据列表
     * */
    List<DeviceData> findByDeviceId(Integer deviceId);

    /*
     * 方法功能:查询指定设备最新的一条采集数据
     * 参数:deviceId-设备ID
     * 返回数据:DeviceData-最新设备数据，无数据时返回null
     * */
    DeviceData findLatestByDeviceId(Integer deviceId);

    /*
     * 方法功能:根据设备ID和时间范围查询采集数据
     * 参数:deviceId-设备ID, startTime-起始时间, endTime-结束时间
     * 返回数据:List<DeviceData>-符合条件的数据列表
     * */
    List<DeviceData> findByDeviceIdAndTimeRange(Integer deviceId,
                                                LocalDateTime startTime, LocalDateTime endTime);

    /*
     * 方法功能:获取指定设备在某个时间段内的数据统计信息
     * 参数:deviceId-设备ID, startTime-开始时间, endTime-结束时间
     * 返回数据:Map<String, Object>-统计结果，包含count/minVal/maxVal/avgVal
     * */
    Map<String, Object> getStatistics(Integer deviceId,
                                      LocalDateTime startTime, LocalDateTime endTime);

    /*
     * 方法功能:保存单条设备数据并触发告警规则评估
     * 参数:deviceData-待保存的设备数据对象
     * 返回数据:无
     * */
    void saveWithAlertCheck(DeviceData deviceData);

    /*
     * 方法功能:批量保存设备数据并逐条触发告警规则评估
     * 参数:dataList-待保存的设备数据列表
     * 返回数据:无
     * */
    void saveBatchWithAlertCheck(List<DeviceData> dataList);

    /*
     * 方法功能:根据设备ID分页查询设备数据
     * 参数:deviceId-设备ID, current-当前页码, size-每页大小
     * 返回数据:Page<DeviceData>-分页设备数据
     * */
    Page<DeviceData> pageByDeviceId(Integer deviceId, Integer current, Integer size);

    /*
     * 方法功能:按可选设备ID条件分页查询设备数据
     * 参数:deviceId-设备ID（可选）, current-当前页码, size-每页大小
     * 返回数据:Page<DeviceData>-分页设备数据
     * */
    Page<DeviceData> pageByCondition(Integer deviceId, Integer current, Integer size);

    /*
     * 方法功能:按时间粒度聚合统计设备数据（支持按小时/天/周聚合）
     * 参数:deviceId-设备ID, startTime-开始时间, endTime-结束时间, granularity-聚合粒度(hour/day/week)
     * 返回数据:List<Map<String, Object>>-聚合统计数据列表
     * */
    List<Map<String, Object>> aggregate(Integer deviceId, LocalDateTime startTime,
                                         LocalDateTime endTime, String granularity);
}
