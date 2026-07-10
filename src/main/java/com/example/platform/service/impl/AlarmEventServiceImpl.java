package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.exception.DataScopeViolationException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.dto.AlarmEventVO;
import com.example.platform.dto.AlarmHandlingRecordVO;
import com.example.platform.dto.AlarmStateTransitionVO;
import com.example.platform.dto.DataScopeContext;
import com.example.platform.entity.AlarmEvent;
import com.example.platform.entity.AlarmHandlingRecord;
import com.example.platform.entity.AlarmStateTransition;
import com.example.platform.mapper.AlarmEventMapper;
import com.example.platform.mapper.AlarmHandlingRecordMapper;
import com.example.platform.mapper.AlarmStateTransitionMapper;
import com.example.platform.security.WebSocketSummary;
import com.example.platform.security.UserScopedWebSocketPublisher;
import com.example.platform.service.AlarmEventService;
import com.example.platform.service.DataScopeService;
import com.example.platform.service.InAppNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * 类功能:告警事件权威状态机服务实现类
 *
 * 【状态机流转图】
 *   TRIGGERED → ACKED → HANDLING → RECOVERED → CLOSED
 *   合法流转按顺序推进；非法流转抛 STATE_INVALID_TRANSITION；
 *   重复关闭抛 ALARM_STATE_CONFLICT。
 *
 * 【数据范围依据】依赖 organizationId 与 cloudRoomId；不依赖 Device.location。
 *
 * 【通知编排】状态进入 ACKED/HANDLING/RECOVERED/CLOSED 时触发站内通知和 WebSocket 摘要推送。
 *
 * 【兼容性】v1.0 alerts 表与 AlarmEvent 不强制外键；本服务不动 Alert.isHandled 字段，
 *   全部以 AlarmEvent.state 为权威状态。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmEventServiceImpl
        extends ServiceImpl<AlarmEventMapper, AlarmEvent>
        implements AlarmEventService {

    private final AlarmStateTransitionMapper alarmStateTransitionMapper;
    private final AlarmHandlingRecordMapper alarmHandlingRecordMapper;
    private final DataScopeService dataScopeService;
    private final InAppNotificationService inAppNotificationService;
    private final UserScopedWebSocketPublisher webSocketPublisher;

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
        return AlarmEventVO.fromEntity(getEventOrThrow(alarmEventId, currentUserId));
    }

    @Override
    public Page<AlarmEventVO> pageEvents(Long currentUserId, Integer current, Integer size,
                                         Integer deviceId, String state, Integer alarmLevel,
                                         Long organizationId, Long cloudRoomId) {
        int[] params = PaginationUtils.validate(current, size);
        DataScopeContext context = dataScopeService.resolveDataScope(currentUserId);

        LambdaQueryWrapper<AlarmEvent> wrapper = new LambdaQueryWrapper<>();
        if (!context.isPlatformAdmin()) {
            if (context.getOrganizationIds().isEmpty()) {
                Page<AlarmEventVO> empty = new Page<>(params[0], params[1]);
                empty.setRecords(new ArrayList<>());
                empty.setTotal(0);
                return empty;
            }
            wrapper.in(AlarmEvent::getOrganizationId, context.getOrganizationIds());
        }
        if (organizationId != null) {
            if (!context.isPlatformAdmin()
                    && !context.hasOrganizationAccess(organizationId)) {
                throw new DataScopeViolationException("Organization", String.valueOf(organizationId));
            }
            wrapper.eq(AlarmEvent::getOrganizationId, organizationId);
        }
        if (cloudRoomId != null) {
            if (!context.isPlatformAdmin()
                    && !context.hasCloudRoomAccess(cloudRoomId)) {
                throw new DataScopeViolationException("CloudRoom", String.valueOf(cloudRoomId));
            }
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
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND,
                    "告警事件不存在: " + alarmEventId);
        }
        if (currentUserId != null) {
            ensureEventScope(event, dataScopeService.resolveDataScope(currentUserId));
        }
        return event;
    }

    // ===================== 内部辅助 =====================

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

        // 4. 触发站内通知与 WebSocket 摘要推送
        notifyAndPush(event, toState, action);

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

    private void ensureEventScope(AlarmEvent event, DataScopeContext context) {
        if (context.isPlatformAdmin()) {
            return;
        }
        Long orgId = event.getOrganizationId();
        if (orgId == null) {
            // 缺少组织字段的旧告警：仅允许平台管理员访问
            throw new DataScopeViolationException("AlarmEvent", String.valueOf(event.getId()));
        }
        if (!context.getOrganizationIds().contains(orgId)) {
            throw new DataScopeViolationException("AlarmEvent", String.valueOf(event.getId()));
        }
        if (event.getCloudRoomId() != null && "ROOMS".equals(context.getScopeType())
                && !context.getCloudRoomIds().contains(event.getCloudRoomId())) {
            throw new DataScopeViolationException("AlarmEvent", String.valueOf(event.getId()));
        }
    }

    private void notifyAndPush(AlarmEvent event, String toState, String action) {
        // 站内通知：发给组织内的告警负责人（这里简化为通知触发用户自身，
        // 实际可由组织管理员订阅场景扩展；本轮按契约 §5 保留已读状态独立于发送日志）
        try {
            inAppNotificationService.notifyAlarmEvent(
                    event.getId(),
                    event.getOrganizationId(),
                    event.getCloudRoomId(),
                    "告警状态更新：" + toState,
                    String.format("告警类型 %s 已流转到 %s", event.getAlarmType(), toState));
        } catch (Exception ex) {
            // 通知失败不影响状态机主流程
            log.warn("告警站内通知发送失败 alarmEventId={} error={}",
                    event.getId(), ex.getMessage());
        }

        // WebSocket 摘要：按当前用户推送最小摘要；前端收到后通过 REST 回查详情
        try {
            if (event.getOrganizationId() != null) {
                WebSocketSummary summary = new WebSocketSummary(
                        "ALARM_STATE_TRANSITION",
                        "AlarmEvent",
                        String.valueOf(event.getId()),
                        toState,
                        java.time.Instant.now());
                // 此处推送给触发者；真实环境按组织订阅扩展
                webSocketPublisher.sendSummary(event.getOrganizationId(), summary);
            }
        } catch (Exception ex) {
            log.warn("告警 WebSocket 摘要推送失败 alarmEventId={} error={}",
                    event.getId(), ex.getMessage());
        }
    }
}