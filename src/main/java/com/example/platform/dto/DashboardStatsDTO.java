package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 仪表盘统计数据DTO，封装设备统计和告警统计信息，供前端仪表盘页面展示
 *
 * <p>数据来源映射：
 * <ul>
 *   <li>DeviceStats.total ← deviceMapper.count()
 *   <li>DeviceStats.online ← deviceMapper.countByStatus() status=1
 *   <li>DeviceStats.offline ← deviceMapper.countByStatus() status=0
 *   <li>DeviceStats.fault ← deviceMapper.countByStatus() status=2
 *   <li>AlertStats.total ← alertService.count()
 *   <li>AlertStats.unhandled ← alertService.count(isHandled=0)
 *   <li>AlertStats.last24h ← alertService.getStatsByTimeRange(24h前, 现在)
 * </ul>
 */
@Schema(description = "仪表盘统计数据")
@Data
public class DashboardStatsDTO {

    @Schema(description = "设备统计信息")
    private DeviceStats deviceStats;

    @Schema(description = "告警统计信息")
    private AlertStats alertStats;

    @Schema(description = "设备统计")
    @Data
    public static class DeviceStats {
        @Schema(description = "设备总数", example = "42")
        private int total;
        @Schema(description = "在线设备数（status=1）", example = "35")
        private int online;
        @Schema(description = "离线设备数（status=0）", example = "7")
        private int offline;
        @Schema(description = "故障设备数（status=2）", example = "5")
        private int fault;
    }

    @Schema(description = "告警统计")
    @Data
    public static class AlertStats {
        @Schema(description = "告警总数", example = "128")
        private long total;
        @Schema(description = "未处理告警数", example = "5")
        private long unhandled;
        @Schema(description = "最近24小时告警数", example = "12")
        private long last24h;
    }
}
