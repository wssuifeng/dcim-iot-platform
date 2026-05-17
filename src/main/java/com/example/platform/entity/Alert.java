package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("alerts")
public class Alert implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /** 告警ID（主键，自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 设备ID（关联 devices 表） */
    @TableField("device_id")
    private Integer deviceId;

    /** 告警类型（如“高温告警”） */
    @TableField("alert_type")
    private String alertType;

    /** 告警级别：1-一般，2-严重，3-紧急 */
    @TableField("alert_level")
    private Integer alertLevel;

    /** 告警详细描述 */
    @TableField("alert_message")
    private String alertMessage;

    /** 是否处理：0-未处理，1-已处理 */
    @TableField("is_handled")
    private Integer isHandled;

    /** 处理时间 */
    @TableField(value = "handle_time")
    private LocalDateTime handleTime;

    /** 处理人（用户名或工号） */
    @TableField("handle_user")
    private String handleUser;

    /** 告警产生时间 */
    @TableField(value = "create_time" , fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /*
     * 逻辑删除字段（当前未启用）
     * 如需开启逻辑删除，请：
     * 1. 在数据库中执行：ALTER TABLE alerts ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除';
     * 2. 取消下方字段及 @TableLogic 注解的注释
     */
    // @TableLogic(value = "0", delval = "1")
    // private Integer deleted;
}