package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.ResultCode;
import com.example.platform.entity.SysUser;
import com.example.platform.service.SysUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserControllerTest {

    @Mock
    private SysUserService sysUserService;

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
    void pageDelegatesKeywordSearchToService() {
        Page<SysUser> page = new Page<>(1, 10);
        when(sysUserService.pageUsers(1, 10, "operator")).thenReturn(page);

        Result<Page<SysUser>> result = controller.page(1, 10, "operator");

        assertThat(result.getData()).isSameAs(page);
        verify(sysUserService).pageUsers(1, 10, "operator");
    }
}
