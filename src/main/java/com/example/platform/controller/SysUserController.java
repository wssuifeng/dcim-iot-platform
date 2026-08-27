package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.ResultCode;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.common.exception.UserNotFoundException;
import com.example.platform.common.group.CreateGroup;
import com.example.platform.common.group.UpdateGroup;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.PasswordResetDTO;
import com.example.platform.entity.SysUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理接口层，提供用户的增删改查、状态管理及密码重置等RESTful接口
 */
@Tag(name = "用户管理", description = "用户 CRUD、状态管理与密码重置（仅 ADMIN 可访问）")
@RestController
@RequestMapping("/users")
@Validated
public class SysUserController {

    private final SysUserService sysUserService;
    private final CurrentUserContext currentUserContext;

    public SysUserController(SysUserService sysUserService,
                             CurrentUserContext currentUserContext) {
        this.sysUserService = sysUserService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "分页查询用户", description = "支持按用户名 keyword 模糊搜索")
    @GetMapping("/page")
    public Result<Page<SysUser>> page(
            @Parameter(description = "当前页码，默认1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "用户名关键字（可选）") @RequestParam(required = false) String keyword) {
        int[] pageParams = PaginationUtils.validate(current, size);
        return Result.success(sysUserService.pageUsers(
                pageParams[0], pageParams[1], keyword));
    }

    @Operation(summary = "按用户名精确查找", description = "未找到时抛出 UserNotFoundException 返回 404")
    @GetMapping("/search")
    public Result<SysUser> search(
            @Parameter(description = "用户名", required = true) @RequestParam String username) {
        SysUser user = sysUserService.findByUsername(username);
        if (user == null) {
            throw new UserNotFoundException(username);
        }
        return Result.success(user);
    }

    @Operation(summary = "根据ID查询用户", description = "返回单条用户记录")
    @GetMapping("/{id}")
    public Result<SysUser> getById(
            @Parameter(description = "用户ID", required = true) @PathVariable Long id) {
        return Result.success(sysUserService.getUserOrThrow(id));
    }

    @Operation(summary = "创建用户", description = "密码由 Service 层 BCrypt 加密后存储；用户名重复时抛出 UsernameAlreadyExistsException")
    @PostMapping
    @AuditLog(operation = "CREATE_USER", detail = "用户名:#user.username, 角色:#user.role")
    public Result<Void> create(@Validated(CreateGroup.class) @RequestBody SysUser user) {
        sysUserService.createUser(user);
        return Result.success();
    }

    @Operation(summary = "更新用户信息", description = "更新昵称、角色等基本信息，不支持密码变更；防御性校验请求体ID一致性")
    @PutMapping("/{id}")
    @AuditLog(operation = "UPDATE_USER", detail = "用户ID:#id, 用户名:#user.username")
    public Result<Void> update(
            @Parameter(description = "用户ID", required = true) @PathVariable Long id,
            @Validated(UpdateGroup.class) @RequestBody SysUser user) {
        if (user.getPassword() != null) {
            return Result.error(ResultCode.INVALID_PARAMETER, "请使用密码重置接口修改密码");
        }
        if (user.getId() != null && !id.equals(user.getId())) {
            return Result.error(ResultCode.INVALID_PARAMETER, "请求ID不一致，无法更新");
        }
        user.setId(id);
        sysUserService.updateUser(user);
        return Result.success();
    }

    @Operation(summary = "重置用户密码", description = "新密码由 Service 层 BCrypt 加密后存储")
    @PutMapping("/{id}/password")
    @AuditLog(operation = "RESET_USER_PASSWORD", detail = "用户ID:#id")
    public Result<Void> resetPassword(
            @Parameter(description = "用户ID", required = true) @PathVariable Long id,
            @Validated @RequestBody PasswordResetDTO dto) {
        sysUserService.resetPassword(id, dto.getNewPassword());
        return Result.success();
    }

    @Operation(summary = "启用/停用账号", description = "status 取值：0-正常，1-停用；超出范围返回 400")
    @PutMapping("/{id}/status")
    @AuditLog(operation = "UPDATE_USER_STATUS", detail = "用户ID:#id, 状态:#status")
    public Result<Void> updateStatus(
            @Parameter(description = "用户ID", required = true) @PathVariable Long id,
            @Parameter(description = "状态：0-正常，1-停用", required = true) @RequestParam Integer status) {
        if (status < 0 || status > 1) {
            return Result.error(ResultCode.INVALID_PARAMETER, "状态值无效，允许值：0-正常，1-停用");
        }
        sysUserService.updateStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "删除用户", description = "用户不存在时抛出 UserNotFoundException")
    @DeleteMapping("/{id}")
    @AuditLog(operation = "DELETE_USER", detail = "用户ID:#id")
    public Result<Void> delete(
            @Parameter(description = "用户ID", required = true) @PathVariable Long id) {
        sysUserService.getUserOrThrow(id);
        sysUserService.removeById(id);
        return Result.success();
    }
}
