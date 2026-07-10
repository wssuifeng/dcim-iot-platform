package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模拟场景步骤 DTO（创建/更新共用）
 */
@Schema(description = "模拟场景步骤")
@Data
public class SimulationSceneStepDTO {

    @Schema(description = "步骤顺序（从1开始递增）", example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "步骤顺序不能为空")
    @Min(value = 1, message = "步骤顺序必须大于 0")
    private Integer stepOrder;

    @Schema(description = "目标点位ID", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "点位ID不能为空")
    private Long pointId;

    @Schema(description = "目标设备ID（运行时由调用方指定的可填，可为空）", example = "5")
    private Integer targetDeviceId;

    @Schema(description = "步骤注入的采样值", example = "220.50")
    private BigDecimal sampleValue;

    @Schema(description = "步骤动作：WRITE_SAMPLE/SLEEP", example = "WRITE_SAMPLE")
    @Size(max = 20, message = "动作最长20个字符")
    private String action;

    @Schema(description = "执行前延迟毫秒", example = "0")
    @Min(value = 0, message = "延迟毫秒不能为负")
    private Integer delayMs;

    @Schema(description = "步骤描述")
    @Size(max = 200, message = "步骤描述最长200个字符")
    private String description;
}