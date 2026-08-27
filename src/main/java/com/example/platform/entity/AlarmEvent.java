package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警事件实体类，映射 alarm_event 表，产品化权威告警状态机
 * state 取值：TRIGGERED/ACKED/HANDLING/RECOVERED/CLOSED
 * 与 v1.0 遗留 Alert（alerts 表）不建立强制外键，两者并存，Alert.isHandled 仅作兼容展示
 */
@Schema(description = "告警事件（权威状态机）")
@Data
@TableName("alarm_event")
public class AlarmEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "告警事件ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "设备ID（关联 devices）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备ID不能为空")
    @TableField("device_id")
    private Integer deviceId;

    @Schema(description = "触发点位ID（关联 point_definition，可为空）", example = "1")
    @TableField("point_id")
    private Long pointId;

    @Schema(description = "告警类型", example = "高温告警")
    @TableField("alarm_type")
    private String alarmType;

    @Schema(description = "告警级别：1-紧急 2-严重 3-一般 4-警告 5-提示", example = "2")
    @TableField("alarm_level")
    private Integer alarmLevel;

    @Schema(description = "告警详细描述")
    @TableField("alarm_message")
    private String alarmMessage;

    @Schema(description = "权威状态：TRIGGERED/ACKED/HANDLING/RECOVERED/CLOSED", example = "TRIGGERED",
            allowableValues = {"TRIGGERED", "ACKED", "HANDLING", "RECOVERED", "CLOSED"})
    private String state;

    @Schema(description = "所属机房ID（数据范围校验依据）", example = "1")
    @TableField("cloud_room_id")
    private Long cloudRoomId;

    @Schema(description = "触发时间（自动填充）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField(value = "triggered_time", fill = FieldFill.INSERT)
    private LocalDateTime triggeredTime;

    @Schema(description = "恢复时间")
    @TableField("recovered_time")
    private LocalDateTime recoveredTime;

    @Schema(description = "关闭时间")
    @TableField("closed_time")
    private LocalDateTime closedTime;

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
