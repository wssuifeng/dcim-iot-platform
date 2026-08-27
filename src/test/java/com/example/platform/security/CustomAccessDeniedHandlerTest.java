package com.example.platform.security;

import com.example.platform.service.SysAuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomAccessDeniedHandlerTest {

    @Mock
    private SysAuditLogService sysAuditLogService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void handleRecordsRejectedAuditForAuthenticatedUser() throws Exception {
        CustomAccessDeniedHandler handler = new CustomAccessDeniedHandler(sysAuditLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/devices");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                9L,
                null,
                PlatformRole.authoritiesFor(PlatformRole.VIEWER));
        authentication.setDetails("viewer_user");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        handler.handle(request, response, new AccessDeniedException("Forbidden"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("权限不足");

        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        verify(sysAuditLogService).log(
                eq(9L),
                eq("viewer_user"),
                eq("ACCESS_DENIED"),
                eq("10.0.0.1"),
                detailCaptor.capture());
        assertThat(detailCaptor.getValue())
                .contains("拒绝访问: GET /devices")
                .contains("ROLE_VIEWER")
                .contains("Forbidden");
    }

    @Test
    void handleStillReturnsForbiddenWhenAuditWriteFails() throws Exception {
        CustomAccessDeniedHandler handler = new CustomAccessDeniedHandler(sysAuditLogService);
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/devices/1");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        doThrow(new RuntimeException("db down"))
                .when(sysAuditLogService)
                .log(eq(null), eq(null), eq("ACCESS_DENIED"), eq("127.0.0.1"), org.mockito.ArgumentMatchers.anyString());

        handler.handle(request, response, new AccessDeniedException("Forbidden"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("权限不足");
    }
}
