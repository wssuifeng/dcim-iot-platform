package com.example.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.entity.Alert;
import com.example.platform.entity.Device;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/*
 * 类功能:告警服务接口，定义告警的查询、批量处理、统计及离线告警生成等业务方法
 * */
public interface AlertService extends IService<Alert> {

    /*
     * 方法功能:查询所有未处理的告警记录
     * 参数:无
     * 返回数据:List<Alert>-未处理告警集合
     * */
    List<Alert> findUnhandled();

    /*
     * 方法功能:根据设备ID查询该设备的所有告警记录
     * 参数:deviceId-设备ID
     * 返回数据:List<Alert>-告警集合
     * */
    List<Alert> findByDeviceId(Integer deviceId);

    /*
     * 方法功能:批量处理指定ID的告警，标记为已处理并记录处理人
     * 参数:ids-待处理的告警ID列表, handleUser-处理人姓名
     * 返回数据:int-成功处理的告警条数
     * */
    int batchHandle(List<Integer> ids, String handleUser);

    /*
     * 方法功能:获取指定设备的告警统计信息
     * 参数:deviceId-设备ID
     * 返回数据:Map<String, Object>-统计结果
     * */
    Map<String, Object> getStatsByDeviceId(Integer deviceId);

    /*
     * 方法功能:获取指定时间段内的全局告警统计信息
     * 参数:startTime-开始时间, endTime-结束时间
     * 返回数据:Map<String, Object>-统计结果
     * */
    Map<String, Object> getStatsByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    /*
     * 方法功能:按时间段统计各设备的告警数量
     * 参数:startTime-开始时间, endTime-结束时间
     * 返回数据:List<Map<String, Object>>-每个Map包含设备ID和告警数量
     * */
    List<Map<String, Object>> listDeviceAlertCountByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    /*
     * 方法功能:为离线设备批量创建离线告警，跳过已有未处理离线告警的设备
     * 参数:offlineDevices-离线设备列表, offlineThresholdMinutes-离线阈值（分钟）
     * 返回数据:int-新生成的告警条数
     * */
    int createOfflineAlertsForDevices(List<Device> offlineDevices, int offlineThresholdMinutes);
}
