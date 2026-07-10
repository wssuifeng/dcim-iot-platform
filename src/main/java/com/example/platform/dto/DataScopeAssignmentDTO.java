package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户数据范围分配请求 DTO（PLATFORM_ADMIN / ORG_ADMIN 给用户分配范围时使用）
 * <p>
 * scopeType 取值约束：
 * <ul>
 *   <li>PLATFORM：平台级，仅用于 PLATFORM_ADMIN（organizationId/cloudRoomId 应为空）</li>
 *   <li>ORG_ALL：本组织全部机房（须填 organizationId，cloudRoomId 留空）</li>
 *   <li>ROOMS：指定机房（须填 organizationId 与 cloudRoomId）</li>
 *   <li>READ_ONLY：只读范围（须填 organizationId，cloudRoomId 可选）</li>
 * </ul>
 */
@Schema(description = "用户数据范围分配请求")
@Data
public class DataScopeAssignmentDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "组织ID（PLATFORM 范围可为空）", example = "1")
    private Long organizationId;

    @Schema(description = "机房ID（仅 ROOMS 范围类型使用）", example = "10")
    private Long cloudRoomId;

    @Schema(description = "范围类型：PLATFORM/ORG_ALL/ROOMS/READ_ONLY", example = "ORG_ALL",
            allowableValues = {"PLATFORM", "ORG_ALL", "ROOMS", "READ_ONLY"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "范围类型不能为空")
    private String scopeType;
}