package com.example.platform.service;

import com.example.platform.dto.DashboardAlarmStatsVO;

/*
 * 类功能:告警看板统计服务接口
 *
 * 【职责】提供全量聚合的告警状态分布、级别分布、近期趋势和设备排名。
 * */
public interface DashboardAlarmStatsService {

    /*
     * 方法功能:获取告警看板聚合统计
     * 参数:currentUserId-当前用户ID
     * 返回数据:DashboardAlarmStatsVO-含状态分布、级别分布、近期趋势、设备排名
     * */
    DashboardAlarmStatsVO getAlarmDashboardStats(Long currentUserId);
}