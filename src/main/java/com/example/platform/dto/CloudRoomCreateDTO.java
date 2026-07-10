package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 云机房新增请求 DTO（ORG_ADMIN 专用，机房必须归属当前用户的组织）
 */
@Schema(description = "云机房新增请求")
@Data
public class CloudRoomCreateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "所属组织ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "所属组织不能为空")
    private Long organizationId;

    @Schema(description = "机房名称", example = "华东一号机房", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "机房名称不能为空")
    @Size(max = 100, message = "机房名称最长100个字符")
    private String roomName;

    @Schema(description = "机房编码（组织内唯一）", example = "ROOM-001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "机房编码不能为空")
    @Size(max = 50, message = "机房编码最长50个字符")
    private String roomCode;

    @Schema(description = "机房物理位置描述", example = "上海市浦东新区XX园区3号楼")
    @Size(max = 200, message = "位置描述最长200个字符")
    private String location;
}