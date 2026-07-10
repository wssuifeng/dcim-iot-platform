package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 点位定义更新请求 DTO
 */
@Schema(description = "点位定义更新请求")
@Data
public class PointDefinitionUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "点位名称", example = "A相电压")
    @Size(max = 100, message = "点位名称最长100个字符")
    private String pointName;

    @Schema(description = "单位", example = "V")
    @Size(max = 20, message = "单位最长20个字符")
    private String unit;

    @Schema(description = "数据类型：NUMBER/BOOLEAN/STRING", example = "NUMBER")
    @Pattern(regexp = "^(NUMBER|BOOLEAN|STRING)$", message = "数据类型仅允许 NUMBER/BOOLEAN/STRING")
    private String dataType;

    @Schema(description = "点位描述", example = "UPS输出A相电压")
    @Size(max = 200, message = "描述最长200个字符")
    private String description;
}