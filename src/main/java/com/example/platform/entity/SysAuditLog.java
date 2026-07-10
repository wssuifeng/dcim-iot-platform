package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志实体类，映射 sys_audit_log 表，记录用户操作行为的审计追踪信息
 */
@Schema(description = "审计日志")
@Data
@TableName("sys_audit_log")
public class SysAuditLog implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "日志ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "操作人ID", example = "1")
    @TableField("user_id")
    private Long userId;

    @Schema(description = "操作人账号", example = "admin")
    private String username;

    @Schema(description = "操作人角色（结构化字段，产品化新增）", example = "ORG_ADMIN")
    @TableField("role")
    private String role;

    @Schema(description = "操作人所属组织ID（结构化字段，产品化新增）", example = "1")
    @TableField("organization_id")
    private Long organizationId;

    @Schema(description = "操作人所属机房ID（结构化字段，产品化新增）", example = "1")
    @TableField("cloud_room_id")
    private Long cloudRoomId;

    @Schema(description = "操作类型", example = "LOGIN_SUCCESS")
    @TableField("operation")
    private String operation;

    @Schema(description = "标准化操作动作（结构化字段，与 operation 并存，产品化新增）", example = "LOGIN")
    @TableField("action")
    private String action;

    @Schema(description = "操作目标类型（结构化字段，产品化新增）", example = "DEVICE")
    @TableField("target_type")
    private String targetType;

    @Schema(description = "操作目标ID（结构化字段，产品化新增）", example = "1")
    @TableField("target_id")
    private String targetId;

    @Schema(description = "操作结果：SUCCESS/FAILURE/REJECTED（结构化字段，产品化新增）", example = "SUCCESS")
    @TableField("result")
    private String result;

    @Schema(description = "错误码（结构化字段，产品化新增）", example = "AUTH_INVALID_CREDENTIALS")
    @TableField("error_code")
    private String errorCode;

    @Schema(description = "操作IP地址", example = "192.168.1.100")
    @TableField("ip")
    private String ip;

    @Schema(description = "链路追踪ID（结构化字段，产品化新增）")
    @TableField("trace_id")
    private String traceId;

    @Schema(description = "操作详情", example = "用户 admin 登录成功")
    @TableField("detail")
    private String detail;

    @Schema(description = "操作时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}