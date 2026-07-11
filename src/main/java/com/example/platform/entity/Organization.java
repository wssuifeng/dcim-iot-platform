package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 组织实体类，映射 sys_organization 表，产品化数据范围模型的顶层归属单元
 */
@Schema(description = "组织")
@Data
@TableName("sys_organization")
public class Organization implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "组织ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "组织名称", example = "示例数据中心运营公司", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "组织名称不能为空")
    @Size(max = 100, message = "组织名称最长100个字符")
    @TableField("org_name")
    private String orgName;

    @Schema(description = "组织编码（全局唯一）", example = "ORG-001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "组织编码不能为空")
    @Size(max = 50, message = "组织编码最长50个字符")
    @TableField("org_code")
    private String orgCode;

    @Schema(description = "组织描述", example = "华东区域数据中心运营主体")
    @Size(max = 200, message = "描述最长200个字符")
    private String description;

    @Schema(description = "状态：0-正常，1-停用", example = "0")
    private Integer status;

    @Schema(description = "是否允许提交加入申请", example = "true")
    @TableField("join_application_enabled")
    private Boolean joinApplicationEnabled;

    @Schema(description = "创建时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Schema(hidden = true)
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
