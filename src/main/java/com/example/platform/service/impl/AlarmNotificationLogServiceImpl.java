package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.ResultCode;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.AlarmNotificationLogVO;
import com.example.platform.entity.AlarmNotificationLog;
import com.example.platform.mapper.AlarmNotificationLogMapper;
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.AlarmNotificationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/*
 * 类功能:告警发送日志服务实现类，分页查询发送日志
 * */
@Service
@RequiredArgsConstructor
public class AlarmNotificationLogServiceImpl
        extends ServiceImpl<AlarmNotificationLogMapper, AlarmNotificationLog>
        implements AlarmNotificationLogService {

    private final AlarmEventService alarmEventService;

    @Override
    public Page<AlarmNotificationLogVO> pageLogs(Long currentUserId, Integer current, Integer size,
                                                 Long alarmEventId, String channel, Integer status) {
        int[] params = PaginationUtils.validate(current, size);

        LambdaQueryWrapper<AlarmNotificationLog> wrapper = new LambdaQueryWrapper<>();
        if (alarmEventId != null) {
            alarmEventService.getEventOrThrow(alarmEventId, currentUserId);
            wrapper.eq(AlarmNotificationLog::getAlarmEventId, alarmEventId);
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
}
