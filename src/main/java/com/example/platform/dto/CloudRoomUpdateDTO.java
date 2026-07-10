package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 云机房更新请求 DTO（ORG_ADMIN 专用）
 * <p>不允许修改 organizationId 与 roomCode，组织归属和编码在创建时固定。</p>
 */
@Schema(description = "云机房更新请求")
@Data
public class CloudRoomUpdateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "机房名称", example = "华东一号机房")
    @Size(max = 100, message = "机房名称最长100个字符")
    private String roomName;

    @Schema(description = "机房物理位置描述", example = "上海市浦东新区XX园区3号楼")
    @Size(max = 200, message = "位置描述最长200个字符")
    private String location;

    @Schema(description = "状态：0-正常，1-停用", example = "0")
    @NotNull(message = "状态不能为空，0-正常 1-停用")
    private Integer status;
}