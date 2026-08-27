package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.entity.SysUser;

/*
 * 类功能:用户服务接口，定义用户认证查询、账号管理及密码管理等业务方法
 * */
public interface SysUserService extends IService<SysUser> {

    /*
     * 方法功能:根据用户名查询用户完整信息（含密码、角色、状态），用于登录认证
     * 参数:username-用户名
     * 返回数据:SysUser-用户信息，不存在返回null
     * */
    SysUser findByUsername(String username);

    SysUser findByLoginIdentifier(String identifier);

    void requireIdentityAvailable(String email, String username);

    void createRegisteredUser(SysUser user);

    void createPlatformAdministrator(SysUser user);

    /*
     * 方法功能:用户存在性检查，不存在则抛出UserNotFoundException
     * 参数:id-用户ID
     * 返回数据:SysUser-查询到的用户对象
     * */
    SysUser getUserOrThrow(Long id);

    /*
     * 方法功能:创建新用户，校验用户名唯一性并对密码进行BCrypt加密
     * 参数:user-待创建的用户对象（含明文密码）
     * 返回数据:无
     * */
    void createUser(SysUser user);

    /*
     * 方法功能:更新用户基本信息（昵称、角色），不涉及密码变更
     * 参数:user-待更新的用户对象
     * 返回数据:无
     * */
    void updateUser(SysUser user);

    /*
     * 方法功能:重置用户密码，新密码经BCrypt加密后存储
     * 参数:id-用户ID, newPassword-新密码(明文8-32位)
     * 返回数据:无
     * */
    void resetPassword(Long id, String newPassword);

    /*
     * 方法功能:启用或停用用户账号
     * 参数:id-用户ID, status-目标状态(0正常/1停用)
     * 返回数据:无
     * */
    void updateStatus(Long id, Integer status);

    /*
     * 方法功能:按用户名关键字分页查询用户列表
     * 参数:current-当前页码, size-每页大小, keyword-用户名关键字(可选)
     * 返回数据:Page<SysUser>-分页用户数据
     * */
    Page<SysUser> pageUsers(Integer current, Integer size, String keyword);

    /*
     * 方法功能:重置用户登录失败计数为0（登录成功时调用）
     * 参数:id-用户ID
     * 返回数据:无
     * */
    void resetLoginFailCount(Long id);

    /*
     * 方法功能:递增登录失败计数，达到阈值(5次)时锁定账号30分钟
     * 参数:id-用户ID
     * 返回数据:int-更新后的失败次数
     * */
    int incrementLoginFailCount(Long id);
}
