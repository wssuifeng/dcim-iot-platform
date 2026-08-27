package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 设备点位绑定请求 DTO（ADMIN/OPERATOR 为设备绑定点位）
 */
@Schema(description = "设备点位绑定请求")
@Data
public class DevicePointBindingDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备ID不能为空")
    private Integer deviceId;

    @Schema(description = "点位定义ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "点位定义ID不能为空")
    private Long pointId;

    @Schema(description = "绑定状态：0-启用 1-停用", example = "0")
    private Integer status;
}