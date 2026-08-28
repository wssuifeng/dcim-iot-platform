package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.AlarmEventVO;
import com.example.platform.dto.AlarmHandlingRecordVO;
import com.example.platform.dto.AlarmStatsVO;
import com.example.platform.dto.AlarmStateTransitionVO;
import com.example.platform.dto.AlarmTriggerContext;
import com.example.platform.dto.AlarmTriggerResult;
import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.AlarmHandlingRecord;
import com.example.platform.entity.AlarmStateTransition;
import com.example.platform.entity.Device;
import com.example.platform.entity.PointDefinition;
import com.example.platform.event.AlarmEventRecoveredEvent;
import com.example.platform.event.AlarmEventTriggeredEvent;
import com.example.platform.mapper.AlarmEventMapper;
import com.example.platform.mapper.AlarmHandlingRecordMapper;
import com.example.platform.mapper.AlarmStateTransitionMapper;
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.AlarmRecipientResolver;
import com.example.platform.service.DeviceService;
import com.example.platform.service.InAppNotificationService;
import com.example.platform.service.PointDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
 * 类功能:告警事件权威状态机服务实现类（产生入口+人工流转+查询）
 *
 * 【状态机流转图】
 *   TRIGGERED → ACKED → HANDLING → RECOVERED → CLOSED
 *   合法流转按顺序推进；非法流转抛 STATE_INVALID_TRANSITION；
 *   重复关闭抛 ALARM_STATE_CONFLICT。
 *
 * 【产生入口（trigger/autoRecover）】
 *   活动告警去重键：deviceId+pointId+alarmType 且 state NOT IN (RECOVERED,CLOSED)；
 *   新级别数字更小时升级（数字小=更紧急）；trigger/autoRecover 事务提交后发布
 *   单向事件（AlarmEventTriggeredEvent/AlarmEventRecoveredEvent）交由分发服务处理，
 *   本服务不直接推送 WebSocket（职责移交，D14/D17）。
 *
 * 【人工流转通知】人工 ACK/HANDLING/RECOVER/CLOSE 仅发站内通知；
 *   WS 摘要推送只属于自动链路三事件（ALARM_CREATED/UPGRADED/RECOVERED，D13）。
 *
 * 【数据范围依据】依赖 organizationId 与 cloudRoomId；不依赖 Device.location。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmEventServiceImpl
        extends ServiceImpl<AlarmEventMapper, AlarmEvent>
        implements AlarmEventService {

    private final AlarmStateTransitionMapper alarmStateTransitionMapper;
    private final AlarmHandlingRecordMapper alarmHandlingRecordMapper;
    private final InAppNotificationService inAppNotificationService;
    private final AlarmRecipientResolver alarmRecipientResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final DeviceService deviceService;
    private final PointDefinitionService pointDefinitionService;

    public static final String STATE_TRIGGERED = "TRIGGERED";
    public static final String STATE_ACKED = "ACKED";
    public static final String STATE_HANDLING = "HANDLING";
    public static final String STATE_RECOVERED = "RECOVERED";
    public static final String STATE_CLOSED = "CLOSED";

    public static final String ACTION_ACK = "ACK";
    public static final String ACTION_HANDLING = "HANDLING";
    public static final String ACTION_RECOVER = "RECOVER";
    public static final String ACTION_CLOSE = "CLOSE";

    /**
     * 状态合法流转表（key=当前状态，value=允许的下一状态集合）。
     */
    private static final Map<String, Set<String>> TRANSITIONS = new LinkedHashMap<>();

    static {
        TRANSITIONS.put(STATE_TRIGGERED, Set.of(STATE_ACKED, STATE_RECOVERED, STATE_CLOSED));
        TRANSITIONS.put(STATE_ACKED, Set.of(STATE_HANDLING, STATE_RECOVERED, STATE_CLOSED));
        TRANSITIONS.put(STATE_HANDLING, Set.of(STATE_RECOVERED, STATE_CLOSED));
        TRANSITIONS.put(STATE_RECOVERED, Set.of(STATE_CLOSED));
        TRANSITIONS.put(STATE_CLOSED, Set.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlarmTriggerResult trigger(AlarmTriggerContext context) {
        AlarmEvent existing = findActiveEvent(
                context.getDeviceId(), context.getPointId(), context.getAlarmType());

        if (existing == null) {
            AlarmEvent event = new AlarmEvent();
            event.setDeviceId(context.getDeviceId());
            event.setPointId(context.getPointId());
            event.setAlarmType(context.getAlarmType());
            event.setAlarmLevel(context.getAlarmLevel());
            event.setAlarmMessage(context.getAlarmMessage());
            event.setState(STATE_TRIGGERED);
            event.setCloudRoomId(context.getCloudRoomId());
            if (!save(event)) {
                throw new BusinessException("写入告警事件失败");
            }
            insertTransition(event.getId(), null, STATE_TRIGGERED, null, "系统触发");
            eventPublisher.publishEvent(new AlarmEventTriggeredEvent(
                    event.getId(), AlarmEventTriggeredEvent.KIND_CREATED));
            log.info("告警产生 alarmEventId={} device={} type={} level={}",
                    event.getId(), event.getDeviceId(), event.getAlarmType(), event.getAlarmLevel());
            return AlarmTriggerResult.of(event, AlarmTriggerResult.KIND_CREATED);
        }

        // 已有活动告警：新级别数字更小（更紧急）时升级，否则去重跳过
        if (context.getAlarmLevel() != null
                && context.getAlarmLevel() < existing.getAlarmLevel()) {
            int fromLevel = existing.getAlarmLevel();
            existing.setAlarmLevel(context.getAlarmLevel());
            existing.setAlarmMessage(context.getAlarmMessage());
            if (!updateById(existing)) {
                throw new BusinessException("升级告警事件失败");
            }
            insertTransition(existing.getId(), STATE_TRIGGERED, STATE_TRIGGERED, null,
                    String.format("级别升级 %d → %d", fromLevel, context.getAlarmLevel()));
            eventPublisher.publishEvent(new AlarmEventTriggeredEvent(
                    existing.getId(), AlarmEventTriggeredEvent.KIND_UPGRADED));
            log.info("告警升级 alarmEventId={} level {} → {}",
                    existing.getId(), fromLevel, context.getAlarmLevel());
            return AlarmTriggerResult.of(existing, AlarmTriggerResult.KIND_UPGRADED);
        }

        log.debug("告警去重跳过 alarmEventId={} level={}", existing.getId(), existing.getAlarmLevel());
        return AlarmTriggerResult.of(existing, AlarmTriggerResult.KIND_SKIPPED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean autoRecover(Integer deviceId, Long pointId, String alarmType) {
        AlarmEvent existing = findActiveEvent(deviceId, pointId, alarmType);
        if (existing == null) {
            return false;
        }
        String fromState = existing.getState();
        existing.setState(STATE_RECOVERED);
        existing.setRecoveredTime(LocalDateTime.now());
        if (!updateById(existing)) {
            throw new BusinessException("自动恢复告警失败");
        }
        insertTransition(existing.getId(), fromState, STATE_RECOVERED, null, "系统自动恢复（值恢复正常/设备上线）");
        eventPublisher.publishEvent(new AlarmEventRecoveredEvent(existing.getId()));
        log.info("告警自动恢复 alarmEventId={} device={} type={}",
                existing.getId(), deviceId, alarmType);
        return true;
    }

    @Override
    public List<AlarmEvent> listActiveByAlarmType(String alarmType) {
        LambdaQueryWrapper<AlarmEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmEvent::getAlarmType, alarmType)
                .notIn(AlarmEvent::getState, List.of(STATE_RECOVERED, STATE_CLOSED));
        return list(wrapper);
    }

    @Override
    public AlarmStatsVO countStats(Long currentUserId, LocalDateTime start, LocalDateTime end) {
        return countStats(currentUserId, start, end, null);
    }

    @Override
    public AlarmStatsVO countStats(Long currentUserId, LocalDateTime start, LocalDateTime end, Long cloudRoomId) {
        List<AlarmEvent> events = listScopedEvents(currentUserId, start, end, cloudRoomId);
        AlarmStatsVO stats = new AlarmStatsVO();
        stats.setStateDistribution(events.stream()
                .filter(event -> event.getState() != null)
                .collect(Collectors.groupingBy(AlarmEvent::getState, Collectors.counting())));
        stats.setLevelDistribution(events.stream()
                .filter(event -> event.getAlarmLevel() != null)
                .collect(Collectors.groupingBy(
                        event -> "L" + event.getAlarmLevel(),
                        TreeMap::new,
                        Collectors.counting())));
        Map<Integer, Long> deviceCounts = events.stream()
                .filter(event -> event.getDeviceId() != null)
                .collect(Collectors.groupingBy(AlarmEvent::getDeviceId, Collectors.counting()));
        stats.setDeviceRanking(deviceCounts.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("deviceId", entry.getKey());
                    item.put("alarmCount", entry.getValue());
                    return item;
                })
                .collect(Collectors.toList()));
        return stats;
    }

    @Override
    public Map<String, Long> trendStats(Long currentUserId, LocalDate start, LocalDate end) {
        return trendStats(currentUserId, start, end, null);
    }

    @Override
    public Map<String, Long> trendStats(Long currentUserId, LocalDate start, LocalDate end, Long cloudRoomId) {
        Map<String, Long> trend = new LinkedHashMap<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            trend.put(day.toString(), 0L);
        }
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();
        for (AlarmEvent event : listScopedEvents(currentUserId, from, to, cloudRoomId)) {
            if (event.getTriggeredTime() == null) {
                continue;
            }
            String key = event.getTriggeredTime().toLocalDate().toString();
            if (trend.containsKey(key)) {
                trend.put(key, trend.get(key) + 1L);
            }
        }
        return trend;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlarmEvent acknowledge(Long alarmEventId, Long currentUserId, String note) {
        return transition(alarmEventId, STATE_ACKED, ACTION_ACK, currentUserId, note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlarmEvent startHandling(Long alarmEventId, Long currentUserId, String note) {
        return transition(alarmEventId, STATE_HANDLING, ACTION_HANDLING, currentUserId, note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlarmEvent recover(Long alarmEventId, Long currentUserId, String note) {
        return transition(alarmEventId, STATE_RECOVERED, ACTION_RECOVER, currentUserId, note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlarmEvent close(Long alarmEventId, Long currentUserId, String note) {
        AlarmEvent event = getEventOrThrow(alarmEventId, currentUserId);
        if (STATE_CLOSED.equals(event.getState())) {
            // 重复关闭抛状态冲突
            throw new BusinessException(ResultCode.ALARM_STATE_CONFLICT,
                    "告警事件已关闭，不能重复关闭: " + alarmEventId);
        }
        return transition(event, STATE_CLOSED, ACTION_CLOSE, currentUserId, note);
    }

    @Override
    public AlarmEventVO getDetail(Long alarmEventId, Long currentUserId) {
        AlarmEventVO vo = AlarmEventVO.fromEntity(getEventOrThrow(alarmEventId, currentUserId));
        fillDisplayFields(new ArrayList<>(List.of(vo)));
        return vo;
    }

    @Override
    public Page<AlarmEventVO> pageEvents(Long currentUserId, Integer current, Integer size,
                                         Integer deviceId, String state, Integer alarmLevel,
                                         Long organizationId, Long cloudRoomId) {
        int[] params = PaginationUtils.validate(current, size);

        LambdaQueryWrapper<AlarmEvent> wrapper = new LambdaQueryWrapper<>();
        if (cloudRoomId != null) {
            wrapper.eq(AlarmEvent::getCloudRoomId, cloudRoomId);
        }
        if (deviceId != null) {
            wrapper.eq(AlarmEvent::getDeviceId, deviceId);
        }
        if (StringUtils.hasText(state)) {
            wrapper.eq(AlarmEvent::getState, state.trim().toUpperCase());
        }
        if (alarmLevel != null) {
            wrapper.eq(AlarmEvent::getAlarmLevel, alarmLevel);
        }
        wrapper.orderByDesc(AlarmEvent::getTriggeredTime);

        Page<AlarmEvent> source = page(new Page<>(params[0], params[1]), wrapper);
        Page<AlarmEventVO> result = new Page<>(params[0], params[1]);
        result.setTotal(source.getTotal());
        List<AlarmEventVO> records = new ArrayList<>(source.getRecords().size());
        for (AlarmEvent event : source.getRecords()) {
            records.add(AlarmEventVO.fromEntity(event));
        }
        fillDisplayFields(records);
        result.setRecords(records);
        return result;
    }

    @Override
    public List<AlarmStateTransitionVO> listTransitions(Long alarmEventId, Long currentUserId) {
        getEventOrThrow(alarmEventId, currentUserId);
        LambdaQueryWrapper<AlarmStateTransition> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmStateTransition::getAlarmEventId, alarmEventId)
                .orderByAsc(AlarmStateTransition::getCreateTime);
        List<AlarmStateTransition> list = alarmStateTransitionMapper.selectList(wrapper);
        List<AlarmStateTransitionVO> result = new ArrayList<>(list.size());
        for (AlarmStateTransition t : list) {
            result.add(AlarmStateTransitionVO.fromEntity(t));
        }
        return result;
    }

    @Override
    public List<AlarmHandlingRecordVO> listHandlingRecords(Long alarmEventId, Long currentUserId) {
        getEventOrThrow(alarmEventId, currentUserId);
        LambdaQueryWrapper<AlarmHandlingRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmHandlingRecord::getAlarmEventId, alarmEventId)
                .orderByAsc(AlarmHandlingRecord::getCreateTime);
        List<AlarmHandlingRecord> list = alarmHandlingRecordMapper.selectList(wrapper);
        List<AlarmHandlingRecordVO> result = new ArrayList<>(list.size());
        for (AlarmHandlingRecord r : list) {
            result.add(AlarmHandlingRecordVO.fromEntity(r));
        }
        return result;
    }

    @Override
    public AlarmEvent getEventOrThrow(Long alarmEventId, Long currentUserId) {
        AlarmEvent event = getById(alarmEventId);
        if (event == null) {
            throw new BusinessException(ResultCode.ALARM_EVENT_NOT_FOUND,
                    "告警事件不存在: " + alarmEventId);
        }
        return event;
    }

    // ===================== 内部辅助 =====================

    private Page<AlarmEventVO> emptyAlarmPage(int current, int size) {
        Page<AlarmEventVO> empty = new Page<>(current, size);
        empty.setRecords(new ArrayList<>());
        empty.setTotal(0);
        return empty;
    }

    private List<AlarmEvent> listScopedEvents(Long currentUserId, LocalDateTime start, LocalDateTime end) {
        return listScopedEvents(currentUserId, start, end, null);
    }

    private List<AlarmEvent> listScopedEvents(Long currentUserId, LocalDateTime start, LocalDateTime end,
                                              Long cloudRoomId) {
        LambdaQueryWrapper<AlarmEvent> wrapper = new LambdaQueryWrapper<>();
        if (start != null) {
            wrapper.ge(AlarmEvent::getTriggeredTime, start);
        }
        if (end != null) {
            wrapper.lt(AlarmEvent::getTriggeredTime, end);
        }
        if (cloudRoomId != null) {
            wrapper.eq(AlarmEvent::getCloudRoomId, cloudRoomId);
        }
        return list(wrapper);
    }

    private AlarmEvent findActiveEvent(Integer deviceId, Long pointId, String alarmType) {
        LambdaQueryWrapper<AlarmEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmEvent::getDeviceId, deviceId)
                .eq(AlarmEvent::getAlarmType, alarmType)
                .notIn(AlarmEvent::getState, List.of(STATE_RECOVERED, STATE_CLOSED))
                .orderByDesc(AlarmEvent::getTriggeredTime)
                .last("LIMIT 1");
        if (pointId == null) {
            wrapper.isNull(AlarmEvent::getPointId);
        } else {
            wrapper.eq(AlarmEvent::getPointId, pointId);
        }
        return getOne(wrapper, false);
    }

    private void insertTransition(Long alarmEventId, String fromState, String toState,
                                  Long operatorUserId, String remark) {
        AlarmStateTransition transition = new AlarmStateTransition();
        transition.setAlarmEventId(alarmEventId);
        transition.setFromState(fromState);
        transition.setToState(toState);
        transition.setOperatorUserId(operatorUserId);
        transition.setRemark(remark);
        if (alarmStateTransitionMapper.insert(transition) <= 0) {
            throw new BusinessException("写入状态流转记录失败");
        }
    }

    private AlarmEvent transition(Long alarmEventId, String toState, String action,
                                  Long currentUserId, String note) {
        AlarmEvent event = getEventOrThrow(alarmEventId, currentUserId);
        return transition(event, toState, action, currentUserId, note);
    }

    private AlarmEvent transition(AlarmEvent event, String toState, String action,
                                  Long currentUserId, String note) {
        String fromState = event.getState();
        validateTransition(fromState, toState, event.getId());

        // 1. 更新权威状态及时间字段
        event.setState(toState);
        if (STATE_RECOVERED.equals(toState)) {
            event.setRecoveredTime(LocalDateTime.now());
        } else if (STATE_CLOSED.equals(toState)) {
            event.setClosedTime(LocalDateTime.now());
        }
        if (!updateById(event)) {
            throw new BusinessException("更新告警状态失败");
        }

        // 2. 写状态流转记录
        AlarmStateTransition transition = new AlarmStateTransition();
        transition.setAlarmEventId(event.getId());
        transition.setFromState(fromState);
        transition.setToState(toState);
        transition.setOperatorUserId(currentUserId);
        transition.setRemark(note);
        if (alarmStateTransitionMapper.insert(transition) <= 0) {
            throw new BusinessException("写入状态流转记录失败");
        }

        // 3. 写处理记录
        AlarmHandlingRecord record = new AlarmHandlingRecord();
        record.setAlarmEventId(event.getId());
        record.setHandlerUserId(currentUserId);
        record.setHandlingAction(action);
        record.setHandlingNote(note);
        if (alarmHandlingRecordMapper.insert(record) <= 0) {
            throw new BusinessException("写入处理记录失败");
        }

        // 4. 人工流转仅发站内通知；WS 摘要推送只属于自动链路三事件（D13），职责在分发服务
        notifyInApp(event, toState);

        log.info("告警状态流转 alarmEventId={} from={} to={} operator={}",
                event.getId(), fromState, toState, currentUserId);
        return event;
    }

    private void validateTransition(String fromState, String toState, Long eventId) {
        if (fromState == null) {
            // 理论不应出现，防御性处理
            throw new BusinessException(ResultCode.ALARM_STATE_INVALID_TRANSITION,
                    "告警事件无初始状态: " + eventId);
        }
        Set<String> allowed = TRANSITIONS.get(fromState);
        if (allowed == null || !allowed.contains(toState)) {
            throw new BusinessException(ResultCode.ALARM_STATE_INVALID_TRANSITION,
                    String.format("非法流转: %s → %s（告警事件ID=%d）", fromState, toState, eventId));
        }
    }

    private void notifyInApp(AlarmEvent event, String toState) {
        Set<Long> recipientUserIds;
        try {
            recipientUserIds = alarmRecipientResolver.resolveRecipientUserIds();
        } catch (Exception ex) {
            log.warn("解析告警接收用户失败 alarmEventId={} error={}",
                    event.getId(), ex.getMessage());
            return;
        }
        if (recipientUserIds.isEmpty()) {
            log.debug("告警无可用接收用户 alarmEventId={}", event.getId());
            return;
        }

        String title = "告警状态更新：" + toState;
        String content = String.format("告警类型 %s 已流转到 %s", event.getAlarmType(), toState);
        try {
            inAppNotificationService.notifyAlarmEvent(
                    event.getId(),
                    recipientUserIds,
                    title,
                    content);
        } catch (Exception ex) {
            log.warn("告警站内通知发送失败 alarmEventId={} error={}",
                    event.getId(), ex.getMessage());
        }
    }

    private void fillDisplayFields(List<AlarmEventVO> vos) {
        Set<Integer> deviceIds = vos.stream()
                .map(AlarmEventVO::getDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> pointIds = vos.stream()
                .map(AlarmEventVO::getPointId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<Device> devices = deviceIds.isEmpty() ? List.of() : deviceService.listByIds(deviceIds);
        List<PointDefinition> points = pointIds.isEmpty() ? List.of() : pointDefinitionService.listByIds(pointIds);
        Map<Integer, Device> deviceById = devices == null ? Map.of()
                : devices.stream().collect(Collectors.toMap(Device::getId, Function.identity()));
        Map<Long, PointDefinition> pointById = points == null ? Map.of()
                : points.stream().collect(Collectors.toMap(PointDefinition::getId, Function.identity()));
        for (AlarmEventVO vo : vos) {
            Device device = deviceById.get(vo.getDeviceId());
            if (device != null) {
                vo.setDeviceName(device.getDeviceName());
            }
            PointDefinition point = pointById.get(vo.getPointId());
            if (point != null) {
                vo.setPointName(point.getPointName());
                vo.setPointUnit(point.getUnit());
            }
        }
    }
}
