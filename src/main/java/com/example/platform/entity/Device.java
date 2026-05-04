package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("devices")
public class Device implements Serializable {

    /** 设备ID（主键，自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 设备名称 */
    @TableField("device_name")  // 显式指定数据库字段名（虽然驼峰映射会自动处理，这里是为了可读性）
    private String deviceName;

    /** 设备编码（唯一标识，如SN码） */
    @TableField("device_code")
    private String deviceCode;

    /** 设备类型ID（关联 device_types 表） */
    @TableField("type_id")
    private Integer typeId;

    /** 设备状态：0-离线，1-在线，2-故障 */
    private Integer status;      // 数据库字段名与属性名完全一致，无需 @TableField

    /** 设备位置（如“3号楼2层东侧”） */
    private String location;     // 数据库字段名与属性名完全一致，无需 @TableField

    /** IP地址 */
    @TableField("ip_address")
    private String ipAddress;

    /** 最后在线时间 */
    @TableField("last_online_time")
    private LocalDateTime lastOnlineTime;

    /** 创建时间（插入时自动填充） */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（插入和更新时自动填充） */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

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