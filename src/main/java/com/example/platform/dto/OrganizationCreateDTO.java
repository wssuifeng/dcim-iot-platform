package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 组织新增请求 DTO（PLATFORM_ADMIN 专用）
 */
@Schema(description = "组织新增请求")
@Data
public class OrganizationCreateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "组织名称", example = "示例数据中心运营公司", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "组织名称不能为空")
    @Size(max = 100, message = "组织名称最长100个字符")
    private String orgName;

    @Schema(description = "组织编码（全局唯一）", example = "ORG-001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "组织编码不能为空")
    @Size(max = 50, message = "组织编码最长50个字符")
    private String orgCode;

    @Schema(description = "组织描述", example = "华东区域数据中心运营主体")
    @Size(max = 200, message = "描述最长200个字符")
    private String description;
}