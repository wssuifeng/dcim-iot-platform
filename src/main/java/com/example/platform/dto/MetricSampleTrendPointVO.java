package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指标趋势分桶聚合点（趋势图专用）
 *
 * <p>按固定桶宽聚合采样值：均值画趋势线，最小/最大构成波动带，
 * 与逐条分页查询解耦——无论原始采样密度如何，返回点数恒定可控。</p>
 */
@Schema(description = "指标趋势分桶聚合点")
@Data
public class MetricSampleTrendPointVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "桶起始时间", example = "2026-08-29T10:00:00")
    private LocalDateTime time;

    @Schema(description = "桶内采样均值", example = "24.35")
    private BigDecimal avgValue;

    @Schema(description = "桶内最小值", example = "23.80")
    private BigDecimal minValue;

    @Schema(description = "桶内最大值", example = "24.90")
    private BigDecimal maxValue;
}
