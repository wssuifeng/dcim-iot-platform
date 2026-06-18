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
    /** 逻辑删除标记：0-未删除，1-已删除 */
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
