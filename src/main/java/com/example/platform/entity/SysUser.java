package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.platform.common.group.CreateGroup;
import com.example.platform.common.group.UpdateGroup;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/*
 * 类功能:用户实体类，映射sys_user表，存储系统用户认证信息及登录安全控制字段
 * */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    @NotNull(message = "用户ID不能为空", groups = UpdateGroup.class)
    private Long id;

    /** 用户名（登录账号，4-20位字母数字下划线） */
    @NotBlank(message = "用户名不能为空", groups = CreateGroup.class)
    @Size(min = 4, max = 20, message = "用户名长度需在4-20之间", groups = CreateGroup.class)
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅允许字母数字和下划线", groups = CreateGroup.class)
    private String username;

    /** 密码（BCrypt加密存储，原文明文8-32位，须包含大小写字母和数字） */
    @JsonIgnore
    @NotBlank(message = "密码不能为空", groups = CreateGroup.class)
    @Size(min = 8, max = 32, message = "密码长度需在8-32之间", groups = CreateGroup.class)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,32}$",
            message = "密码必须包含大小写字母和数字", groups = CreateGroup.class)
    private String password;

    /** 昵称（展示用） */
    @Size(max = 50, message = "昵称最长50个字符", groups = {CreateGroup.class, UpdateGroup.class})
    private String nickname;

    /** 角色标识(ADMIN:超管, OPERATOR:运维, VIEWER:普通) */
    @NotBlank(message = "角色不能为空", groups = {CreateGroup.class, UpdateGroup.class})
    @Pattern(regexp = "^(ADMIN|OPERATOR|VIEWER)$", message = "角色仅允许ADMIN/OPERATOR/VIEWER",
            groups = {CreateGroup.class, UpdateGroup.class})
    private String role;

    /** 状态 (0正常 1停用) */
    @JsonIgnore
    @Min(value = 0, message = "状态值只能为0/1", groups = {CreateGroup.class, UpdateGroup.class})
    @Max(value = 1, message = "状态值只能为0/1", groups = {CreateGroup.class, UpdateGroup.class})
    private Integer status;

    /** 连续登录失败次数（满足REQ-SEC-FR-001业务规则1：连续5次失败锁定） */
    @JsonIgnore
    @TableField("login_fail_count")
    private Integer loginFailCount;

    /** 账号锁定截止时间（满足REQ-SEC-FR-001业务规则1：锁定30分钟） */
    @JsonIgnore
    @TableField("lock_time")
    private LocalDateTime lockTime;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
}