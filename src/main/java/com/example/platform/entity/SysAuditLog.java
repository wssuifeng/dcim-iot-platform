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

    @Schema(description = "操作类型", example = "LOGIN_SUCCESS")
    @TableField("operation")
    private String operation;

    @Schema(description = "操作IP地址", example = "192.168.1.100")
    @TableField("ip")
    private String ip;

    @Schema(description = "操作详情", example = "用户 admin 登录成功")
    @TableField("detail")
    private String detail;

    @Schema(description = "操作时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}