package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户数据范围分配实体类，映射 sys_data_scope_assignment 表
 * scopeType 取值：PLATFORM / ORG_ALL / ROOMS / READ_ONLY（服务层校验见 INFRA-CLAUDE-02）
 */
@Schema(description = "用户数据范围分配")
@Data
@TableName("sys_data_scope_assignment")
public class DataScopeAssignment implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "分配ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户ID（关联 sys_user）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "用户ID不能为空")
    @TableField("user_id")
    private Long userId;

    @Schema(description = "组织ID（PLATFORM范围可为空）", example = "1")
    @TableField("organization_id")
    private Long organizationId;

    @Schema(description = "机房ID（仅 ROOMS 范围类型使用）", example = "1")
    @TableField("cloud_room_id")
    private Long cloudRoomId;

    @Schema(description = "范围类型：PLATFORM/ORG_ALL/ROOMS/READ_ONLY", example = "ORG_ALL",
            allowableValues = {"PLATFORM", "ORG_ALL", "ROOMS", "READ_ONLY"}, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "范围类型不能为空")
    @TableField("scope_type")
    private String scopeType;

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
