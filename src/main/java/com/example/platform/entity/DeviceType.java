package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.example.platform.dto.AlertRuleConfig;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/*
 * 类功能:设备类型实体类，映射device_types表，定义设备分类及其类型级告警规则配置
 * */
@Data
@TableName(value="device_types",autoResultMap = true)
public class DeviceType implements Serializable {

    /** 类型ID（主键，自增） */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 类型名称（如"温度传感器"） */
    @TableField("type_name")
    private String typeName;

    /** 类型编码（可用于程序内标识） */
    @TableField("type_code")
    private String typeCode;

    /** 类型描述 */
    private String description;

    /** 创建时间（插入时自动填充） */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（插入和更新时自动填充） */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 设备类型级别告警规则（JSON格式存储，作为设备级规则的降级配置） */
    @TableField(value = "alert_rule_config", typeHandler = JacksonTypeHandler.class)
    private AlertRuleConfig alertRuleConfig;
    /*
     * 逻辑删除字段（当前未启用）
     * 如需开启逻辑删除，请：
     * 1. 在数据库中执行：ALTER TABLE device_types ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除';
     * 2. 取消下方字段及 @TableLogic 注解的注释
     * 3. 确保 application.yml 中 mybatis-plus.global-config.db-config 已配置 logic-delete-field: deleted
     */
    // @TableLogic(value = "0", delval = "1")
    // private Integer deleted;
}
