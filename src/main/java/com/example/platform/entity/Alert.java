package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/*
 * 类功能:告警实体类，映射alerts表，记录设备产生的告警信息及其处理状态
 * */
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

    /** 告警类型（如"高温告警"） */
    @TableField("alert_type")
    private String alertType;

    /** 告警级别：1-紧急，2-严重，3-一般，4-警告，5-提示 */
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

    /** 逻辑删除标记：0-未删除，1-已删除 */
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
