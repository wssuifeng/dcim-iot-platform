package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/*
 * 类功能:安全审计日志实体类，映射sys_audit_log表，记录用户登录及安全相关操作日志
 * */
@Data
@TableName("sys_audit_log")
public class SysAuditLog implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作人ID */
    @TableField("user_id")
    private Long userId;

    /** 操作人账号 */
    private String username;

    /** 操作类型(如：LOGIN_SUCCESS, LOGIN_FAIL, CREATE_USER) */
    @TableField("operation")
    private String operation;

    /** 操作IP地址 */
    @TableField("ip")
    private String ip;

    /** 操作详情(如密码错误1次) */
    @TableField("detail")
    private String detail;

    /** 操作时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}