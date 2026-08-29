package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 指标定义新增请求 DTO（ADMIN 维护全局指标字典）
 */
@Schema(description = "指标定义新增请求")
@Data
public class PointDefinitionCreateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "指标编码（全局唯一）", example = "voltage_a", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "指标编码不能为空")
    @Size(max = 50, message = "指标编码最长50个字符")
    private String pointCode;

    @Schema(description = "指标名称（展示用）", example = "A相电压", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "指标名称不能为空")
    @Size(max = 100, message = "指标名称最长100个字符")
    private String pointName;

    @Schema(description = "单位", example = "V")
    @Size(max = 20, message = "单位最长20个字符")
    private String unit;

    @Schema(description = "数据类型：NUMBER/BOOLEAN/STRING", example = "NUMBER",
            allowableValues = {"NUMBER", "BOOLEAN", "STRING"})
    @Pattern(regexp = "^(NUMBER|BOOLEAN|STRING)$", message = "数据类型仅允许 NUMBER/BOOLEAN/STRING")
    private String dataType;

    @Schema(description = "指标描述", example = "UPS输出A相电压")
    @Size(max = 200, message = "描述最长200个字符")
    private String description;
}