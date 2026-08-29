package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 设备类型指标模板项（添加/更新模板时使用）
 */
@Schema(description = "设备类型指标模板项")
@Data
public class DeviceTypePointTemplateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "指标定义ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "指标定义ID不能为空")
    private Long pointId;

    @Schema(description = "是否必选指标：0-可选 1-必选", example = "1")
    private Integer isRequired;

    @Schema(description = "展示排序", example = "0")
    private Integer sortOrder;
}