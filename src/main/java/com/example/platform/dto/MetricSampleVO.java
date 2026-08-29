package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指标采样响应 VO（带指标名称/单位/编码，供前端直接展示）
 */
@Schema(description = "指标采样响应")
@Data
public class MetricSampleVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "采样ID")
    private Long id;

    @Schema(description = "设备ID")
    private Integer deviceId;

    @Schema(description = "指标定义ID")
    private Long pointId;

    @Schema(description = "指标编码", example = "voltage_a")
    private String pointCode;

    @Schema(description = "指标名称", example = "A相电压")
    private String pointName;

    @Schema(description = "单位", example = "V")
    private String unit;

    @Schema(description = "采样值", example = "220.50")
    private BigDecimal sampleValue;

    @Schema(description = "采样时间")
    private LocalDateTime sampleTime;
}