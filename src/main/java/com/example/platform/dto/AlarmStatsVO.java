package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 告警统计聚合数据
 */
@Schema(description = "告警统计聚合数据")
@Data
public class AlarmStatsVO {

    @Schema(description = "按状态分布统计")
    private Map<String, Long> stateDistribution;

    @Schema(description = "按级别分布统计")
    private Map<String, Long> levelDistribution;

    @Schema(description = "告警数量排名的设备列表")
    private List<Map<String, Object>> deviceRanking;
}
