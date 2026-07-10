package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 模拟场景更新 DTO（仅允许修改名称、描述、机房、设备类型、状态等可变字段；
 * organization_id、scene_code 不可变，避免破坏数据范围与追溯链）
 */
@Schema(description = "模拟场景更新请求")
@Data
public class SimulationSceneUpdateDTO {

    @Schema(description = "场景名称", example = "UPS 高负载模拟测试")
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
}