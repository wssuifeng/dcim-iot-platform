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
import com.example.platform.service.SysUserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/*
 * 类功能:用户管理接口层，提供用户的增删改查、状态管理及密码重置等RESTful接口
 * */
@RestController
@RequestMapping("/users")
@Validated
public class SysUserController {

    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /*
     * 方法功能:分页查询用户列表，支持按用户名关键字搜索
     * 参数:current-当前页码, size-每页大小, keyword-用户名关键字（可选）
     * 返回数据:Result<Page<SysUser>>-分页用户数据
     * */
    @GetMapping("/page")
    public Result<Page<SysUser>> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        int[] pageParams = PaginationUtils.validate(current, size);
        return Result.success(sysUserService.pageUsers(pageParams[0], pageParams[1], keyword));
    }

    /*
     * 方法功能:按用户名精确查找用户
     * 参数:username-用户名
     * 返回数据:Result<SysUser>-查询到的用户对象，未找到返回404
     * */
    @GetMapping("/search")
    public Result<SysUser> search(@RequestParam String username) {
        SysUser user = sysUserService.findByUsername(username);
        if (user == null) {
            throw new UserNotFoundException(username);
        }
        return Result.success(user);
    }

    /*
     * 方法功能:根据ID查询单个用户详情
     * 参数:id-用户ID（路径参数）
     * 返回数据:Result<SysUser>-查询到的用户对象
     * */
    @GetMapping("/{id}")
    public Result<SysUser> getById(@PathVariable Long id) {
        return Result.success(sysUserService.getUserOrThrow(id));
    }

    /*
     * 方法功能:创建新用户
     * 参数:user-需符合CreateGroup校验规则的用户JSON对象
     * 返回数据:Result<Void>-操作结果
     * */
    @PostMapping
    @AuditLog(operation = "CREATE_USER", detail = "用户名:#user.username, 角色:#user.role")
    public Result<Void> create(@Validated(CreateGroup.class) @RequestBody SysUser user) {
        sysUserService.createUser(user);
        return Result.success();
    }

    /*
     * 方法功能:更新用户基本信息（昵称、角色），不支持密码变更
     * 参数:id-用户ID（路径参数）, user-需符合UpdateGroup校验规则的用户对象
     * 返回数据:Result<Void>-操作结果，ID不一致时返回错误
     * */
    @PutMapping("/{id}")
    @AuditLog(operation = "UPDATE_USER", detail = "用户ID:#id, 用户名:#user.username")
    public Result<Void> update(@PathVariable Long id,
                               @Validated(UpdateGroup.class) @RequestBody SysUser user) {
        if (user.getId() != null && !id.equals(user.getId())) {
            return Result.error(ResultCode.INVALID_PARAMETER, "请求ID不一致，无法更新");
        }
        user.setId(id);
        sysUserService.updateUser(user);
        return Result.success();
    }

    /*
     * 方法功能:重置用户密码，新密码经Service层BCrypt加密后存储
     * 参数:id-用户ID（路径参数）, dto-包含新密码的请求体
     * 返回数据:Result<Void>-操作结果
     * */
    @PutMapping("/{id}/password")
    @AuditLog(operation = "RESET_USER_PASSWORD", detail = "用户ID:#id")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Validated @RequestBody PasswordResetDTO dto) {
        sysUserService.resetPassword(id, dto.getNewPassword());
        return Result.success();
    }

    /*
     * 方法功能:启用或停用用户账号
     * 参数:id-用户ID（路径参数）, status-目标状态（0正常/1停用）
     * 返回数据:Result<Void>-操作结果
     * */
    @PutMapping("/{id}/status")
    @AuditLog(operation = "UPDATE_USER_STATUS", detail = "用户ID:#id, 状态:#status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        if (status < 0 || status > 1) {
            return Result.error(ResultCode.INVALID_PARAMETER, "状态值无效，允许值：0-正常，1-停用");
        }
        sysUserService.updateStatus(id, status);
        return Result.success();
    }

    /*
     * 方法功能:根据ID删除用户
     * 参数:id-用户ID（路径参数）
     * 返回数据:Result<Void>-操作结果
     * */
    @DeleteMapping("/{id}")
    @AuditLog(operation = "DELETE_USER", detail = "用户ID:#id")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.getUserOrThrow(id);
        sysUserService.removeById(id);
        return Result.success();
    }
}