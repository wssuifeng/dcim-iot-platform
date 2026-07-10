package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指标最新值响应 VO
 */
@Schema(description = "指标最新值响应")
@Data
public class MetricLatestVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键")
    private Long id;

    @Schema(description = "设备ID")
    private Integer deviceId;

    @Schema(description = "点位定义ID")
    private Long pointId;

    @Schema(description = "点位编码", example = "voltage_a")
    private String pointCode;

    @Schema(description = "点位名称", example = "A相电压")
    private String pointName;

    @Schema(description = "单位", example = "V")
    private String unit;

    @Schema(description = "最新值", example = "220.50")
    private BigDecimal latestValue;

    @Schema(description = "最新值采集时间")
    private LocalDateTime latestTime;
}