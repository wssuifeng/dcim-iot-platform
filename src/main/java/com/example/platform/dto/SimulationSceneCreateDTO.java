package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 模拟场景创建 DTO
 */
@Schema(description = "模拟场景创建请求")
@Data
public class SimulationSceneCreateDTO {

    @Schema(description = "场景编码（全局唯一）", example = "UPS_LOAD_TEST",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "场景编码不能为空")
    @Size(max = 50, message = "场景编码最长50个字符")
    private String sceneCode;

    @Schema(description = "场景名称", example = "UPS 负载模拟测试",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "场景名称不能为空")
    @Size(max = 100, message = "场景名称最长100个字符")
    private String sceneName;

    @Schema(description = "场景描述")
    @Size(max = 500, message = "描述最长500个字符")
    private String description;

    @Schema(description = "所属机房ID（可空）", example = "1")
    private Long cloudRoomId;

    @Schema(description = "目标设备类型编码", example = "UPS")
    @Size(max = 50, message = "设备类型编码最长50个字符")
    private String deviceTypeCode;

    @Schema(description = "场景包含的步骤列表（可选，创建后可单独追加）")
    @Valid
    private List<SimulationSceneStepDTO> steps;
}