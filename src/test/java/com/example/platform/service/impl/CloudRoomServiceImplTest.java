package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.dto.CloudRoomCreateDTO;
import com.example.platform.dto.CloudRoomUpdateDTO;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.CloudRoom;
import com.example.platform.entity.Organization;
import com.example.platform.mapper.CloudRoomMapper;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.OrganizationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
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
 * 类功能:CloudRoomServiceImpl 单元测试，覆盖组织归属校验、范围过滤、创建/更新权限
 * 覆盖任务卡 BIZ-OPEN-01：运维和查看者只能访问授权范围；组织管理员不能跨组织
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("CloudRoomService 单元测试")
class CloudRoomServiceImplTest {

    @Mock
    private CloudRoomMapper cloudRoomMapper;

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private OrganizationService organizationService;

    @InjectMocks
    private CloudRoomServiceImpl cloudRoomService;

    @BeforeEach
    void setUp() throws Exception {
        initBaseMapper();
        initLambdaCache();
    }

    private void initBaseMapper() throws Exception {
        Class<?> clazz = CloudRoomServiceImpl.class;
        Field baseMapperField = null;
        while (clazz != null) {
            try {
                baseMapperField = clazz.getDeclaredField("baseMapper");
                break;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        assertThat(baseMapperField).as("应在继承链中找到baseMapper字段").isNotNull();
        baseMapperField.setAccessible(true);
        baseMapperField.set(cloudRoomService, cloudRoomMapper);
    }

    private void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(CloudRoomMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, CloudRoom.class);
    }

    private DataScopeContext orgAdminContext(Set<Long> orgIds) {
        return DataScopeContext.builder()
                .userId(10L).role("ORG_ADMIN").scopeType("ORG_ALL")
                .organizationIds(orgIds).cloudRoomIds(new HashSet<>()).build();
    }

    private DataScopeContext operatorContext(Set<Long> orgIds, Set<Long> roomIds) {
        return DataScopeContext.builder()
                .userId(20L).role("OPERATOR").scopeType("ROOMS")
                .organizationIds(orgIds).cloudRoomIds(roomIds).build();
    }

    private DataScopeContext platformAdminContext() {
        return DataScopeContext.builder()
                .userId(1L).role("PLATFORM_ADMIN").scopeType("PLATFORM")
                .organizationIds(new HashSet<>()).cloudRoomIds(new HashSet<>()).build();
    }

    private CloudRoom buildRoom(Long id, Long orgId, String code, String name, Integer status) {
        CloudRoom room = new CloudRoom();
        room.setId(id);
        room.setOrganizationId(orgId);
        room.setRoomCode(code);
        room.setRoomName(name);
        room.setStatus(status);
        return room;
    }

    private Organization buildOrg(Long id) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setOrgCode("ORG-" + id);
        organization.setOrgName("组织" + id);
        organization.setStatus(0);
        return organization;
    }

    @Nested
    @DisplayName("createCloudRoom - 创建机房")
    class Create {

        @Test
        @DisplayName("ORG_ADMIN 在自身组织内创建机房应成功")
        void should_create_when_orgAdminInScope() {
            Set<Long> ownedOrgs = new HashSet<>();
            ownedOrgs.add(1L);
            when(dataScopeService.resolveDataScope(10L)).thenReturn(orgAdminContext(ownedOrgs));
            when(organizationService.getById(1L)).thenReturn(buildOrg(1L));
            when(cloudRoomMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(cloudRoomMapper.insert((CloudRoom) any())).thenReturn(1);

            CloudRoomCreateDTO dto = new CloudRoomCreateDTO();
            dto.setOrganizationId(1L);
            dto.setRoomName("一号机房");
            dto.setRoomCode("ROOM-001");
            dto.setLocation("上海");

            CloudRoom saved = cloudRoomService.createCloudRoom(dto, 10L);
            assertThat(saved.getStatus()).isEqualTo(0);
            assertThat(saved.getRoomCode()).isEqualTo("ROOM-001");
            verify(cloudRoomMapper, times(1)).insert((CloudRoom) any());
        }

        @Test
        @DisplayName("ORG_ADMIN 跨组织创建应抛 DataScopeViolationException")
        void should_throw_when_orgAdminCrossOrg() {
            Set<Long> ownedOrgs = new HashSet<>();
            ownedOrgs.add(1L);
            when(dataScopeService.resolveDataScope(10L)).thenReturn(orgAdminContext(ownedOrgs));

            CloudRoomCreateDTO dto = new CloudRoomCreateDTO();
            dto.setOrganizationId(2L); // 不在范围内
            dto.setRoomName("二号机房");
            dto.setRoomCode("ROOM-2");

            assertThatThrownBy(() -> cloudRoomService.createCloudRoom(dto, 10L))
                    .isInstanceOf(DataScopeViolationException.class);
            verify(cloudRoomMapper, never()).insert((CloudRoom) any());
        }

        @Test
        @DisplayName("OPERATOR 无写权限应抛 DataScopeViolationException")
        void should_throw_when_operatorNoWrite() {
            when(dataScopeService.resolveDataScope(20L))
                    .thenReturn(operatorContext(new HashSet<>(List.of(1L)), new HashSet<>()));

            CloudRoomCreateDTO dto = new CloudRoomCreateDTO();
            dto.setOrganizationId(1L);
            dto.setRoomName("X");
            dto.setRoomCode("ROOM-X");

            assertThatThrownBy(() -> cloudRoomService.createCloudRoom(dto, 20L))
                    .isInstanceOf(DataScopeViolationException.class);
            verify(cloudRoomMapper, never()).insert((CloudRoom) any());
        }

        @Test
        @DisplayName("组织内 roomCode 重复应抛 BusinessException")
        void should_throw_when_roomCodeDuplicate() {
            Set<Long> ownedOrgs = new HashSet<>();
            ownedOrgs.add(1L);
            when(dataScopeService.resolveDataScope(10L)).thenReturn(orgAdminContext(ownedOrgs));
            when(organizationService.getById(1L)).thenReturn(buildOrg(1L));
            when(cloudRoomMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            CloudRoomCreateDTO dto = new CloudRoomCreateDTO();
            dto.setOrganizationId(1L);
            dto.setRoomName("冲突机房");
            dto.setRoomCode("ROOM-001");

            assertThatThrownBy(() -> cloudRoomService.createCloudRoom(dto, 10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("机房编码在组织内已存在");
            verify(cloudRoomMapper, never()).insert((CloudRoom) any());
        }

        @Test
        @DisplayName("归属组织不存在时抛 BusinessException")
        void should_throw_when_orgNotFound() {
            Set<Long> ownedOrgs = new HashSet<>();
            ownedOrgs.add(1L);
            when(dataScopeService.resolveDataScope(10L)).thenReturn(orgAdminContext(ownedOrgs));
            when(organizationService.getById(1L)).thenReturn(null);

            CloudRoomCreateDTO dto = new CloudRoomCreateDTO();
            dto.setOrganizationId(1L);
            dto.setRoomName("X");
            dto.setRoomCode("ROOM-X");

            assertThatThrownBy(() -> cloudRoomService.createCloudRoom(dto, 10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("归属组织不存在");
        }
    }

    @Nested
    @DisplayName("getCloudRoomOrThrow - 按ID查询带范围校验")
    class GetOrThrow {

        @Test
        @DisplayName("不存在应抛 BusinessException")
        void should_throw_when_notExists() {
            when(cloudRoomMapper.selectById(99L)).thenReturn(null);
            assertThatThrownBy(() -> cloudRoomService.getCloudRoomOrThrow(99L, 10L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("OPERATOR 访问未授权机房应抛 DataScopeViolationException")
        void should_throw_when_operatorOutOfRoomScope() {
            CloudRoom existing = buildRoom(5L, 1L, "ROOM-001", "机房", 0);
            when(cloudRoomMapper.selectById(5L)).thenReturn(existing);
            when(dataScopeService.resolveDataScope(20L))
                    .thenReturn(operatorContext(new HashSet<>(List.of(1L)), new HashSet<>(List.of(4L))));

            assertThatThrownBy(() -> cloudRoomService.getCloudRoomOrThrow(5L, 20L))
                    .isInstanceOf(DataScopeViolationException.class);
        }

        @Test
        @DisplayName("OPERATOR 访问授权机房应成功（ROOMS 范围优先匹配机房ID）")
        void should_return_when_operatorInRoomScope() {
            CloudRoom existing = buildRoom(4L, 1L, "ROOM-001", "机房", 0);
            when(cloudRoomMapper.selectById(4L)).thenReturn(existing);
            when(dataScopeService.resolveDataScope(20L))
                    .thenReturn(operatorContext(new HashSet<>(List.of(1L)), new HashSet<>(List.of(4L))));

            CloudRoom result = cloudRoomService.getCloudRoomOrThrow(4L, 20L);
            assertThat(result.getId()).isEqualTo(4L);
        }

        @Test
        @DisplayName("PLATFORM_ADMIN 仍可按 ID 访问任意机房用于平台运维")
        void should_return_when_platformAdminAnyRoom() {
            CloudRoom existing = buildRoom(7L, 5L, "ROOM", "X", 0);
            when(cloudRoomMapper.selectById(7L)).thenReturn(existing);
            when(dataScopeService.resolveDataScope(1L)).thenReturn(platformAdminContext());

            CloudRoom result = cloudRoomService.getCloudRoomOrThrow(7L, 1L);
            assertThat(result.getId()).isEqualTo(7L);
        }
    }

    @Nested
    @DisplayName("pageCloudRooms - 范围过滤")
    class Paging {

        @Test
        @DisplayName("PLATFORM_ADMIN 分页查询返回空页（不穿透业务）")
        void should_returnEmpty_when_platformAdmin() {
            when(dataScopeService.resolveDataScope(1L)).thenReturn(platformAdminContext());
            Page<CloudRoom> result = cloudRoomService.pageCloudRooms(1L, 1, 10, null, null, null);
            assertThat(result.getRecords()).isEmpty();
            verify(cloudRoomMapper, never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("ORG_ADMIN 分页查询本组织机房")
        void should_filterByOrg_when_orgAdmin() {
            Set<Long> ownedOrgs = new HashSet<>();
            ownedOrgs.add(1L);
            when(dataScopeService.resolveDataScope(10L)).thenReturn(orgAdminContext(ownedOrgs));
            Page<CloudRoom> mockPage = new Page<>(1, 10);
            mockPage.setRecords(List.of(buildRoom(1L, 1L, "ROOM-001", "A", 0)));
            when(cloudRoomMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            Page<CloudRoom> result = cloudRoomService.pageCloudRooms(10L, 1, 10, null, null, null);
            assertThat(result.getRecords()).hasSize(1);
        }

        @Test
        @DisplayName("ORG_ADMIN 无组织授权时返回空页且不调用 mapper")
        void should_returnEmpty_when_orgAdminNoOrg() {
            when(dataScopeService.resolveDataScope(10L)).thenReturn(orgAdminContext(new HashSet<>()));
            Page<CloudRoom> result = cloudRoomService.pageCloudRooms(10L, 1, 10, null, null, null);
            assertThat(result.getRecords()).isEmpty();
            verify(cloudRoomMapper, never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("ORG_ADMIN 请求跨组织 organizationId 时拒绝返回空（不抛异常，安静拒绝）")
        void should_returnEmpty_when_orgAdminCrossOrgFilter() {
            Set<Long> ownedOrgs = new HashSet<>();
            ownedOrgs.add(1L);
            when(dataScopeService.resolveDataScope(10L)).thenReturn(orgAdminContext(ownedOrgs));

            Page<CloudRoom> result = cloudRoomService.pageCloudRooms(10L, 1, 10, 99L, null, null);
            assertThat(result.getRecords()).isEmpty();
            verify(cloudRoomMapper, never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("OPERATOR 按授权机房过滤 + roomId 二次过滤生效")
        void should_filterByRoom_when_operatorWithRooms() {
            Set<Long> orgIds = new HashSet<>(List.of(1L));
            Set<Long> roomIds = new HashSet<>();
            roomIds.add(1L);
            roomIds.add(2L);
            when(dataScopeService.resolveDataScope(20L)).thenReturn(operatorContext(orgIds, roomIds));
            Page<CloudRoom> mockPage = new Page<>(1, 10);
            mockPage.setRecords(List.of(buildRoom(1L, 1L, "R1", "A", 0), buildRoom(2L, 1L, "R2", "B", 0)));
            when(cloudRoomMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            Page<CloudRoom> result = cloudRoomService.pageCloudRooms(20L, 1, 10, null, null, null);
            assertThat(result.getRecords()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("updateStatus - 启用/停用机房")
    class UpdateStatus {

        @Test
        @DisplayName("非法状态应抛 BusinessException")
        void should_throw_invalidStatus() {
            assertThatThrownBy(() -> cloudRoomService.updateStatus(1L, -1, 10L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("OPERATOR 无权修改机房状态应抛 DataScopeViolationException")
        void should_throw_when_operator() {
            when(dataScopeService.resolveDataScope(20L))
                    .thenReturn(operatorContext(new HashSet<>(List.of(1L)), new HashSet<>(List.of(4L))));
            CloudRoom existing = buildRoom(4L, 1L, "R", "X", 0);
            when(cloudRoomMapper.selectById(4L)).thenReturn(existing);

            assertThatThrownBy(() -> cloudRoomService.updateStatus(4L, 1, 20L))
                    .isInstanceOf(DataScopeViolationException.class);
        }

        @Test
        @DisplayName("ORG_ADMIN 在自身组织内停用机房应成功")
        void should_update_when_orgAdminInScope() {
            Set<Long> ownedOrgs = new HashSet<>();
            ownedOrgs.add(1L);
            when(dataScopeService.resolveDataScope(10L)).thenReturn(orgAdminContext(ownedOrgs));
            CloudRoom existing = buildRoom(1L, 1L, "R", "X", 0);
            when(cloudRoomMapper.selectById(1L)).thenReturn(existing);
            when(cloudRoomMapper.updateById(any(CloudRoom.class))).thenReturn(1);

            CloudRoom result = cloudRoomService.updateStatus(1L, 1, 10L);
            assertThat(result.getStatus()).isEqualTo(1);
            verify(cloudRoomMapper, times(1)).updateById(any(CloudRoom.class));
        }
    }
}