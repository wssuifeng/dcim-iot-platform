package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.ResultCode;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.AlarmNotificationLogVO;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.AlarmNotificationLog;
import com.example.platform.mapper.AlarmNotificationLogMapper;
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.AlarmNotificationLogService;
import com.example.platform.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/*
 * 类功能:告警发送日志服务实现类，按数据范围过滤的分页查询
 * */
@Service
@RequiredArgsConstructor
public class AlarmNotificationLogServiceImpl
        extends ServiceImpl<AlarmNotificationLogMapper, AlarmNotificationLog>
        implements AlarmNotificationLogService {

    private final DataScopeService dataScopeService;
    private final AlarmEventService alarmEventService;

    @Override
    public Page<AlarmNotificationLogVO> pageLogs(Long currentUserId, Integer current, Integer size,
                                                 Long alarmEventId, String channel, Integer status) {
        int[] params = PaginationUtils.validate(current, size);
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);

        LambdaQueryWrapper<AlarmNotificationLog> wrapper = new LambdaQueryWrapper<>();
        if (!context.isPlatformAdmin()) {
            if (context.getOrganizationIds().isEmpty()) {
                Page<AlarmNotificationLogVO> empty = new Page<>(params[0], params[1]);
                empty.setRecords(new ArrayList<>());
                empty.setTotal(0);
                return empty;
            }
            wrapper.in(AlarmNotificationLog::getOrganizationId, context.getOrganizationIds());
        }
        if (alarmEventId != null) {
            alarmEventService.getEventOrThrow(alarmEventId, currentUserId);
            wrapper.eq(AlarmNotificationLog::getAlarmEventId, alarmEventId);
        }
        if ("ROOMS".equals(context.getScopeType())) {
            if (context.getCloudRoomIds() == null || context.getCloudRoomIds().isEmpty()) {
                return emptyLogPage(params[0], params[1]);
            }
            List<Long> scopedEventIds = alarmEventService.list(
                    new LambdaQueryWrapper<com.example.platform.entity.AlarmEvent>()
                            .in(com.example.platform.entity.AlarmEvent::getCloudRoomId,
                                    context.getCloudRoomIds()))
                    .stream().map(com.example.platform.entity.AlarmEvent::getId).toList();
            if (scopedEventIds.isEmpty()) {
                return emptyLogPage(params[0], params[1]);
            }
            wrapper.in(AlarmNotificationLog::getAlarmEventId, scopedEventIds);
        }
        if (StringUtils.hasText(channel)) {
            wrapper.eq(AlarmNotificationLog::getChannel, channel.trim().toUpperCase());
        }
        if (status != null) {
            wrapper.eq(AlarmNotificationLog::getStatus, status);
        }
        wrapper.orderByDesc(AlarmNotificationLog::getSentTime);

        Page<AlarmNotificationLog> source = page(new Page<>(params[0], params[1]), wrapper);
        Page<AlarmNotificationLogVO> result = new Page<>(params[0], params[1]);
        result.setTotal(source.getTotal());
        List<AlarmNotificationLogVO> records = new ArrayList<>();
        for (AlarmNotificationLog log : source.getRecords()) {
            records.add(AlarmNotificationLogVO.fromEntity(log));
        }
        result.setRecords(records);
        return result;
    }

    private Page<AlarmNotificationLogVO> emptyLogPage(int current, int size) {
        Page<AlarmNotificationLogVO> empty = new Page<>(current, size);
        empty.setRecords(new ArrayList<>());
        empty.setTotal(0);
        return empty;
    }
}
