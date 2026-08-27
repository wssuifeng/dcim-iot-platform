package com.example.platform.ingest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 网关凭证更新请求 DTO（不允许修改绑定机房：换机房等于换接入主体，应重发凭证）
 */
@Schema(description = "网关凭证更新请求")
@Data
public class IngestCredentialUpdateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "网关显示名", example = "机房A-动环主机01", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "网关名称不能为空")
    @Size(max = 100, message = "网关名称最长100个字符")
    private String gatewayName;

    @Schema(description = "凭证过期时间（可空=永久）", example = "2027-01-01T00:00:00")
    private LocalDateTime expireAt;
}
