package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警实体类，映射 alerts 表，记录设备产生的告警信息及其处理状态
 */
@Schema(description = "告警")
@Data
@TableName("alerts")
public class Alert implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "告警ID（主键，自增）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Schema(description = "设备ID（关联 devices 表）", example = "5")
    @TableField("device_id")
    private Integer deviceId;

    @Schema(description = "告警类型", example = "高温告警")
    @TableField("alert_type")
    private String alertType;

    @Schema(description = "告警级别：1-紧急，2-严重，3-一般，4-警告，5-提示", example = "2", allowableValues = {"1", "2", "3", "4", "5"})
    @TableField("alert_level")
    private Integer alertLevel;

    @Schema(description = "告警详细描述", example = "温度传感器A当前值 42.5℃，超过阈值 40.0℃")
    @TableField("alert_message")
    private String alertMessage;

    @Schema(description = "是否处理：0-未处理，1-已处理", example = "0", allowableValues = {"0", "1"})
    @TableField("is_handled")
    private Integer isHandled;

    @Schema(description = "处理时间")
    @TableField(value = "handle_time")
    private LocalDateTime handleTime;

    @Schema(description = "处理人", example = "张三")
    @TableField("handle_user")
    private String handleUser;

    @Schema(description = "告警产生时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "create_time" , fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(hidden = true)
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
