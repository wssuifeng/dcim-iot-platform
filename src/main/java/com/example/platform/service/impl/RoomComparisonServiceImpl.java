package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.RoomComparisonVO;
import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.CloudRoom;
import com.example.platform.entity.Device;
import com.example.platform.mapper.AlarmEventMapper;
import com.example.platform.mapper.DeviceMapper;
import com.example.platform.service.CloudRoomService;
import com.example.platform.service.RoomComparisonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/*
 * 类功能:机房对比服务实现类——逐机房聚合设备统计与告警统计
 *
 * 【聚合口径】
 *   设备：roomId + typeId + deviceName 模糊；
 *   告警：roomId + 时间范围 [start, end) + （设备过滤非空时）命中设备集；
 *   活动告警数 = state ∈ {TRIGGERED, ACKED, HANDLING}（不受时间范围限制，反映当前态势）。
 * */
@Service
@RequiredArgsConstructor
public class RoomComparisonServiceImpl implements RoomComparisonService {

    private static final int MIN_ROOMS = 2;
    private static final int MAX_ROOMS = 4;
    private static final Set<String> ACTIVE_STATES = Set.of("TRIGGERED", "ACKED", "HANDLING");

    private final CloudRoomService cloudRoomService;
    private final DeviceMapper deviceMapper;
    private final AlarmEventMapper alarmEventMapper;

    @Override
    public List<RoomComparisonVO> compareRooms(Long currentUserId, List<Long> roomIds, Integer typeId,
                                               String deviceName, LocalDateTime start, LocalDateTime end) {
        if (roomIds == null || roomIds.size() < MIN_ROOMS) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "对比至少需要 " + MIN_ROOMS + " 个机房");
        }
        if (roomIds.size() > MAX_ROOMS) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER,
                    "对比机房数上限 " + MAX_ROOMS + " 个");
        }
        if (start == null || end == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "时间范围不能为空");
        }

        List<RoomComparisonVO> result = new ArrayList<>();
        for (Long roomId : roomIds) {
            CloudRoom room = cloudRoomService.getCloudRoomOrThrow(roomId, currentUserId);
            result.add(buildComparison(room, typeId, deviceName, start, end));
        }
        return result;
    }

    private RoomComparisonVO buildComparison(CloudRoom room, Integer typeId, String deviceName,
                                             LocalDateTime start, LocalDateTime end) {
        // 设备过滤：机房 + 类型 + 名称模糊
        LambdaQueryWrapper<Device> deviceWrapper = new LambdaQueryWrapper<>();
        deviceWrapper.eq(Device::getCloudRoomId, room.getId());
        if (typeId != null) {
            deviceWrapper.eq(Device::getTypeId, typeId);
        }
        if (StringUtils.hasText(deviceName)) {
            deviceWrapper.like(Device::getDeviceName, deviceName.trim());
        }
        List<Device> devices = deviceMapper.selectList(deviceWrapper);

        RoomComparisonVO vo = new RoomComparisonVO();
        vo.setRoomId(room.getId());
        vo.setRoomName(room.getRoomName());
        vo.setDeviceTotal((long) devices.size());
        vo.setDeviceOnline(devices.stream().filter(d -> Integer.valueOf(1).equals(d.getStatus())).count());
        vo.setOnlineRate(vo.getDeviceTotal() == 0 ? 0
                : (int) Math.round(vo.getDeviceOnline() * 100.0 / vo.getDeviceTotal()));

        // 告警过滤：机房 + 时间范围 + （有设备过滤时）命中设备集
        LambdaQueryWrapper<AlarmEvent> alarmWrapper = new LambdaQueryWrapper<>();
        alarmWrapper.eq(AlarmEvent::getCloudRoomId, room.getId())
                .ge(AlarmEvent::getTriggeredTime, start)
                .lt(AlarmEvent::getTriggeredTime, end);
        boolean deviceFiltered = typeId != null || StringUtils.hasText(deviceName);
        if (deviceFiltered) {
            Set<Integer> deviceIds = devices.stream().map(Device::getId).collect(Collectors.toSet());
            if (deviceIds.isEmpty()) {
                vo.setActiveAlarms(countActive(room.getId()));
                vo.setAlarmCount(0L);
                vo.setLevelDistribution(new TreeMap<>());
                return vo;
            }
            alarmWrapper.in(AlarmEvent::getDeviceId, deviceIds);
        }
        List<AlarmEvent> events = alarmEventMapper.selectList(alarmWrapper);
        vo.setAlarmCount((long) events.size());
        vo.setLevelDistribution(events.stream()
                .filter(e -> e.getAlarmLevel() != null)
                .collect(Collectors.groupingBy(
                        e -> "L" + e.getAlarmLevel(),
                        TreeMap::new,
                        Collectors.counting())));
        vo.setActiveAlarms(countActive(room.getId()));
        return vo;
    }

    // 活动告警态势不受对比时间范围限制
    private long countActive(Long roomId) {
        LambdaQueryWrapper<AlarmEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmEvent::getCloudRoomId, roomId).in(AlarmEvent::getState, ACTIVE_STATES);
        return alarmEventMapper.selectCount(wrapper);
    }
}
