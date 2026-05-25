package com.example.platform.service.impl;

import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.AccountLockedException;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.util.JwtUtil;
import com.example.platform.dto.LoginDTO;
import com.example.platform.dto.LoginResponseDTO;
import com.example.platform.entity.SysUser;
import com.example.platform.service.AuthService;
import com.example.platform.service.SysAuditLogService;
import com.example.platform.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/*
 * 类功能:认证服务实现类，实现用户登录认证核心逻辑
 * 包含：账号状态检查、密码验证、失败计数与锁定、JWT签发、审计日志记录
 * */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {
    /** IP地址-登录失败次数映射表，用于记录每个IP的登录失败次数，避免直接查询数据库 */
    private final java.util.concurrent.ConcurrentHashMap<String, Integer> ipFailCount
        = new java.util.concurrent.ConcurrentHashMap<>();
    /** 最大连续登录失败次数（REQ-SEC-FR-001业务规则1：连续5次失败锁定） */
    private static final int MAX_LOGIN_FAIL_COUNT = 3;
    /** 账号锁定时长（分钟） */
    private static final int LOCK_DURATION_MINUTES = 10;
    /** 锁定截止时间格式化模板，用于拼接异常提示消息 */
    private static final DateTimeFormatter LOCK_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysUserService sysUserService;
    private final SysAuditLogService sysAuditLogService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(SysUserService sysUserService,
                           SysAuditLogService sysAuditLogService,
                           JwtUtil jwtUtil,
                           PasswordEncoder passwordEncoder) {
        this.sysUserService = sysUserService;
        this.sysAuditLogService = sysAuditLogService;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    /*
     * 方法功能:用户登录认证
     * 流程:查找用户 → 检查锁定 → 检查停用 → 验证密码 → 签发Token → 记录审计日志
     * 参数:loginDTO-登录请求体, ip-客户端IP
     * 返回数据:LoginResponseDTO-登录成功响应
     * */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public LoginResponseDTO login(LoginDTO loginDTO, String ip) {
        // 第1步：检查IP是否已记录失败次数
        Integer count = ipFailCount.getOrDefault(ip, 0);
        if (count >= 5) {
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS,
                    "登录尝试过于频繁，请1分钟后再试");
        }

        // 第1步：根据用户名查找用户
        SysUser user = sysUserService.findByUsername(loginDTO.getUsername());

        // 用户不存在时记录审计日志并抛出统一错误提示（防止用户名枚举攻击）
        if (user == null) {
            sysAuditLogService.log(null, loginDTO.getUsername(), "LOGIN_FAIL", ip,
                    "用户不存在");
            throw new BusinessException(
                    ResultCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }

        // 第2步：检查账号锁定状态（锁定过期时自动重置计数）
        boolean lockJustExpired = checkAccountLocked(user);

        // 第3步：检查账号是否被停用
        checkAccountDisabled(user);

        // 第4步：验证密码（BCrypt匹配）
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            handleLoginFailure(user, ip);
            
        }

        // 第5步：登录成功，重置失败计数（锁定刚过期时已在checkAccountLocked中重置，避免冗余写入）
        if (!lockJustExpired) {
            sysUserService.resetLoginFailCount(user.getId());
        }

        // 第6步：签发JWT Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        // 第7步：记录登录成功审计日志
        sysAuditLogService.log(user.getId(), user.getUsername(), "LOGIN_SUCCESS", ip,
                "登录成功");
        
        ipFailCount.remove(ip);

        return buildLoginResponse(token, user);
    }

    /*
     * 方法功能:检查账号是否处于锁定状态，锁定中则抛出AccountLockedException
     * 参数:user-用户对象
     * 返回数据:boolean-锁定刚过期返回true（已自动重置计数），否则返回false
     * */
    private boolean checkAccountLocked(SysUser user) {
        if (user.getLockTime() != null && user.getLockTime().isAfter(LocalDateTime.now())) {
            log.warn("账号已锁定: userId={}, lockTime={}", user.getId(), user.getLockTime());
            throw new AccountLockedException(user.getLockTime().format(LOCK_TIME_FORMATTER));
        }

        if (user.getLockTime() != null && !user.getLockTime().isAfter(LocalDateTime.now())) {
            sysUserService.resetLoginFailCount(user.getId());
            return true;
        }
        return false;
    }

    /*
     * 方法功能:检查账号是否被停用
     * 参数:user-用户对象
     * 返回数据:无
     * */
    private void checkAccountDisabled(SysUser user) {
        if (user.getStatus() != null && user.getStatus() == 1) {
            log.warn("账号已停用: userId={}", user.getId());
            throw new BusinessException(
                    ResultCode.ACCOUNT_DISABLED, "账号已被停用，请联系管理员");
        }
    }

    /*
     * 方法功能:处理登录失败，递增失败计数并在达到阈值时锁定账号
     * 参数:user-用户对象, ip-客户端IP
     * 返回数据:无（抛出统一错误提示）
     * */
    private void handleLoginFailure(SysUser user, String ip) {
        int failCount = sysUserService.incrementLoginFailCount(user.getId());

        String detail = failCount >= MAX_LOGIN_FAIL_COUNT
                ? "密码错误" + failCount + "次，账号已锁定" + LOCK_DURATION_MINUTES + "分钟"
                : "密码错误" + failCount + "次";

        sysAuditLogService.log(user.getId(), user.getUsername(), "LOGIN_FAIL", ip, detail);
        ipFailCount.merge(ip, 1, Integer::sum);  // ip对应的值+1，不存在则设为1
        log.warn("登录失败: userId={}, failCount={}", user.getId(), failCount);

        throw new BusinessException(
                ResultCode.INVALID_CREDENTIALS, "用户名或密码错误");
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
        response.setRole(user.getRole());
        return response;
    }
}