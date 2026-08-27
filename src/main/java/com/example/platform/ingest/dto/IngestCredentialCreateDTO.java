package com.example.platform.ingest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 网关凭证创建请求 DTO（secret 由服务端生成并在创建响应中一次性返回）
 */
@Schema(description = "网关凭证创建请求")
@Data
public class IngestCredentialCreateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "绑定机房ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "绑定机房不能为空")
    private Long cloudRoomId;

    @Schema(description = "首包自动注册：1=收到未知 deviceCode 自动建档", example = "0")
    private Integer autoRegister = 0;

    @Schema(description = "自动注册设备默认类型ID（auto_register=1 时必填）", example = "4")
    private Integer defaultTypeId;

    @Schema(description = "凭证过期时间（可空=永久）", example = "2027-01-01T00:00:00")
    private LocalDateTime expireAt;

    @Schema(description = "自定义接入标识（可空=服务端生成；仅字母数字短横线）", example = "gw-room-a-01")
    @Size(max = 64, message = "接入标识最长64个字符")
    @Pattern(regexp = "^$|^[A-Za-z0-9-]{4,64}$", message = "接入标识仅支持4-64位字母、数字和短横线")
    private String accessKey;
}
