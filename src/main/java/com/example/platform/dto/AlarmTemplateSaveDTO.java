package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 告警模版创建/更新请求 DTO
 */
@Schema(description = "告警模版创建/更新请求")
@Data
public class AlarmTemplateSaveDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "模版编码（全局唯一，仅允许字母数字下划线短横线）", example = "TEMP_HUM_STD",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模版编码不能为空")
    @Size(max = 50, message = "模版编码最长50个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "模版编码只能包含字母、数字、下划线和短横线")
    private String templateCode;

    @Schema(description = "模版显示名", example = "温湿度标准监控", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模版名称不能为空")
    @Size(max = 100, message = "模版名称最长100个字符")
    private String templateName;

    @Schema(description = "用途说明", example = "机房通用温湿度阈值")
    @Size(max = 300, message = "用途说明最长300个字符")
    private String description;

    @Schema(description = "状态：true-启用 false-停用", example = "true")
    private Boolean status;
}
