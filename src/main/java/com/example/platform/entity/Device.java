package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.example.platform.common.group.CreateGroup;
import com.example.platform.common.group.UpdateGroup;
import com.example.platform.dto.AlertRuleConfig;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/*
 * 类功能:设备实体类，映射devices表，包含设备基本信息、状态及设备级告警规则配置
 * */
@Data
@TableName(value = "devices" , autoResultMap = true)
public class Device implements Serializable {

    /** 设备ID（主键，自增） */
    @TableId(value = "id", type = IdType.AUTO)
    @NotNull(message = "设备ID不能为空", groups = UpdateGroup.class)
    private Integer id;

    /** 设备名称 */
    @TableField("device_name")
    @NotBlank(message = "设备名称不能为空", groups = {CreateGroup.class, UpdateGroup.class})
    @Size(min = 2, max = 50, message = "设备名称长度需在2-50之间")
    private String deviceName;

    /** 设备编码（唯一标识，如SN码） */
    @TableField("device_code")
    @NotBlank(message = "设备编码不能为空", groups = {CreateGroup.class, UpdateGroup.class})
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "设备编码只能包含字母、数字、下划线和短横线")
    private String deviceCode;

    /** 设备类型ID（关联 device_types 表） */
    @TableField("type_id")
    @NotNull(message = "设备类型不能为空", groups = {CreateGroup.class, UpdateGroup.class})
    private Integer typeId;

    /** 设备状态：0-离线，1-在线，2-故障 */
    @NotNull(message = "设备状态不能为空", groups = {CreateGroup.class, UpdateGroup.class})
    @Min(value = 0, message = "状态值只能为0/1/2")
    @Max(value = 2, message = "状态值只能为0/1/2")
    private Integer status;

    /** 设备位置（创建时必须告知） */
    @NotBlank(message = "设备位置不能为空", groups = {CreateGroup.class, UpdateGroup.class})
    @Size(max = 100, message = "位置信息最长100个字符")
    private String location;

    /** IP地址（可选，有值时校验格式） */
    @TableField("ip_address")
    @Pattern(regexp = "^$|^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$",
            message = "IP地址格式不正确")
    private String ipAddress;

    /** 最后在线时间（系统自动记录，不接收用户传入） */
    @TableField("last_online_time")
    private LocalDateTime lastOnlineTime;

    /** 创建时间（插入时自动填充） */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（插入和更新时自动填充） */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 设备级告警规则（JSON格式存储，设备级优先于类型级） */
    @TableField(value = "alert_rule_config", typeHandler = JacksonTypeHandler.class)
    private AlertRuleConfig alertRuleConfig;

    /*
     * 逻辑删除字段（当前未启用）
     * 如需开启逻辑删除，请：
     * 1. 在数据库中执行：ALTER TABLE devices ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除';
     * 2. 取消下方字段及 @TableLogic 注解的注释
     * 3. 确保 application.yml 中 mybatis-plus.global-config.db-config 已配置 logic-delete-field: deleted
     */
    // @TableLogic(value = "0", delval = "1")
    // private Integer deleted;
}
