package com.example.platform.security;

import com.example.platform.common.util.JwtUtil;
import com.example.platform.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationServiceTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private Claims claims;

    @InjectMocks
    private JwtAuthenticationService authenticationService;

    @Test
    void authenticateBuildsSharedAuthenticationWithRoleAuthority() {
        Date issuedAt = new Date();
        when(jwtUtil.parseToken("valid-token")).thenReturn(claims);
        when(claims.getId()).thenReturn("token-id");
        when(claims.getSubject()).thenReturn("42");
        when(claims.getIssuedAt()).thenReturn(issuedAt);
        when(claims.get("username", String.class)).thenReturn("operator");
        when(jwtUtil.getRoleFromToken("valid-token")).thenReturn("OPERATOR");
        when(tokenBlacklistService.isTokenBlacklisted("token-id")).thenReturn(false);
        when(tokenBlacklistService.isUserBlacklistedAndTokenBefore("42", issuedAt))
                .thenReturn(false);

        Authentication authentication = authenticationService.authenticate("valid-token");

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(42L);
        assertThat(authentication.getDetails()).isEqualTo("operator");
        assertThat(authentication.getName()).isEqualTo("42");
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_AUTHENTICATED_USER", "ROLE_OPERATOR");
    }

    @Test
    void authenticateRejectsBlacklistedToken() {
        when(jwtUtil.parseToken("revoked-token")).thenReturn(claims);
        when(claims.getId()).thenReturn("revoked-id");
        when(tokenBlacklistService.isTokenBlacklisted("revoked-id")).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.authenticate("revoked-token"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("失效");
    }

    @Test
    void authenticateRejectsTokenWithoutRole() {
        when(jwtUtil.parseToken("no-role-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("42");
        when(claims.get("username", String.class)).thenReturn("operator");
        when(jwtUtil.getRoleFromToken("no-role-token")).thenReturn(null);

        assertThatThrownBy(() -> authenticationService.authenticate("no-role-token"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("缺少角色信息");
    }
}
