package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.ResultCode;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.SysUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserControllerTest {

    @Mock
    private SysUserService sysUserService;

    @Mock
    private CurrentUserContext currentUserContext;

    @InjectMocks
    private SysUserController controller;

    @Test
    void updateRejectsPasswordAndRequiresDedicatedPasswordEndpoint() {
        SysUser user = new SysUser();
        user.setId(8L);
        user.setRole("VIEWER");
        user.setPassword("Changed123");

        Result<Void> result = controller.update(8L, user);

        assertThat(result.getCode()).isEqualTo(ResultCode.INVALID_PARAMETER);
        assertThat(result.getMessage()).contains("密码");
        verify(sysUserService, never()).updateUser(user);
    }

    @Test
    void pagePassesCurrentOrganizationScopeToService() {
        DataScopeContext scope = DataScopeContext.builder()
                .userId(5L)
                .role("ORG_ADMIN")
                .scopeType("ORG_ALL")
                .organizationIds(Set.of(10L, 20L))
                .build();
        Page<SysUser> page = new Page<>(1, 10);
        when(currentUserContext.requireDataScope()).thenReturn(scope);
        when(sysUserService.pageUsers(1, 10, "operator", scope)).thenReturn(page);

        Result<Page<SysUser>> result = controller.page(1, 10, "operator");

        assertThat(result.getData()).isSameAs(page);
        verify(sysUserService).pageUsers(1, 10, "operator", scope);
    }
}
