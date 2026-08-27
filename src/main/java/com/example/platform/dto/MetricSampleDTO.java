package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 指标采样写入请求 DTO（ADMIN/OPERATOR 单条写入）
 */
@Schema(description = "指标采样写入请求")
@Data
public class MetricSampleDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备ID不能为空")
    private Integer deviceId;

    @Schema(description = "点位定义ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "点位定义ID不能为空")
    private Long pointId;

    @Schema(description = "采样值", example = "220.50", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "采样值不能为空")
    private BigDecimal sampleValue;

    @Schema(description = "采样时间（为空时使用服务端当前时间）", example = "2026-07-10T12:00:00")
    private LocalDateTime sampleTime;
}