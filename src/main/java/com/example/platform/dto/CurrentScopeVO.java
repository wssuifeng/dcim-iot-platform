package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 当前用户数据范围响应 VO（/auth/current-scope 端点）
 * <p>由 DataScopeService.resolveDataScope 解析后映射，供前端按当前范围切换操作面板与构造查询参数。</p>
 */
@Schema(description = "当前用户数据范围")
@Data
public class CurrentScopeVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Schema(description = "用户名", example = "org_admin")
    private String username;

    @Schema(description = "角色：PLATFORM_ADMIN/ORG_ADMIN/OPERATOR/VIEWER", example = "ORG_ADMIN")
    private String role;

    @Schema(description = "范围类型：PLATFORM/ORG_ALL/ROOMS/READ_ONLY", example = "ORG_ALL")
    private String scopeType;

    @Schema(description = "授权组织ID集合（PLATFORM_ADMIN 为空表示无限制）", example = "[1, 2]")
    private Set<Long> organizationIds;

    @Schema(description = "授权机房ID集合（PLATFORM_ADMIN 为空表示无限制）", example = "[10, 20]")
    private Set<Long> cloudRoomIds;

    @Schema(description = "是否具备写权限（READ_ONLY 范围为 false）", example = "true")
    private boolean writePermission;
}