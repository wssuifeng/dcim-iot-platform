package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警模版实体类，映射 alarm_template 表
 * 命名的规则融合模版：一套安装场景的告警规则聚合，设备类型绑定基础版，具体设备可改绑自定义模版
 */
@Schema(description = "告警模版")
@Data
@TableName("alarm_template")
public class AlarmTemplate implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "模版ID（主键，自增）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "模版编码（全局唯一，仅允许字母数字下划线短横线）", example = "TEMP_HUM_STD",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模版编码不能为空")
    @Size(max = 50, message = "模版编码最长50个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "模版编码只能包含字母、数字、下划线和短横线")
    @TableField("template_code")
    private String templateCode;

    @Schema(description = "模版显示名", example = "温湿度标准监控", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模版名称不能为空")
    @Size(max = 100, message = "模版名称最长100个字符")
    @TableField("template_name")
    private String templateName;

    @Schema(description = "用途说明", example = "机房通用温湿度阈值")
    @Size(max = 300, message = "用途说明最长300个字符")
    @TableField("description")
    private String description;

    @Schema(description = "状态：0-启用 1-停用", example = "0", allowableValues = {"0", "1"})
    @TableField("status")
    private Integer status;

    @Schema(description = "创建时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
