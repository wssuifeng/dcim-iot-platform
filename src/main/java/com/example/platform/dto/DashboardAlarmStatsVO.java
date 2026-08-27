package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 告警看板聚合统计 VO
 */
@Schema(description = "告警看板聚合统计")
@Data
public class DashboardAlarmStatsVO {

    @Schema(description = "当前授权范围说明（用于前端标识聚合范围）", example = "PLATFORM: 全量")
    private String scopeDescription;

    @Schema(description = "按状态分布统计")
    private Map<String, Long> stateDistribution;

    @Schema(description = "按级别分布统计")
    private Map<String, Long> levelDistribution;

    @Schema(description = "近 7 日告警趋势（日期字符串 -> 计数）")
    private Map<String, Long> dailyTrend;

    @Schema(description = "告警数量排名的设备列表（取 Top N）")
    private List<Map<String, Object>> deviceRanking;
}