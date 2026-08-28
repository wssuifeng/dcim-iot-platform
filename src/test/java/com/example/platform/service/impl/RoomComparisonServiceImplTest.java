package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.RoomComparisonVO;
import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.CloudRoom;
import com.example.platform.entity.Device;
import com.example.platform.mapper.AlarmEventMapper;
import com.example.platform.mapper.DeviceMapper;
import com.example.platform.service.CloudRoomService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/*
 * 类功能:RoomComparisonServiceImpl 单元测试
 * 覆盖：机房数校验（<2 / >4）、机房不存在拒绝、设备过滤聚合、在线率计算
 * */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoomComparisonService 单元测试")
class RoomComparisonServiceImplTest {

    @Mock
    private CloudRoomService cloudRoomService;
    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private AlarmEventMapper alarmEventMapper;

    @InjectMocks
    private RoomComparisonServiceImpl comparisonService;

    private static final Long USER_ID = 60L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 27, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 8, 28, 0, 0);

    @BeforeEach
    void setUp() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant deviceAssistant = new MapperBuilderAssistant(configuration, "");
        deviceAssistant.setCurrentNamespace(DeviceMapper.class.getName());
        TableInfoHelper.initTableInfo(deviceAssistant, Device.class);
        MapperBuilderAssistant alarmAssistant = new MapperBuilderAssistant(configuration, "");
        alarmAssistant.setCurrentNamespace(AlarmEventMapper.class.getName());
        TableInfoHelper.initTableInfo(alarmAssistant, AlarmEvent.class);
        lenient().when(deviceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        lenient().when(alarmEventMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        lenient().when(alarmEventMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
    }

    private CloudRoom room(long id, String name) {
        CloudRoom room = new CloudRoom();
        room.setId(id);
        room.setRoomName(name);
        return room;
    }

    @Test
    @DisplayName("机房数少于 2 拒绝（4001）")
    void should_reject_whenTooFewRooms() {
        assertThatThrownBy(() -> comparisonService.compareRooms(USER_ID, List.of(4L), null, null, START, END))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(4001);
    }

    @Test
    @DisplayName("机房数超过 4 拒绝（4001）")
    void should_reject_whenTooManyRooms() {
        List<Long> ids = List.of(1L, 2L, 3L, 4L, 5L);
        assertThatThrownBy(() -> comparisonService.compareRooms(USER_ID, ids, null, null, START, END))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(4001);
    }

    @Test
    @DisplayName("机房不存在拒绝（4040）")
    void should_reject_whenRoomMissing() {
        lenient().when(cloudRoomService.getCloudRoomOrThrow(4L, USER_ID)).thenReturn(room(4L, "机房A"));
        when(cloudRoomService.getCloudRoomOrThrow(99L, USER_ID))
                .thenThrow(new BusinessException(4040, "机房不存在: 99"));

        assertThatThrownBy(() -> comparisonService.compareRooms(USER_ID, List.of(4L, 99L), null, null, START, END))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(4040);
    }

    @Test
    @DisplayName("两机房对比：设备统计与在线率计算正确")
    void should_aggregateDevices_andOnlineRate() {
        when(cloudRoomService.getCloudRoomOrThrow(4L, USER_ID)).thenReturn(room(4L, "机房A"));
        when(cloudRoomService.getCloudRoomOrThrow(5L, USER_ID)).thenReturn(room(5L, "机房B"));
        when(deviceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(device(1, 1), device(2, 1), device(3, 0)))
                .thenReturn(List.of(device(4, 0)));

        List<RoomComparisonVO> result = comparisonService.compareRooms(
                USER_ID, List.of(4L, 5L), null, null, START, END);

        assertThat(result).hasSize(2);
        RoomComparisonVO a = result.get(0);
        assertThat(a.getRoomName()).isEqualTo("机房A");
        assertThat(a.getDeviceTotal()).isEqualTo(3);
        assertThat(a.getDeviceOnline()).isEqualTo(2);
        assertThat(a.getOnlineRate()).isEqualTo(67);
        RoomComparisonVO b = result.get(1);
        assertThat(b.getDeviceTotal()).isEqualTo(1);
        assertThat(b.getOnlineRate()).isEqualTo(0);
    }

    private Device device(int id, int status) {
        Device d = new Device();
        d.setId(id);
        d.setStatus(status);
        d.setCloudRoomId(4L);
        return d;
    }
}
