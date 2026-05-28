package com.example.platform.dto;

import lombok.Data;

/*
 * 类功能:仪表盘统计数据DTO，封装设备统计和告警统计信息，供前端仪表盘页面展示
 *
 * 【数据来源映射】
 * - DeviceStats.total      ← deviceMapper.count()（IService通用方法）
 * - DeviceStats.online     ← deviceMapper.countByStatus() 中 status=1 的 count
 * - DeviceStats.offline    ← deviceMapper.countByStatus() 中 status=0 的 count
 * - DeviceStats.fault      ← deviceMapper.countByStatus() 中 status=2 的 count
 * - AlertStats.total       ← alertService.count()（IService通用方法）
 * - AlertStats.unhandled   ← alertService.count(isHandled=0)
 * - AlertStats.last24h     ← alertService.getStatsByTimeRange(24h前, 现在) 的 alertCount
 * */
@Data
public class DashboardStatsDTO {

    /** 设备统计信息 */
    private DeviceStats deviceStats;

    /** 告警统计信息 */
    private AlertStats alertStats;

    @Data
    public static class DeviceStats {
        /** 设备总数 */
        private int total;
        /** 在线设备数（status=1） */
        private int online;
        /** 离线设备数（status=0） */
        private int offline;
        /** 故障设备数（status=2） */
        private int fault;
    }

    @Data
    public static class AlertStats {
        /** 告警总数 */
        private long total;
        /** 未处理告警数 */
        private long unhandled;
        /** 最近24小时告警数 */
        private long last24h;
    }
}
