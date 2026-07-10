package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 组织更新请求 DTO（PLATFORM_ADMIN 专用）
 * <p>不允许通过此接口修改 orgCode（组织编码是全局唯一业务标识，仅初始化时设定）。</p>
 */
@Schema(description = "组织更新请求")
@Data
public class OrganizationUpdateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "组织名称", example = "示例数据中心运营公司")
    @Size(max = 100, message = "组织名称最长100个字符")
    private String orgName;

    @Schema(description = "组织描述", example = "华东区域数据中心运营主体")
    @Size(max = 200, message = "描述最长200个字符")
    private String description;
}