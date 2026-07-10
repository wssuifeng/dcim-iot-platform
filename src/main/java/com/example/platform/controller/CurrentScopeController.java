package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.CurrentScopeVO;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.DataScopeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户数据范围查询接口（/auth/current-scope），独立于 {@link AuthController}。
 *
 * <p>该端点为只读：基于 {@link CurrentUserContext} 与 {@link DataScopeService} 解析当前用户
 * 平台角色与授权数据范围，供前端按当前范围切换操作面板与构造查询参数。
 * 任何已认证用户均可调用；安全边界由服务层范围过滤保证。</p>
 */
@Tag(name = "当前用户范围", description = "查询当前登录用户的数据范围上下文")
@RestController
public class CurrentScopeController {

    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;

    public CurrentScopeController(CurrentUserContext currentUserContext,
                                 DataScopeService dataScopeService) {
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
    }

    @Operation(summary = "查询当前用户数据范围",
            description = "返回当前登录用户的角色、scopeType、授权组织ID与机房ID集合、写权限标记")
    @GetMapping("/auth/current-scope")
    public Result<CurrentScopeVO> currentScope() {
        CurrentUser user = currentUserContext.requireCurrentUser();
        DataScopeContext context = dataScopeService.resolveDataScope(user.userId());
        CurrentScopeVO vo = new CurrentScopeVO();
        vo.setUserId(context.getUserId());
        vo.setUsername(user.username());
        vo.setRole(context.getRole());
        vo.setScopeType(context.getScopeType());
        vo.setOrganizationIds(context.getOrganizationIds());
        vo.setCloudRoomIds(context.getCloudRoomIds());
        vo.setWritePermission(context.hasWritePermission());
        return Result.success(vo);
    }
}