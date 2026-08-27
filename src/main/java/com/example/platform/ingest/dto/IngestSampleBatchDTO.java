package com.example.platform.ingest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 网关上报批量采样请求（传输无关契约，HTTP 与 MQTT 共用）
 */
@Schema(description = "网关上报批量采样请求（单批 ≤ 200 条）")
@Data
public class IngestSampleBatchDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "采样数据列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "采样数据列表不能为空")
    @Size(max = 200, message = "单批上报不能超过200条")
    @Valid
    private List<IngestSampleItemDTO> items;
}
