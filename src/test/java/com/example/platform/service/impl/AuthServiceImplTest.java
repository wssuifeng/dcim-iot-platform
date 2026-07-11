package com.example.platform.service.impl;

import com.example.platform.common.constant.LoginFailScript;
import com.example.platform.common.exception.AccountLockedException;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.util.JwtUtil;
import com.example.platform.dto.LoginDTO;
import com.example.platform.dto.LoginResponseDTO;
import com.example.platform.entity.SysUser;
import com.example.platform.security.PlatformRole;
import com.example.platform.service.CacheService;
import com.example.platform.service.SysAuditLogService;
import com.example.platform.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/*
 * 类功能:AuthServiceImpl纯单元测试，覆盖登录失败计数、锁定判断、成功重置等核心场景
 *
 * 【测试策略】
 * - @ExtendWith(MockitoExtension.class)，禁止@SpringBootTest
 * - 5个Mock依赖：SysUserService、SysAuditLogService、JwtUtil、PasswordEncoder、CacheService
 * - Lua脚本不真实执行，Mock cacheService.executeLua() 返回 List<Long>
 * - 由于已删除checkIpLocked，每次login失败最多调用1次executeLua
 * - 成功路径不调用executeLua，只调用delete
 * - 停用用户直接拒绝，不消耗失败计数（verify executeLua never）
 *
 * 【LoginFailResult返回值约定】
 * Lua返回 [状态码, 重试次数, 锁定剩余秒数]：
 *   - [0, retryCount, 0]           → 正常失败
 *   - [1, retryCount, lockRemain]  → 已锁定
 *   - [2, retryCount, lockTotal]   → 刚触发锁定
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl 单元测试")
class AuthServiceImplTest {

    @Mock
    private SysUserService sysUserService;

    @Mock
    private SysAuditLogService sysAuditLogService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private AuthServiceImpl authService;

    private LoginDTO loginDTO;
    private SysUser activeUser;
    private SysUser disabledUser;

    @BeforeEach
    void setUp() {
        loginDTO = new LoginDTO();
        loginDTO.setUsername("admin");
        loginDTO.setPassword("Admin123");

        activeUser = new SysUser();
        activeUser.setId(1L);
        activeUser.setUsername("admin");
        activeUser.setPassword("$2a$10$encoded");
        activeUser.setRole("ADMIN");
        activeUser.setStatus(0);
        activeUser.setNickname("管理员");

        disabledUser = new SysUser();
        disabledUser.setId(2L);
        disabledUser.setUsername("disabled");
        disabledUser.setPassword("$2a$10$encoded");
        disabledUser.setRole("VIEWER");
        disabledUser.setStatus(1);
        disabledUser.setNickname("已停用");
    }

    /*
     * 场景11：密码错误，Lua返回正常失败（状态码0），提示剩余尝试次数
     * */
    @Test
    @DisplayName("loginFail - 正常密码错误，提示剩余次数")
    void loginFail_NormalFailure() {
        // Given
        when(sysUserService.findByLoginIdentifier("admin")).thenReturn(activeUser);
        when(passwordEncoder.matches("Admin123", "$2a$10$encoded")).thenReturn(false);
        when(jwtUtil.getFailWindow()).thenReturn(1800L);
        when(jwtUtil.getLockDuration()).thenReturn(1800L);
        when(cacheService.executeLua(eq(LoginFailScript.RECORD_FAIL), eq(List.class), anyList(), anyLong(), anyLong(), anyInt(), anyLong()))
                .thenReturn(List.of(0L, 2L, 0L));

        // When / Then
        assertThatThrownBy(() -> authService.login(loginDTO, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("剩余尝试次数3次");

        verify(sysAuditLogService).log(eq(1L), eq("admin"), eq("LOGIN_FAIL"), eq("127.0.0.1"), eq("密码错误"));
    }

    /*
     * 场景12：密码错误，Lua返回刚触发锁定（状态码2），抛出AccountLockedException
     * */
    @Test
    @DisplayName("loginFail - 刚触发锁定，抛出锁定异常")
    void loginFail_JustTriggeredLock() {
        // Given
        when(sysUserService.findByLoginIdentifier("admin")).thenReturn(activeUser);
        when(passwordEncoder.matches("Admin123", "$2a$10$encoded")).thenReturn(false);
        when(jwtUtil.getFailWindow()).thenReturn(1800L);
        when(jwtUtil.getLockDuration()).thenReturn(1800L);
        when(cacheService.executeLua(eq(LoginFailScript.RECORD_FAIL), eq(List.class), anyList(), anyLong(), anyLong(), anyInt(), anyLong()))
                .thenReturn(List.of(2L, 5L, 1800L));

        // When / Then
        assertThatThrownBy(() -> authService.login(loginDTO, "127.0.0.1"))
                .isInstanceOf(AccountLockedException.class)
                .hasMessageContaining("1800秒");
    }

    /*
     * 场景13：密码错误，Lua返回已锁定（状态码1），抛出AccountLockedException
     * */
    @Test
    @DisplayName("loginFail - 已是锁定状态，抛出锁定异常")
    void loginFail_AlreadyLocked() {
        // Given
        when(sysUserService.findByLoginIdentifier("admin")).thenReturn(activeUser);
        when(passwordEncoder.matches("Admin123", "$2a$10$encoded")).thenReturn(false);
        when(jwtUtil.getFailWindow()).thenReturn(1800L);
        when(jwtUtil.getLockDuration()).thenReturn(1800L);
        when(cacheService.executeLua(eq(LoginFailScript.RECORD_FAIL), eq(List.class), anyList(), anyLong(), anyLong(), anyInt(), anyLong()))
                .thenReturn(List.of(1L, 5L, 999L));

        // When / Then
        assertThatThrownBy(() -> authService.login(loginDTO, "127.0.0.1"))
                .isInstanceOf(AccountLockedException.class)
                .hasMessageContaining("999秒");
    }

    /*
     * 场景14：登录成功，清除失败状态Key、重置DB计数、签发Token
     * */
    @Test
    @DisplayName("loginSuccess - 清除失败计数并签发Token")
    void loginSuccess_ResetCounter() {
        // Given
        when(sysUserService.findByLoginIdentifier("admin")).thenReturn(activeUser);
        when(passwordEncoder.matches("Admin123", "$2a$10$encoded")).thenReturn(true);
        when(jwtUtil.generateToken(1L, "admin", null)).thenReturn("jwt-token");
        when(jwtUtil.getExpiration()).thenReturn(3600L);

        // When
        LoginResponseDTO response = authService.login(loginDTO, "127.0.0.1");

        // Then
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getRole()).isEqualTo(PlatformRole.AUTHENTICATED_USER);

        verify(cacheService).delete("auth:fail:state:127.0.0.1");
        verify(sysUserService).resetLoginFailCount(1L);
        verify(sysAuditLogService).log(1L, "admin", "LOGIN_SUCCESS", "127.0.0.1", "登录成功");
        verify(cacheService, never()).executeLua(anyString(), any(Class.class), anyList(), any());
    }

    /*
     * 场景14.1：旧 ADMIN 只作为兼容账号登录，不再写入组织角色到 JWT
     * */
    @Test
    @DisplayName("loginSuccess - 旧ADMIN不写入组织角色")
    void loginSuccess_LegacyAdminRoleNormalized() {
        // Given
        when(sysUserService.findByLoginIdentifier("admin")).thenReturn(activeUser);
        when(passwordEncoder.matches("Admin123", "$2a$10$encoded")).thenReturn(true);
        when(jwtUtil.generateToken(1L, "admin", null)).thenReturn("jwt-token");
        when(jwtUtil.getExpiration()).thenReturn(3600L);

        // When
        LoginResponseDTO response = authService.login(loginDTO, "127.0.0.1");

        // Then
        assertThat(response.getRole()).isEqualTo(PlatformRole.AUTHENTICATED_USER);
        verify(jwtUtil).generateToken(1L, "admin", null);
    }

    /*
     * 场景14.2：新 PLATFORM_ADMIN 角色可登录且不被降级为组织管理员
     * */
    @Test
    @DisplayName("loginSuccess - PLATFORM_ADMIN角色可登录")
    void loginSuccess_PlatformAdminRoleSupported() {
        // Given
        SysUser platformAdmin = new SysUser();
        platformAdmin.setId(3L);
        platformAdmin.setUsername("platform_admin");
        platformAdmin.setPassword("$2a$10$encoded");
        platformAdmin.setRole(PlatformRole.PLATFORM_ADMIN);
        platformAdmin.setPlatformRole(PlatformRole.PLATFORM_ADMIN);
        platformAdmin.setStatus(0);
        platformAdmin.setNickname("平台管理员");

        LoginDTO platformLogin = new LoginDTO();
        platformLogin.setUsername("platform_admin");
        platformLogin.setPassword("Admin123");

        when(sysUserService.findByLoginIdentifier("platform_admin")).thenReturn(platformAdmin);
        when(passwordEncoder.matches("Admin123", "$2a$10$encoded")).thenReturn(true);
        when(jwtUtil.generateToken(3L, "platform_admin", PlatformRole.PLATFORM_ADMIN)).thenReturn("platform-token");
        when(jwtUtil.getExpiration()).thenReturn(3600L);

        // When
        LoginResponseDTO response = authService.login(platformLogin, "127.0.0.1");

        // Then
        assertThat(response.getToken()).isEqualTo("platform-token");
        assertThat(response.getRole()).isEqualTo(PlatformRole.PLATFORM_ADMIN);
        verify(jwtUtil).generateToken(3L, "platform_admin", PlatformRole.PLATFORM_ADMIN);
    }

    /*
     * 场景15：用户不存在，仍调用Lua记录失败（防用户枚举攻击）
     * 设计理由：用户不存在和密码错误返回相同提示，攻击者无法通过响应差异枚举用户名
     * */
    @Test
    @DisplayName("loginFail - 用户不存在时仍记录失败计数")
    void loginFail_UserNotFound() {
        // Given
        when(sysUserService.findByLoginIdentifier("admin")).thenReturn(null);
        when(jwtUtil.getFailWindow()).thenReturn(1800L);
        when(jwtUtil.getLockDuration()).thenReturn(1800L);
        when(cacheService.executeLua(eq(LoginFailScript.RECORD_FAIL), eq(List.class), anyList(), anyLong(), anyLong(), anyInt(), anyLong()))
                .thenReturn(List.of(0L, 1L, 0L));

        // When / Then
        assertThatThrownBy(() -> authService.login(loginDTO, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("剩余尝试次数4次");

        verify(sysAuditLogService).log(isNull(), isNull(), eq("LOGIN_FAIL"), eq("127.0.0.1"), eq("用户不存在"));
    }

    /*
     * 场景16：账号已停用，直接拒绝，不消耗失败计数
     * 设计理由：停用用户的请求不应消耗Redis失败计数，否则攻击者可利用停用账号消耗IP锁定配额
     * */
    @Test
    @DisplayName("loginFail - 账号停用时不消耗失败计数")
    void loginFail_AccountDisabled() {
        // Given
        LoginDTO disabledLogin = new LoginDTO();
        disabledLogin.setUsername("disabled");
        disabledLogin.setPassword("Admin123");
        when(sysUserService.findByLoginIdentifier("disabled")).thenReturn(disabledUser);

        // When / Then
        assertThatThrownBy(() -> authService.login(disabledLogin, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("停用");

        verify(cacheService, never()).executeLua(anyString(), any(Class.class), anyList(), any());
    }
}
