package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    void pageUsersDelegatesToMapperWithKeyword() {
        Page<SysUser> expected = pageWithUser(1L, "admin");
        when(sysUserMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(expected);

        Page<SysUser> result = sysUserService.pageUsers(1, 10, "admin");

        assertThat(result).isSameAs(expected);
        verify(sysUserMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void pageUsersWithoutKeywordReturnsAllUsers() {
        Page<SysUser> expected = pageWithUser(2L, "operator");
        when(sysUserMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(expected);

        Page<SysUser> result = sysUserService.pageUsers(1, 10, null);

        assertThat(result).isSameAs(expected);
        assertThat(result.getRecords()).hasSize(1);
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
