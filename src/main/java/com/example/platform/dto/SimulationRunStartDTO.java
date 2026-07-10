package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 模拟运行启动 DTO
 *
 * <p>运行由后端调起：必须经 simulation.write-enabled 开关放行，并通过数据范围校验。</p>
 * <p>targetDeviceId 覆盖：当步骤中未指定 targetDeviceId 时，由运行启动方统一指定，
 *   用于将同一场景应用到不同设备。</p>
 */
@Schema(description = "模拟运行启动请求")
@Data
public class SimulationRunStartDTO {

    @Schema(description = "场景ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "场景ID不能为空")
    private Long sceneId;

    @Schema(description = "目标设备ID（覆盖步骤中为空的目标设备）", example = "5")
    private Integer targetDeviceId;

    @Schema(description = "目标机房ID（运行范围校验依据，可空）", example = "1")
    private Long cloudRoomId;
}