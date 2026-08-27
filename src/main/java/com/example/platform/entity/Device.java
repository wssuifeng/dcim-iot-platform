package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.example.platform.common.group.CreateGroup;
import com.example.platform.common.group.UpdateGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 设备实体类，映射 devices 表，包含设备基本信息、状态及设备级告警规则配置
 */
@Schema(description = "设备")
@Data
@TableName(value = "devices" , autoResultMap = true)
public class Device implements Serializable {

    @Schema(description = "设备ID（主键，自增）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    @NotNull(message = "设备ID不能为空", groups = UpdateGroup.class)
    private Integer id;

    @Schema(description = "设备名称", example = "温度传感器A", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField("device_name")
    @NotBlank(message = "设备名称不能为空", groups = {CreateGroup.class, UpdateGroup.class})
    @Size(min = 2, max = 50, message = "设备名称长度需在2-50之间")
    private String deviceName;

    @Schema(description = "设备编码（唯一标识，如SN码）", example = "SN-001", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField("device_code")
    @NotBlank(message = "设备编码不能为空", groups = {CreateGroup.class, UpdateGroup.class})
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "设备编码只能包含字母、数字、下划线和短横线")
    private String deviceCode;

    @Schema(description = "设备类型ID（关联 device_types 表）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @TableField("type_id")
    @NotNull(message = "设备类型不能为空", groups = {CreateGroup.class, UpdateGroup.class})
    private Integer typeId;

    @Schema(description = "所属机房ID", example = "1")
    @TableField("cloud_room_id")
    private Long cloudRoomId;

    @Schema(description = "设备状态：0-离线，1-在线，2-故障", example = "1", allowableValues = {"0", "1", "2"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备状态不能为空", groups = {CreateGroup.class, UpdateGroup.class})
    @Min(value = 0, message = "状态值只能为0/1/2")
    @Max(value = 2, message = "状态值只能为0/1/2")
    private Integer status;

    @Schema(description = "设备位置", example = "3号楼2层", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "设备位置不能为空", groups = {CreateGroup.class, UpdateGroup.class})
    @Size(max = 100, message = "位置信息最长100个字符")
    private String location;

    @Schema(description = "IP地址（可选）", example = "192.168.1.100")
    @TableField("ip_address")
    @Pattern(regexp = "^$|^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$",
            message = "IP地址格式不正确")
    private String ipAddress;

    @Schema(description = "最后在线时间（系统自动记录）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField("last_online_time")
    private LocalDateTime lastOnlineTime;

    @Schema(description = "最后上报所经网关凭证ID（接入视角）", accessMode = Schema.AccessMode.READ_ONLY)
    @TableField("last_ingest_credential_id")
    private Long lastIngestCredentialId;

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
