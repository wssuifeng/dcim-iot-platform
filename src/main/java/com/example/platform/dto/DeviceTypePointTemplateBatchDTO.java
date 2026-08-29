package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 设备类型指标模板批量请求 DTO（一次性设置某类型的全部模板项，覆盖写）
 */
@Schema(description = "设备类型指标模板批量保存请求")
@Data
public class DeviceTypePointTemplateBatchDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "模板项列表（整批覆盖保存）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "模板项列表不能为空")
    @Valid
    private List<DeviceTypePointTemplateDTO> items;
}