package com.example.platform.service;

import com.example.platform.dto.DashboardAlarmStatsVO;

/*
 * 类功能:告警看板统计服务接口
 *
 * 【职责】提供告警状态分布、级别分布、近期趋势和设备排名的聚合；
 *   支持全量口径与限定机房口径（机房工作台概览）。
 * */
public interface DashboardAlarmStatsService {

    /*
     * 方法功能:获取告警看板聚合统计（全量口径）
     * 参数:currentUserId-当前用户ID
     * 返回数据:DashboardAlarmStatsVO-含状态分布、级别分布、近期趋势、设备排名
     * */
    DashboardAlarmStatsVO getAlarmDashboardStats(Long currentUserId);

    /*
     * 方法功能:获取告警看板聚合统计（限定机房口径）
     * 参数:currentUserId-当前用户ID, cloudRoomId-机房ID（null=全量）, roomName-机房名（scope 展示用）
     * 返回数据:DashboardAlarmStatsVO-含状态分布、级别分布、近期趋势、设备排名
     * */
    DashboardAlarmStatsVO getAlarmDashboardStats(Long currentUserId, Long cloudRoomId, String roomName);
}
