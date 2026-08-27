package com.example.platform.ingest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 网关设备清单上报请求（清单上报式接入）
 */
@Schema(description = "网关设备清单上报请求")
@Data
public class IngestInventoryDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "设备清单", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "设备清单不能为空")
    @Size(max = 500, message = "单次清单不能超过500台设备")
    @Valid
    private List<DeviceItem> devices;

    /**
     * 清单单台设备
     */
    @Schema(description = "清单单台设备")
    @Data
    public static class DeviceItem implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "设备编码", example = "TH-ROOM1-B", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "设备编码不能为空")
        @Size(max = 50)
        private String deviceCode;

        @Schema(description = "设备名称（网关侧命名，可空回退编码）", example = "机房A-温湿度-01")
        @Size(max = 50)
        private String deviceName;

        @Schema(description = "网关侧点位编码列表", example = "[\"temperature\",\"humidity\"]")
        @Size(max = 50)
        private List<String> pointCodes;
    }
}
