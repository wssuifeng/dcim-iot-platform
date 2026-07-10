package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SysUserServiceImpl sysUserService;

    @BeforeEach
    void setUp() throws Exception {
        Field baseMapperField = null;
        Class<?> type = SysUserServiceImpl.class;
        while (type != null && baseMapperField == null) {
            try {
                baseMapperField = type.getDeclaredField("baseMapper");
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        assertThat(baseMapperField).isNotNull();
        baseMapperField.setAccessible(true);
        baseMapperField.set(sysUserService, sysUserMapper);

        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(SysUserMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @Test
    void platformAdminCanPageAllUsers() {
        DataScopeContext scope = platformScope();
        Page<SysUser> expected = pageWithUser(1L, "platform");
        when(sysUserMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(expected);

        Page<SysUser> result = sysUserService.pageUsers(1, 10, null, scope);

        assertThat(result).isSameAs(expected);
        verify(sysUserMapper, never()).selectPageByOrganizationScope(
                any(Page.class), any(), any());
    }

    @Test
    void organizationAdminPagesOnlyUsersAssignedToOwnedOrganizations() {
        Set<Long> organizationIds = Set.of(10L, 20L);
        DataScopeContext scope = orgScope(organizationIds);
        Page<SysUser> expected = pageWithUser(2L, "operator");
        when(sysUserMapper.selectPageByOrganizationScope(
                any(Page.class), eq("operator"), eq(organizationIds)))
                .thenReturn(expected);

        Page<SysUser> result = sysUserService.pageUsers(1, 10, " operator ", scope);

        assertThat(result).isSameAs(expected);
        verify(sysUserMapper).selectPageByOrganizationScope(
                any(Page.class), eq("operator"), eq(organizationIds));
    }

    @Test
    void emptyOrganizationScopeReturnsEmptyPageWithoutQueryingUsers() {
        Page<SysUser> result = sysUserService.pageUsers(2, 20, null, orgScope(Set.of()));

        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getRecords()).isEmpty();
        verify(sysUserMapper, never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        verify(sysUserMapper, never()).selectPageByOrganizationScope(
                any(Page.class), any(), any());
    }

    private DataScopeContext platformScope() {
        return DataScopeContext.builder()
                .userId(1L)
                .role("PLATFORM_ADMIN")
                .scopeType("PLATFORM")
                .organizationIds(Set.of())
                .build();
    }

    private DataScopeContext orgScope(Set<Long> organizationIds) {
        return DataScopeContext.builder()
                .userId(5L)
                .role("ORG_ADMIN")
                .scopeType("ORG_ALL")
                .organizationIds(organizationIds)
                .build();
    }

    private Page<SysUser> pageWithUser(Long id, String username) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        Page<SysUser> page = new Page<>(1, 10);
        page.setRecords(List.of(user));
        page.setTotal(1);
        return page;
    }
}
