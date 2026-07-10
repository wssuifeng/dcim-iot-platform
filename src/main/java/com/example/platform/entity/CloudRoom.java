package com.example.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 云机房实体类，映射 sys_cloud_room 表，隶属于组织的数据范围二级单元
 */
@Schema(description = "云机房")
@Data
@TableName("sys_cloud_room")
public class CloudRoom implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "机房ID（主键）", example = "1")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "所属组织ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属组织不能为空")
    @TableField("organization_id")
    private Long organizationId;

    @Schema(description = "机房名称", example = "华东一号机房", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "机房名称不能为空")
    @Size(max = 100, message = "机房名称最长100个字符")
    @TableField("room_name")
    private String roomName;

    @Schema(description = "机房编码（组织内唯一）", example = "ROOM-001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "机房编码不能为空")
    @Size(max = 50, message = "机房编码最长50个字符")
    @TableField("room_code")
    private String roomCode;

    @Schema(description = "机房物理位置描述", example = "上海市浦东新区XX园区3号楼")
    @Size(max = 200, message = "位置描述最长200个字符")
    private String location;

    @Schema(description = "状态：0-正常，1-停用", example = "0")
    private Integer status;

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
