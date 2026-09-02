package com.example.platform.ingest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 报文字段映射 DTO（ADR-009）：报文 JSON 字段路径 → 平台指标 pointCode
 */
@Schema(description = "报文字段映射")
@Data
public class PayloadFieldMapping implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "报文 JSON 内部路径（点路径+数组下标）", example = "data.temp")
    @NotBlank(message = "字段路径不能为空")
    @Size(max = 200, message = "字段路径最长200字符")
    private String path;

    @Schema(description = "平台指标编码（point_definition.point_code）", example = "temperature")
    @NotBlank(message = "指标编码不能为空")
    @Size(max = 64, message = "指标编码最长64字符")
    private String pointCode;

    @Schema(description = "缩放系数（raw × scale，可空=原值直取）", example = "0.1")
    private BigDecimal scale;
}
