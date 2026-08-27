package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.CloudRoomCreateDTO;
import com.example.platform.entity.CloudRoom;
import com.example.platform.mapper.CloudRoomMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * 类功能:CloudRoomServiceImpl 单元测试，单租户收束后机房为顶层资源：
 * roomCode 全局唯一、无组织归属校验
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("CloudRoomService 单元测试")
class CloudRoomServiceImplTest {

    @Mock
    private CloudRoomMapper cloudRoomMapper;

    @Mock
    private com.example.platform.mapper.DeviceMapper deviceMapper;

    @Mock
    private com.example.platform.ingest.service.IngestCredentialService ingestCredentialService;

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

    private CloudRoom buildRoom(Long id, String code, String name, Integer status) {
        CloudRoom room = new CloudRoom();
        room.setId(id);
        room.setRoomCode(code);
        room.setRoomName(name);
        room.setStatus(status);
        return room;
    }

    @Nested
    @DisplayName("createCloudRoom - 创建机房")
    class Create {

        @Test
        @DisplayName("创建机房成功且默认状态为正常")
        void should_create_withDefaultStatus() {
            when(cloudRoomMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(cloudRoomMapper.insert((CloudRoom) any())).thenReturn(1);

            CloudRoomCreateDTO dto = new CloudRoomCreateDTO();
            dto.setRoomName("一号机房");
            dto.setRoomCode("ROOM-001");
            dto.setLocation("上海");
            com.example.platform.ingest.dto.IngestCredentialCreatedVO cred =
                    new com.example.platform.ingest.dto.IngestCredentialCreatedVO();
            cred.setAccessKey("gw-test");
            cred.setSecret("sk-test");
            when(ingestCredentialService.createCredentialForRoom(any(), any()))
                    .thenReturn(cred);

            com.example.platform.dto.CloudRoomCreatedVO saved = cloudRoomService.createCloudRoom(dto, 10L);
            assertThat(saved.getRoomCode()).isEqualTo("ROOM-001");
            verify(cloudRoomMapper, times(1)).insert((CloudRoom) any());
        }

        @Test
        @DisplayName("roomCode 全局重复应抛 BusinessException")
        void should_throw_when_roomCodeDuplicate() {
            when(cloudRoomMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            CloudRoomCreateDTO dto = new CloudRoomCreateDTO();
            dto.setRoomName("冲突机房");
            dto.setRoomCode("ROOM-001");

            assertThatThrownBy(() -> cloudRoomService.createCloudRoom(dto, 10L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("机房编码已存在");
            verify(cloudRoomMapper, never()).insert((CloudRoom) any());
        }
    }

    @Nested
    @DisplayName("getCloudRoomOrThrow - 按ID查询")
    class GetOrThrow {

        @Test
        @DisplayName("不存在应抛 BusinessException")
        void should_throw_when_notExists() {
            when(cloudRoomMapper.selectById(99L)).thenReturn(null);
            assertThatThrownBy(() -> cloudRoomService.getCloudRoomOrThrow(99L, 10L))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("任意已认证用户均可按 ID 访问机房")
        void should_return_when_exists() {
            CloudRoom existing = buildRoom(7L, "ROOM-001", "机房", 0);
            when(cloudRoomMapper.selectById(7L)).thenReturn(existing);

            CloudRoom result = cloudRoomService.getCloudRoomOrThrow(7L, 20L);
            assertThat(result.getId()).isEqualTo(7L);
        }
    }

    @Nested
    @DisplayName("pageCloudRooms - 分页查询")
    class Paging {

        @Test
        @DisplayName("分页查询返回机房列表")
        void should_returnPagedRooms() {
            Page<CloudRoom> mockPage = new Page<>(1, 10);
            mockPage.setRecords(List.of(buildRoom(1L, "ROOM-001", "A", 0)));
            when(cloudRoomMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mockPage);

            Page<CloudRoom> result = cloudRoomService.pageCloudRooms(10L, 1, 10, null, null);
            assertThat(result.getRecords()).hasSize(1);
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
        @DisplayName("停用机房应成功")
        void should_update_status() {
            CloudRoom existing = buildRoom(1L, "R", "X", 0);
            when(cloudRoomMapper.selectById(1L)).thenReturn(existing);
            when(cloudRoomMapper.updateById(any(CloudRoom.class))).thenReturn(1);

            CloudRoom result = cloudRoomService.updateStatus(1L, 1, 10L);
            assertThat(result.getStatus()).isEqualTo(1);
            verify(cloudRoomMapper, times(1)).updateById(any(CloudRoom.class));
        }
    }
}
