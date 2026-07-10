package com.example.platform.security;

import com.example.platform.dto.DataScopeContext;
import com.example.platform.service.DataScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserContextTest {

    @Mock
    private DataScopeService dataScopeService;

    @InjectMocks
    private CurrentUserContext currentUserContext;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUserReadsHttpSecurityContext() {
        Authentication authentication = authentication(7L, "operator", PlatformRole.OPERATOR);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        CurrentUser currentUser = currentUserContext.requireCurrentUser();

        assertThat(currentUser.userId()).isEqualTo(7L);
        assertThat(currentUser.username()).isEqualTo("operator");
        assertThat(currentUser.role()).isEqualTo(PlatformRole.OPERATOR);
        assertThat(currentUser.roles()).containsExactly(PlatformRole.OPERATOR);
    }

    @Test
    void currentUserReadsWebSocketPrincipal() {
        Authentication authentication = authentication(8L, "viewer", PlatformRole.VIEWER);

        CurrentUser currentUser = currentUserContext.requireCurrentUser(authentication);

        assertThat(currentUser.userId()).isEqualTo(8L);
        assertThat(currentUser.hasRole(PlatformRole.VIEWER)).isTrue();
    }

    @Test
    void requireDataScopeResolvesFromAuthenticatedUserId() {
        Authentication authentication = authentication(9L, "org-admin", PlatformRole.ORG_ADMIN);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        DataScopeContext scope = DataScopeContext.builder()
                .userId(9L)
                .role(PlatformRole.ORG_ADMIN)
                .organizationIds(Set.of(100L))
                .scopeType("ORG_ALL")
                .build();
        when(dataScopeService.resolveDataScope(9L)).thenReturn(scope);

        assertThat(currentUserContext.requireDataScope()).isSameAs(scope);
        verify(dataScopeService).resolveDataScope(9L);
    }

    @Test
    void requireCurrentUserRejectsUnauthenticatedContext() {
        assertThatThrownBy(currentUserContext::requireCurrentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void currentUserRejectsUnknownRoleAuthority() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        10L, null, java.util.List.of(() -> "ROLE_ROOT"));
        authentication.setDetails("root");

        assertThat(currentUserContext.currentUser(authentication)).isEmpty();
    }

    private Authentication authentication(Long userId, String username, String role) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId, null, PlatformRole.authoritiesFor(role));
        authentication.setDetails(username);
        return authentication;
    }
}
