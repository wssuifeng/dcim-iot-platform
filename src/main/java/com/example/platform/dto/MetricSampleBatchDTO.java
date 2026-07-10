package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 指标采样批量写入请求 DTO
 */
@Schema(description = "指标采样批量写入请求")
@Data
public class MetricSampleBatchDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "采样数据列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "采样数据列表不能为空")
    @Valid
    private List<MetricSampleDTO> items;
}