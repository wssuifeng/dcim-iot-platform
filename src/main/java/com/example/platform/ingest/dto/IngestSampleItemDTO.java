package com.example.platform.ingest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 网关上报单条采样项（传输无关契约，HTTP 与 MQTT 共用）
 */
@Schema(description = "网关上报单条采样项")
@Data
public class IngestSampleItemDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备编码（devices.device_code，全局唯一）", example = "UPS-ROOM1-A", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "设备编码不能为空")
    @Size(max = 50, message = "设备编码最长50个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "设备编码只能包含字母、数字、下划线和短横线")
    private String deviceCode;

    @Schema(description = "指标编码（point_definition.point_code）", example = "temperature", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "指标编码不能为空")
    @Size(max = 50, message = "指标编码最长50个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "指标编码只能包含字母、数字、下划线和短横线")
    private String pointCode;

    @Schema(description = "采样值", example = "22.5", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "采样值不能为空")
    private BigDecimal value;

    @Schema(description = "设备侧采样时间（必填；早于平台时间为补传合法，晚于+5分钟拒绝）", example = "2026-09-01T12:00:05", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "采样时间不能为空")
    private LocalDateTime sampleTime;

    @Schema(description = "网关侧单调递增序号（幂等键成分，QoS1 重发保持不变；HMAC 通道必填；"
            + "TB 兼容/MQTT 开放通道无 seq，幂等键降级为 deviceCode+pointCode+sampleTime，见 ADR-008 D4）", example = "10023")
    private Long seq;
}
