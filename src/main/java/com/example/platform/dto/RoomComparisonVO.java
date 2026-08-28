package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 机房对比条目 VO（跨机房检索对比页数据源，每机房一条）
 */
@Schema(description = "机房对比条目")
@Data
public class RoomComparisonVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "机房ID", example = "4")
    private Long roomId;

    @Schema(description = "机房名称", example = "南昌红谷滩-铭万")
    private String roomName;

    @Schema(description = "命中设备总数（按过滤口径）")
    private Long deviceTotal;

    @Schema(description = "命中在线设备数")
    private Long deviceOnline;

    @Schema(description = "在线率（0-100 整数）")
    private Integer onlineRate;

    @Schema(description = "活动告警数（TRIGGERED/ACKED/HANDLING，按过滤口径）")
    private Long activeAlarms;

    @Schema(description = "时间范围内告警总数")
    private Long alarmCount;

    @Schema(description = "级别分布（L1-L5 → 数量）")
    private Map<String, Long> levelDistribution;
}
