package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.CurrentScopeVO;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.service.DataScopeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/*
 * 类功能:CurrentScopeController 单元测试
 * 校验 /auth/current-scope 端点将 DataScopeService 解析的上下文正确映射到响应 VO
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("CurrentScopeController 单元测试")
class CurrentScopeControllerTest {

    @Mock
    private CurrentUserContext currentUserContext;

    @Mock
    private DataScopeService dataScopeService;

    @InjectMocks
    private CurrentScopeController currentScopeController;

    @Test
    @DisplayName("PLATFORM_ADMIN 应返回平台级范围上下文")
    void should_returnPlatformScope_when_platformAdmin() {
        when(currentUserContext.requireCurrentUser())
                .thenReturn(new CurrentUser(1L, "platform_admin", "PLATFORM_ADMIN",
                        Set.of("PLATFORM_ADMIN")));
        when(dataScopeService.resolveDataScope(1L))
                .thenReturn(DataScopeContext.builder()
                        .userId(1L).role("PLATFORM_ADMIN").scopeType("PLATFORM")
                        .organizationIds(new HashSet<>()).cloudRoomIds(new HashSet<>()).build());

        Result<CurrentScopeVO> result = currentScopeController.currentScope();

        assertThat(result.getData().getRole()).isEqualTo("PLATFORM_ADMIN");
        assertThat(result.getData().getScopeType()).isEqualTo("PLATFORM");
        assertThat(result.getData().getOrganizationIds()).isEmpty();
        assertThat(result.getData().isWritePermission()).isTrue();
        assertThat(result.getData().getUsername()).isEqualTo("platform_admin");
    }

    @Test
    @DisplayName("ORG_ADMIN 应返回本组织范围集合与 ORG_ALL scopeType")
    void should_returnOrgScope_when_orgAdmin() {
        when(currentUserContext.requireCurrentUser())
                .thenReturn(new CurrentUser(10L, "org_admin", "ORG_ADMIN", Set.of("ORG_ADMIN")));
        Set<Long> orgIds = new HashSet<>(List.of(1L, 2L));
        when(dataScopeService.resolveDataScope(10L))
                .thenReturn(DataScopeContext.builder()
                        .userId(10L).role("ORG_ADMIN").scopeType("ORG_ALL")
                        .organizationIds(orgIds).cloudRoomIds(new HashSet<>()).build());

        Result<CurrentScopeVO> result = currentScopeController.currentScope();

        assertThat(result.getData().getScopeType()).isEqualTo("ORG_ALL");
        assertThat(result.getData().getOrganizationIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(result.getData().isWritePermission()).isTrue();
    }

    @Test
    @DisplayName("VIEWER READ_ONLY 范围应返回 writePermission=false")
    void should_returnReadOnly_when_viewer() {
        when(currentUserContext.requireCurrentUser())
                .thenReturn(new CurrentUser(30L, "viewer_a", "VIEWER", Set.of("VIEWER")));
        when(dataScopeService.resolveDataScope(30L))
                .thenReturn(DataScopeContext.builder()
                        .userId(30L).role("VIEWER").scopeType("READ_ONLY")
                        .organizationIds(new HashSet<>()).cloudRoomIds(new HashSet<>()).build());

        Result<CurrentScopeVO> result = currentScopeController.currentScope();

        assertThat(result.getData().isWritePermission()).isFalse();
        assertThat(result.getData().getScopeType()).isEqualTo("READ_ONLY");
    }

    @Test
    @DisplayName("OPERATOR ROOMS 范围应返回机房ID集合与 ROOMS scopeType")
    void should_returnRoomsScope_when_operator() {
        when(currentUserContext.requireCurrentUser())
                .thenReturn(new CurrentUser(20L, "operator_a", "OPERATOR", Set.of("OPERATOR")));
        Set<Long> roomIds = new HashSet<>(List.of(5L, 6L));
        when(dataScopeService.resolveDataScope(20L))
                .thenReturn(DataScopeContext.builder()
                        .userId(20L).role("OPERATOR").scopeType("ROOMS")
                        .organizationIds(new HashSet<>(List.of(1L)))
                        .cloudRoomIds(roomIds).build());

        Result<CurrentScopeVO> result = currentScopeController.currentScope();

        assertThat(result.getData().getScopeType()).isEqualTo("ROOMS");
        assertThat(result.getData().getCloudRoomIds()).containsExactlyInAnyOrder(5L, 6L);
        assertThat(result.getData().getOrganizationIds()).contains(1L);
    }
}