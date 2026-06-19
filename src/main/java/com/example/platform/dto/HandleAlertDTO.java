package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单条告警处理DTO，封装标记单条告警为已处理的请求参数
 */
@Schema(description = "单条告警处理请求")
@Data
public class HandleAlertDTO {

    @Schema(description = "处理人姓名", example = "张三", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "处理人不能为空")
    private String handleUser;

    @Schema(description = "告警ID（正整数）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "告警ID不能为空")
    @Min(value = 1, message = "告警ID必须为正整数")
    private Integer alertId;
}
