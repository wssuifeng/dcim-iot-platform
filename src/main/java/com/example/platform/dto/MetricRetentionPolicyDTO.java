package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 指标保留策略请求 DTO
 * <p>retentionDays 上限 90，超出按契约拒绝（共享契约 4：不得默认启用无限保留）。</p>
 */
@Schema(description = "指标保留策略请求")
@Data
public class MetricRetentionPolicyDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "指标定义ID（为空表示全局默认策略）", example = "1")
    private Long pointId;

    @Schema(description = "保留天数（1~90）", example = "90", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "保留天数不能为空")
    @Min(value = 1, message = "保留天数至少1天")
    @Max(value = 90, message = "保留天数上限90天")
    private Integer retentionDays;
}