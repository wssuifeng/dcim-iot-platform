package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.platform.common.group.CreateGroup;
import com.example.platform.common.group.UpdateGroup;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体类，映射 sys_user 表，存储系统用户认证信息及登录安全控制字段
 */
@Schema(description = "系统用户")
@Data
@TableName("sys_user")
public class SysUser implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    @NotNull(message = "用户ID不能为空", groups = UpdateGroup.class)
    private Long id;

    @Schema(description = "用户名（登录账号，4-20位字母数字下划线）", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "用户名不能为空", groups = CreateGroup.class)
    @Size(min = 4, max = 20, message = "用户名长度需在4-20之间", groups = CreateGroup.class)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅允许字母数字和下划线", groups = CreateGroup.class)
    private String username;

    @JsonIgnore
    @Schema(description = "密码（BCrypt加密，明文须含大小写字母和数字）", example = "Admin@123", requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.WRITE_ONLY)
    @NotBlank(message = "密码不能为空", groups = CreateGroup.class)
    @Size(min = 8, max = 32, message = "密码长度需在8-32之间", groups = CreateGroup.class)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,32}$",
            message = "密码必须包含大小写字母和数字", groups = CreateGroup.class)
    private String password;

    @Schema(description = "昵称（展示用）", example = "系统管理员")
    @Size(max = 50, message = "昵称最长50个字符", groups = {CreateGroup.class, UpdateGroup.class})
    private String nickname;

    @Schema(description = "角色标识：ADMIN-超管，OPERATOR-运维，VIEWER-普通", example = "ADMIN", allowableValues = {"ADMIN", "OPERATOR", "VIEWER"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "角色不能为空", groups = {CreateGroup.class, UpdateGroup.class})
    @Pattern(regexp = "^(ADMIN|OPERATOR|VIEWER)$", message = "角色仅允许ADMIN/OPERATOR/VIEWER",
            groups = {CreateGroup.class, UpdateGroup.class})
    private String role;

    @JsonIgnore
    @Schema(description = "状态：0-正常，1-停用", example = "0", allowableValues = {"0", "1"}, accessMode = Schema.AccessMode.READ_ONLY)
    @Min(value = 0, message = "状态值只能为0/1", groups = {CreateGroup.class, UpdateGroup.class})
    @Max(value = 1, message = "状态值只能为0/1", groups = {CreateGroup.class, UpdateGroup.class})
    private Integer status;

    @JsonIgnore
    @Schema(description = "连续登录失败次数（连续5次锁定账户30分钟）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField("login_fail_count")
    private Integer loginFailCount;

    @JsonIgnore
    @Schema(description = "账号锁定截止时间（锁定30分钟后自动解锁）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField("lock_time")
    private LocalDateTime lockTime;

    @Schema(description = "创建时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
}