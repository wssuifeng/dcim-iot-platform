package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.UserNotFoundException;
import com.example.platform.common.exception.UsernameAlreadyExistsException;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.SysUserMapper;
import com.example.platform.service.SysUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/*
 * 类功能:用户服务实现类，负责用户的CRUD、密码加密、账号状态管理及登录查询
 * */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final PasswordEncoder passwordEncoder;

    public SysUserServiceImpl(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    /*
     * 方法功能:根据用户名查询用户完整信息（含密码、角色、状态），用于登录认证
     * 参数:username-用户名
     * 返回数据:SysUser-用户信息，不存在返回null
     * */
    @Override
    public SysUser findByUsername(String username) {
        return baseMapper.findByUsername(username);
    }

    /*
     * 方法功能:用户存在性检查，不存在则抛出UserNotFoundException
     * 参数:id-用户ID
     * 返回数据:SysUser-查询到的用户对象
     * */
    @Override
    public SysUser getUserOrThrow(Long id) {
        SysUser user = getById(id);
        if (user == null) {
            throw new UserNotFoundException(id);
        }
        return user;
    }

    /*
     * 方法功能:创建新用户，校验用户名唯一性并对密码进行BCrypt加密
     * 参数:user-待创建的用户对象（含明文密码）
     * 返回数据:无
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void createUser(SysUser user) {
        checkUsernameUnique(user.getUsername());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getStatus() == null) {
            user.setStatus(0);
        }
        if (user.getRole() == null) {
            user.setRole("VIEWER");
        }
        if (!super.save(user)) {
            throw new BusinessException("创建用户失败，请稍后重试");
        }
    }

    /*
     * 方法功能:更新用户基本信息（昵称、角色），不涉及密码变更
     * 参数:user-待更新的用户对象
     * 返回数据:无
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateUser(SysUser user) {
        // 校验用户存在性
        getUserOrThrow(user.getId());
        // 使用LambdaUpdateWrapper仅更新允许的字段，防止通过请求体注入password/status等敏感字段
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysUser::getId, user.getId());
        if (user.getNickname() != null) {
            wrapper.set(SysUser::getNickname, user.getNickname());
        }
        if (user.getRole() != null) {
            wrapper.set(SysUser::getRole, user.getRole());
        }
        if (!update(wrapper)) {
            throw new BusinessException("更新用户失败，数据可能已被他人修改");
        }
    }

    /*
     * 方法功能:重置用户密码，新密码经BCrypt加密后存储
     * 参数:id-用户ID, newPassword-新密码(明文8-32位)
     * 返回数据:无
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void resetPassword(Long id, String newPassword) {
        getUserOrThrow(id);
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysUser::getId, id)
                .set(SysUser::getPassword, passwordEncoder.encode(newPassword));
        update(wrapper);
    }

    /*
     * 方法功能:启用或停用用户账号
     * 参数:id-用户ID, status-目标状态(0正常/1停用)
     * 返回数据:无
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateStatus(Long id, Integer status) {
        getUserOrThrow(id);
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysUser::getId, id)
                .set(SysUser::getStatus, status);
        update(wrapper);
    }

    /*
     * 方法功能:按用户名关键字分页查询用户列表，按创建时间倒序排列
     * 参数:current-当前页码, size-每页大小, keyword-用户名关键字(可选), scope-当前用户数据范围
     * 返回数据:Page<SysUser>-分页用户数据
     * */
    @Override
    public Page<SysUser> pageUsers(Integer current, Integer size, String keyword,
                                   DataScopeContext scope) {
        Page<SysUser> page = new Page<>(current, size);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();

        if (!scope.isPlatformAdmin()) {
            Set<Long> organizationIds = scope.getOrganizationIds();
            if (organizationIds == null || organizationIds.isEmpty()) {
                return page;
            }
            return baseMapper.selectPageByOrganizationScope(
                    page, normalizedKeyword, organizationIds);
        }

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (normalizedKeyword != null) {
            wrapper.like(SysUser::getUsername, normalizedKeyword);
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        return page(page, wrapper);
    }

    /*
     * 方法功能:校验用户名是否唯一，已存在则抛出UsernameAlreadyExistsException
     * 参数:username-待校验的用户名
     * 返回数据:无
     * */
    private void checkUsernameUnique(String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        if (count(wrapper) > 0) {
            throw new UsernameAlreadyExistsException(username);
        }
    }

    /*
     * 方法功能:重置用户登录失败计数为0并清除锁定时间（登录成功时调用）
     * 参数:id-用户ID
     * 返回数据:无
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void resetLoginFailCount(Long id) {
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysUser::getId, id)
                .set(SysUser::getLoginFailCount, 0)
                .set(SysUser::getLockTime, null);
        update(wrapper);
    }

    /*
     * 方法功能:递增登录失败计数，达到5次时锁定账号30分钟
     * 参数:id-用户ID
     * 返回数据:int-更新后的失败次数
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int incrementLoginFailCount(Long id) {
        // 第1步：使用SQL原子递增，避免并发竞态条件（read-then-write）
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysUser::getId, id)
                .setSql("login_fail_count = COALESCE(login_fail_count, 0) + 1");
        update(wrapper);

        // 第2步：读取递增后的最新计数值
        SysUser afterUpdate = getById(id);
        int currentCount = afterUpdate.getLoginFailCount() != null ? afterUpdate.getLoginFailCount() : 0;

        // 第3步：达到阈值且尚未锁定时，设置锁定截止时间
        if (currentCount >= 3 && afterUpdate.getLockTime() == null) {
            LambdaUpdateWrapper<SysUser> lockWrapper = new LambdaUpdateWrapper<>();
            lockWrapper.eq(SysUser::getId, id)
                    .set(SysUser::getLockTime, java.time.LocalDateTime.now().plusMinutes(30));
            update(lockWrapper);
        }

        return currentCount;
    }
}
