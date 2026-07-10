package com.example.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Set;

/**
 * 数据范围上下文，封装当前用户的角色和授权数据范围信息
 * 由 DataScopeService 根据用户ID和角色从 DataScopeAssignment 表解析生成
 */
@Schema(description = "数据范围上下文")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataScopeContext {

    @Schema(description = "用户ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;

    @Schema(description = "用户角色：PLATFORM_ADMIN/ORG_ADMIN/OPERATOR/VIEWER",
            example = "ORG_ADMIN", requiredMode = Schema.RequiredMode.REQUIRED)
    private String role;

    @Schema(description = "授权组织ID集合（PLATFORM_ADMIN 可为空）", example = "[1, 2]")
    @Builder.Default
    private Set<Long> organizationIds = Collections.emptySet();

    @Schema(description = "授权机房ID集合（ROOMS 范围类型使用）", example = "[10, 20]")
    @Builder.Default
    private Set<Long> cloudRoomIds = Collections.emptySet();

    @Schema(description = "范围类型：PLATFORM/ORG_ALL/ROOMS/READ_ONLY",
            example = "ORG_ALL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String scopeType;

    /**
     * 判断是否为平台管理员
     */
    public boolean isPlatformAdmin() {
        return "PLATFORM_ADMIN".equals(role);
    }

    /**
     * 判断是否为组织管理员
     */
    public boolean isOrgAdmin() {
        return "ORG_ADMIN".equals(role);
    }

    /**
     * 判断是否为运维人员
     */
    public boolean isOperator() {
        return "OPERATOR".equals(role);
    }

    /**
     * 判断是否为只读查看者
     */
    public boolean isViewer() {
        return "VIEWER".equals(role);
    }

    /**
     * 判断是否拥有指定组织的访问权限
     */
    public boolean hasOrganizationAccess(Long organizationId) {
        if (organizationId == null) {
            return false;
        }
        return isPlatformAdmin() || organizationIds.contains(organizationId);
    }

    /**
     * 判断是否拥有指定机房的访问权限
     */
    public boolean hasCloudRoomAccess(Long cloudRoomId) {
        if (cloudRoomId == null) {
            return false;
        }
        return isPlatformAdmin() || cloudRoomIds.contains(cloudRoomId);
    }

    /**
     * 判断是否拥有写权限（READ_ONLY 范围类型无写权限）
     */
    public boolean hasWritePermission() {
        return !"READ_ONLY".equals(scopeType);
    }
}
