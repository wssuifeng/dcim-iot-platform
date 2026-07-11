package com.example.platform.service.impl;

import com.example.platform.common.ResultCode;
import com.example.platform.common.constant.LoginFailScript;
import com.example.platform.common.exception.AccountLockedException;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.util.JwtUtil;
import com.example.platform.dto.LoginDTO;
import com.example.platform.dto.LoginResponseDTO;
import com.example.platform.entity.SysUser;
import com.example.platform.security.PlatformRole;
import com.example.platform.service.AuthService;
import com.example.platform.service.CacheService;
import com.example.platform.service.SysAuditLogService;
import com.example.platform.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/*
 * 类功能:认证服务实现类，实现用户登录认证核心逻辑
 * 包含：账号状态检查、密码验证、Redis滑动窗口失败计数与锁定、JWT签发、审计日志记录
 *
 * 【统一失败计数路径】
 * 删除了 checkIpLocked 前置拦截，所有锁定判断统一由 recordFailAndThrow 中的 Lua 返回值处理。
 * 原因：
 *   1. checkIpLocked 调用 RECORD_FAIL 脚本会白白消耗失败计数（每次登录消耗2次）
 *   2. 统一路径减少1次Redis网络往返
 *   3. 防止用户枚举攻击（原来锁定状态在查用户之前就暴露了）
 *
 * 【登录失败计数——从DB到Redis Lua的演进】
 * 旧方案：DB字段fail_count+lock_time + 内存ConcurrentHashMap
 *   - ConcurrentHashMap：单机有效，多实例部署时各自计数，5次限制形同虚设
 *   - DB读写：每次失败2次DB操作（incrementLoginFailCount + 查询），高并发下成为瓶颈
 *   - 固定窗口：无滑动窗口概念，边界突刺可绕过限制
 *
 * 新方案：Redis Lua脚本实现滑动窗口+锁定状态机
 *   - 原子性：Lua脚本内"读-判-写"不可中断，无并发竞态
 *   - 分布式：多实例共享同一Redis，计数全局一致
 *   - 滑动窗口：30分钟内失败5次才锁定，无边界突刺
 *   - 性能：1次Lua调用替代2次DB操作，Redis内存操作微秒级
 * */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final String FAIL_STATE_PREFIX = "auth:fail:state:";
    private static final int MAX_LOGIN_FAIL_COUNT = 5;

    private final SysUserService sysUserService;
    private final SysAuditLogService sysAuditLogService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final CacheService cacheService;

    public AuthServiceImpl(SysUserService sysUserService,
                           SysAuditLogService sysAuditLogService,
                           JwtUtil jwtUtil,
                           PasswordEncoder passwordEncoder,
                           CacheService cacheService) {
        this.sysUserService = sysUserService;
        this.sysAuditLogService = sysAuditLogService;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.cacheService = cacheService;
    }

    /*
     * 方法功能:用户登录认证
     * 流程:查找用户 → 检查停用 → 验证密码 → 签发Token → 记录审计日志
     * 参数:loginDTO-登录请求体, ip-客户端IP
     * 返回数据:LoginResponseDTO-登录成功响应
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public LoginResponseDTO login(LoginDTO loginDTO, String ip) {
        SysUser user = sysUserService.findByUsername(loginDTO.getUsername());

        if (user == null) {
            recordFailAndThrow(ip, null, null, "用户不存在");
        }

        checkAccountDisabled(user);

        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            recordFailAndThrow(ip, user.getId(), user.getUsername(), "密码错误");
        }

        cacheService.delete(FAIL_STATE_PREFIX + ip);
        sysUserService.resetLoginFailCount(user.getId());

        String platformRole = PlatformRole.PLATFORM_ADMIN.equals(user.getPlatformRole())
                ? PlatformRole.PLATFORM_ADMIN : null;
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), platformRole);

        sysAuditLogService.log(user.getId(), user.getUsername(), "LOGIN_SUCCESS", ip, "登录成功");

        return buildLoginResponse(token, user);
    }

    /*
     * 方法功能:记录登录失败并抛出异常
     * 流程:调用Lua脚本递增失败计数 → 根据返回状态码构造友好提示 → 抛出异常
     *
     * 【统一锁定判断】
     * Lua脚本返回 [状态码, 重试次数, 锁定剩余秒数]：
     *   - 状态码0：正常失败，value为剩余尝试次数
     *   - 状态码1：已处于锁定期，value为锁定剩余秒数
     *   - 状态码2：刚触发锁定，value为锁定总秒数
     *
     * 参数:ip-客户端IP, userId-用户ID(可能为null), username-用户名(可能为null), detail-失败详情
     * 返回数据:无（始终抛出BusinessException或AccountLockedException）
     * */
    private void recordFailAndThrow(String ip, Long userId, String username, String detail) {
        sysAuditLogService.log(userId, username, "LOGIN_FAIL", ip, detail);

        List<Long> result = executeLoginFailLua(ip);
        if (result == null || result.size() < 2) {
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }

        LoginFailScript.LoginFailResult failResult = LoginFailScript.LoginFailResult.fromLuaResult(result);

        if (failResult.isLocked()) {
            throw new AccountLockedException(failResult.lockRemainSec() + "秒");
        }

        throw new BusinessException(
                ResultCode.INVALID_CREDENTIALS,
                "用户名或密码错误，剩余尝试次数" + failResult.remainingAttempts(MAX_LOGIN_FAIL_COUNT) + "次");
    }

    /*
     * 方法功能:执行登录失败Lua脚本，组装ARGV参数
     *
     * 【ARGV参数说明】
     * ARGV[1] = 当前时间戳（毫秒）  → Java端System.currentTimeMillis()
     * ARGV[2] = 滑动窗口时长（毫秒） → jwtUtil.getFailWindow() * 1000
     * ARGV[3] = 最大失败次数          → MAX_LOGIN_FAIL_COUNT
     * ARGV[4] = 锁定时长（毫秒）     → jwtUtil.getLockDuration() * 1000
     *
     * 参数:ip-客户端IP
     * 返回数据:List<Long>-Lua脚本返回值，Redis异常时返回null
     * */
    @SuppressWarnings("unchecked")
    private List<Long> executeLoginFailLua(String ip) {
        String key = FAIL_STATE_PREFIX + ip;
        long nowMs = System.currentTimeMillis();
        long windowMs = jwtUtil.getFailWindow() * 1000;
        long lockMs = jwtUtil.getLockDuration() * 1000;

        return (List<Long>) cacheService.executeLua(
                LoginFailScript.RECORD_FAIL,
                List.class,
                List.of(key),
                nowMs, windowMs, MAX_LOGIN_FAIL_COUNT, lockMs
        );
    }

    /*
     * 方法功能:检查账号是否被停用
     * 参数:user-用户对象
     * 返回数据:无
     * */
    private void checkAccountDisabled(SysUser user) {
        if (user.getStatus() != null && user.getStatus() == 1) {
            log.warn("账号已停用: userId={}", user.getId());
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED, "账号已被停用，请联系管理员");
        }
    }

    /*
     * 方法功能:构建登录成功响应对象
     * 参数:token-JWT令牌, user-用户对象
     * 返回数据:LoginResponseDTO-登录响应
     * */
    private LoginResponseDTO buildLoginResponse(String token, SysUser user) {
        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(token);
        response.setTokenType("Bearer");
        response.setExpiresIn(jwtUtil.getExpiration());
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRole(PlatformRole.PLATFORM_ADMIN.equals(user.getPlatformRole())
                ? PlatformRole.PLATFORM_ADMIN : PlatformRole.AUTHENTICATED_USER);
        return response;
    }
}
