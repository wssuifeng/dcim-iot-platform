package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlarmRecipientResolverImplTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @InjectMocks
    private AlarmRecipientResolverImpl resolver;

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @Test
    void shouldReturnOnlyEnabledUsersOfRecipientRoles() {
        // 查询已按 status=0 且角色 IN (ADMIN,OPERATOR,VIEWER) 过滤，返回启用用户
        when(sysUserMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                user(1L, "ADMIN", 0),
                user(2L, "OPERATOR", 0),
                user(3L, "VIEWER", 0)
        ));

        var recipients = resolver.resolveRecipientUserIds();

        assertThat(recipients).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void shouldReturnEmptyWhenNoEnabledUserMatches() {
        when(sysUserMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThat(resolver.resolveRecipientUserIds()).isEmpty();
    }

    private SysUser user(Long id, String role, Integer status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
