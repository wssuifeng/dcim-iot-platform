package com.example.platform.service;

import com.example.platform.dto.DashboardAlarmStatsVO;

/*
 * 类功能:告警看板统计服务接口
 *
 * 【契约】共享契约 §5：聚合数据不跨组织或机房泄露；
 *   所有聚合查询必须经过服务层范围校验，由 DataScopeService 解析用户范围。
 *
 * 【职责】提供按授权范围聚合的告警状态分布、级别分布、近期趋势和设备排名，
 *   不直接返回跨组织数据，也不替代前端的安全边界。
 * */
public interface DashboardAlarmStatsService {

    /*
     * 方法功能:获取当前用户授权范围内的告警看板聚合统计
     * 参数:currentUserId-当前用户ID
     * 返回数据:DashboardAlarmStatsVO-含状态分布、级别分布、近期趋势、设备排名
     * */
    DashboardAlarmStatsVO getAlarmDashboardStats(Long currentUserId);
}