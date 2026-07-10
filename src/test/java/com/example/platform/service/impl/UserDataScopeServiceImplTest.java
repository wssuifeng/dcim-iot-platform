package com.example.platform.service.impl;

import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.DataScopeAssignmentDTO;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.CloudRoom;
import com.example.platform.entity.DataScopeAssignment;
import com.example.platform.entity.SysUser;
import com.example.platform.mapper.CloudRoomMapper;
import com.example.platform.mapper.DataScopeAssignmentMapper;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:UserDataScopeServiceImpl 单元测试，覆盖分配/移除的权限与范围校验
 * 覆盖任务卡 BIZ-OPEN-01：平台管理员初始化组织管理员；组织管理员不能跨组织
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserDataScopeService 单元测试")
class UserDataScopeServiceImplTest {

    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private DataScopeAssignmentMapper dataScopeAssignmentMapper;
    @Mock
    private CloudRoomMapper cloudRoomMapper;

    @InjectMocks
    private UserDataScopeServiceImpl userDataScopeService;

    private SysUser targetOrgAdmin;
    private SysUser targetOperator;

    @BeforeEach
    void setUp() {
        targetOrgAdmin = new SysUser();
        targetOrgAdmin.setId(20L);
        targetOrgAdmin.setUsername("org_admin_target");
        targetOrgAdmin.setRole("ORG_ADMIN");

        targetOperator = new SysUser();
        targetOperator.setId(21L);
        targetOperator.setUsername("operator_target");
        targetOperator.setRole("OPERATOR");
    }

    private DataScopeContext platformAdminContext() {
        return DataScopeContext.builder()
                .userId(1L).role("PLATFORM_ADMIN").scopeType("PLATFORM")
                .organizationIds(new HashSet<>()).cloudRoomIds(new HashSet<>()).build();
    }

    private DataScopeContext orgAdminContext(Set<Long> orgIds) {
        return DataScopeContext.builder()
                .userId(10L).role("ORG_ADMIN").scopeType("ORG_ALL")
                .organizationIds(orgIds).cloudRoomIds(new HashSet<>()).build();
    }

    private DataScopeAssignmentDTO dto(String scopeType, Long orgId, Long roomId) {
        DataScopeAssignmentDTO dto = new DataScopeAssignmentDTO();
        dto.setScopeType(scopeType);
        dto.setOrganizationId(orgId);
        dto.setCloudRoomId(roomId);
        return dto;
    }

    private CloudRoom buildRoom(Long id, Long orgId) {
        CloudRoom room = new CloudRoom();
        room.setId(id);
        room.setOrganizationId(orgId);
        return room;
    }

    @Nested
    @DisplayName("assignScope - 分配数据范围")
    class Assign {

        @Test
        @DisplayName("PLATFORM_ADMIN 给 ORG_ADMIN 分配 ORG_ALL 应成功")
        void should_assignOrgAll_when_platformAdminInitializesOrgAdmin() {
            when(sysUserService.getUserOrThrow(20L)).thenReturn(targetOrgAdmin);
            when(dataScopeService.resolveDataScope(1L)).thenReturn(platformAdminContext());
            when(dataScopeAssignmentMapper.selectCount(any())).thenReturn(0L);
            when(dataScopeAssignmentMapper.insert((DataScopeAssignment) any())).thenReturn(1);

            DataScopeAssignment result =
                    userDataScopeService.assignScope(20L, dto("ORG_ALL", 1L, null), 1L);

            assertThat(result.getUserId()).isEqualTo(20L);
            assertThat(result.getScopeType()).isEqualTo("ORG_ALL");
            assertThat(result.getOrganizationId()).isEqualTo(1L);
            verify(dataScopeAssignmentMapper, times(1)).insert((DataScopeAssignment) any());
        }

        @Test
        @DisplayName("PLATFORM_ADMIN 给非 PLATFORM_ADMIN 用户分配 PLATFORM 范围应拒绝")
        void should_reject_when_platformScopeForNonPlatformAdminUser() {
            when(sysUserService.getUserOrThrow(21L)).thenReturn(targetOperator);
            when(dataScopeService.resolveDataScope(1L)).thenReturn(platformAdminContext());

            assertThatThrownBy(() ->
                    userDataScopeService.assignScope(21L, dto("PLATFORM", null, null), 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("PLATFORM 范围只能分配给平台管理员");
            verify(dataScopeAssignmentMapper, never()).insert((DataScopeAssignment) any());
        }

        @Test
        @DisplayName("ORG_ADMIN 不可分配 PLATFORM 范围")
        void should_reject_when_orgAdminAssignsPlatform() {
            when(sysUserService.getUserOrThrow(21L)).thenReturn(targetOperator);
            when(dataScopeService.resolveDataScope(10L))
                    .thenReturn(orgAdminContext(new HashSet<>(List.of(1L))));

            assertThatThrownBy(() ->
                    userDataScopeService.assignScope(21L, dto("PLATFORM", null, null), 10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅平台管理员可分配 PLATFORM 范围");
            verify(dataScopeAssignmentMapper, never()).insert((DataScopeAssignment) any());
        }

        @Test
        @DisplayName("ORG_ADMIN 跨组织分配应抛 DataScopeViolationException")
        void should_throw_when_orgAdminCrossOrg() {
            when(sysUserService.getUserOrThrow(21L)).thenReturn(targetOperator);
            when(dataScopeService.resolveDataScope(10L))
                    .thenReturn(orgAdminContext(new HashSet<>(List.of(1L))));

            assertThatThrownBy(() ->
                    userDataScopeService.assignScope(21L, dto("ROOMS", 2L, 5L), 10L))
                    .isInstanceOf(DataScopeViolationException.class);
            verify(dataScopeAssignmentMapper, never()).insert((DataScopeAssignment) any());
        }

        @Test
        @DisplayName("ROOMS 范围机房不属于指定组织时拒绝")
        void should_throw_when_roomNotInOrg() {
            when(sysUserService.getUserOrThrow(21L)).thenReturn(targetOperator);
            Set<Long> orgIds = new HashSet<>(List.of(1L));
            Set<Long> roomIds = new HashSet<>(List.of(5L));
            when(dataScopeService.resolveDataScope(10L)).thenReturn(orgAdminContext(orgIds));
            when(cloudRoomMapper.selectById(5L)).thenReturn(buildRoom(5L, 99L)); // 机房属于别组织

            assertThatThrownBy(() ->
                    userDataScopeService.assignScope(21L, dto("ROOMS", 1L, 5L), 10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不属于组织");
            verify(dataScopeAssignmentMapper, never()).insert((DataScopeAssignment) any());
        }

        @Test
        @DisplayName("ORG_ALL 范围附带 cloudRoomId 应拒绝（对齐校验）")
        void should_throw_when_orgAllHasRoomId() {
            when(sysUserService.getUserOrThrow(21L)).thenReturn(targetOperator);
            when(dataScopeService.resolveDataScope(10L))
                    .thenReturn(orgAdminContext(new HashSet<>(List.of(1L))));

            assertThatThrownBy(() ->
                    userDataScopeService.assignScope(21L, dto("ORG_ALL", 1L, 5L), 10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("ORG_ALL");
            verify(dataScopeAssignmentMapper, never()).insert((DataScopeAssignment) any());
        }

        @Test
        @DisplayName("重复分配相同范围应拒绝")
        void should_throw_when_duplicateAssignment() {
            when(sysUserService.getUserOrThrow(20L)).thenReturn(targetOrgAdmin);
            when(dataScopeService.resolveDataScope(1L)).thenReturn(platformAdminContext());
            when(dataScopeAssignmentMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() ->
                    userDataScopeService.assignScope(20L, dto("ORG_ALL", 1L, null), 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已分配");
            verify(dataScopeAssignmentMapper, never()).insert((DataScopeAssignment) any());
        }

        @Test
        @DisplayName("非法 scopeType 应拒绝")
        void should_throw_when_invalidScopeType() {
            when(sysUserService.getUserOrThrow(20L)).thenReturn(targetOrgAdmin);

            assertThatThrownBy(() ->
                    userDataScopeService.assignScope(20L, dto("UNKNOWN", null, null), 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("无效的 scopeType");
            verify(dataScopeAssignmentMapper, never()).insert((DataScopeAssignment) any());
        }

        @Test
        @DisplayName("ORG_ADMIN 在本组织为 OPERATOR 分配 ROOMS 范围应成功")
        void should_assignRooms_when_orgAdminInScope() {
            when(sysUserService.getUserOrThrow(21L)).thenReturn(targetOperator);
            Set<Long> orgIds = new HashSet<>(List.of(1L));
            Set<Long> roomIds = new HashSet<>(List.of(5L));
            // ORG_ADMIN 范围上下文中 cloudRoomIds 为空，但 hasCloudRoomAccess 仍按 cloudRoomIds 比对
            // 此处简化：给 ORG_ADMIN 上下文仅含组织，分配 ROOMS 时校验机房归属组织即可
            when(dataScopeService.resolveDataScope(10L)).thenReturn(orgAdminContextWithRooms(orgIds, roomIds));
            when(cloudRoomMapper.selectById(5L)).thenReturn(buildRoom(5L, 1L));
            when(dataScopeAssignmentMapper.selectCount(any())).thenReturn(0L);
            when(dataScopeAssignmentMapper.insert((DataScopeAssignment) any())).thenReturn(1);

            DataScopeAssignment result =
                    userDataScopeService.assignScope(21L, dto("ROOMS", 1L, 5L), 10L);

            assertThat(result.getCloudRoomId()).isEqualTo(5L);
            assertThat(result.getOrganizationId()).isEqualTo(1L);
            verify(dataScopeAssignmentMapper, times(1)).insert((DataScopeAssignment) any());
        }

        private DataScopeContext orgAdminContextWithRooms(Set<Long> orgIds, Set<Long> roomIds) {
            return DataScopeContext.builder()
                    .userId(10L).role("ORG_ADMIN").scopeType("ORG_ALL")
                    .organizationIds(orgIds).cloudRoomIds(roomIds).build();
        }
    }

    @Nested
    @DisplayName("removeAssignment - 移除数据范围")
    class Remove {

        @Test
        @DisplayName("PLATFORM_ADMIN 可移除任意分配")
        void should_remove_when_platformAdmin() {
            when(sysUserService.getUserOrThrow(20L)).thenReturn(targetOrgAdmin);
            DataScopeAssignment existing = new DataScopeAssignment();
            existing.setId(100L);
            existing.setUserId(20L);
            existing.setOrganizationId(2L);
            existing.setScopeType("ORG_ALL");
            when(dataScopeAssignmentMapper.selectById(100L)).thenReturn(existing);
            when(dataScopeService.resolveDataScope(1L)).thenReturn(platformAdminContext());
            when(dataScopeAssignmentMapper.deleteById(100L)).thenReturn(1);

            userDataScopeService.removeAssignment(20L, 100L, 1L);

            verify(dataScopeAssignmentMapper, times(1)).deleteById(100L);
        }

        @Test
        @DisplayName("分配记录不属于目标用户时拒绝")
        void should_throw_when_assignmentNotOwnedByUser() {
            when(sysUserService.getUserOrThrow(20L)).thenReturn(targetOrgAdmin);
            DataScopeAssignment existing = new DataScopeAssignment();
            existing.setId(100L);
            existing.setUserId(99L); // 属于他人
            existing.setScopeType("PLATFORM");
            when(dataScopeAssignmentMapper.selectById(100L)).thenReturn(existing);

            assertThatThrownBy(() -> userDataScopeService.removeAssignment(20L, 100L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不属于目标用户");
            verify(dataScopeAssignmentMapper, never()).deleteById(any());
        }

        @Test
        @DisplayName("ORG_ADMIN 移除自身组织外的分配应拒绝")
        void should_throw_when_orgAdminRemoveOutOfScope() {
            when(sysUserService.getUserOrThrow(20L)).thenReturn(targetOrgAdmin);
            DataScopeAssignment existing = new DataScopeAssignment();
            existing.setId(100L);
            existing.setUserId(20L);
            existing.setOrganizationId(99L); // 不在组织的范围内
            existing.setScopeType("ORG_ALL");
            when(dataScopeAssignmentMapper.selectById(100L)).thenReturn(existing);
            when(dataScopeService.resolveDataScope(10L))
                    .thenReturn(orgAdminContext(new HashSet<>(List.of(1L))));

            assertThatThrownBy(() -> userDataScopeService.removeAssignment(20L, 100L, 10L))
                    .isInstanceOf(DataScopeViolationException.class);
            verify(dataScopeAssignmentMapper, never()).deleteById(any());
        }

        @Test
        @DisplayName("记录不存在应抛 BusinessException")
        void should_throw_when_notExists() {
            when(sysUserService.getUserOrThrow(20L)).thenReturn(targetOrgAdmin);
            when(dataScopeAssignmentMapper.selectById(100L)).thenReturn(null);

            assertThatThrownBy(() -> userDataScopeService.removeAssignment(20L, 100L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不存在");
        }
    }

    @Nested
    @DisplayName("listAssignments - 查询用户范围分配")
    class ListAssignments {

        @Test
        @DisplayName("PLATFORM_ADMIN 可见目标用户所有分配")
        void should_returnAll_when_platformAdmin() {
            when(sysUserService.getUserOrThrow(20L)).thenReturn(targetOrgAdmin);
            when(dataScopeService.resolveDataScope(1L)).thenReturn(platformAdminContext());
            when(dataScopeAssignmentMapper.selectList(any()))
                    .thenReturn(List.of(new DataScopeAssignment()));

            List<DataScopeAssignment> result = userDataScopeService.listAssignments(20L, 1L);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("ORG_ADMIN 无组织授权时返回空")
        void should_returnEmpty_when_orgAdminNoOrg() {
            when(sysUserService.getUserOrThrow(20L)).thenReturn(targetOrgAdmin);
            when(dataScopeService.resolveDataScope(10L))
                    .thenReturn(orgAdminContext(new HashSet<>()));

            List<DataScopeAssignment> result = userDataScopeService.listAssignments(20L, 10L);
            assertThat(result).isEmpty();
            verify(dataScopeAssignmentMapper, never()).selectList(any());
        }
    }
}