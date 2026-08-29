package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.AlarmEventVO;
import com.example.platform.dto.AlarmHandlingRecordVO;
import com.example.platform.dto.AlarmStatsVO;
import com.example.platform.dto.AlarmStateTransitionVO;
import com.example.platform.dto.AlarmTriggerContext;
import com.example.platform.dto.AlarmTriggerResult;
import com.example.platform.entity.AlarmEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/*
 * 类功能:告警事件权威状态机服务接口（产生入口+人工流转+查询）
 *
 * 【角色与范围】
 *   - 产生入口 trigger/autoRecover：评估引擎与离线检测（系统调用，无用户上下文）
 *   - 处理动作（ACK/HANDLING/RECOVER/CLOSE）：ADMIN/OPERATOR
 *   - 查询：ADMIN/OPERATOR/VIEWER
 *
 * 【状态机】TRIGGERED→ACKED→HANDLING→RECOVERED→CLOSED
 *   - 非法流转抛 BusinessException(STATE_INVALID_TRANSITION)
 *   - 重复关闭抛 BusinessException(ALARM_STATE_CONFLICT)
 *
 * 【去重键】deviceId+pointId+alarmType 且 state NOT IN (RECOVERED,CLOSED)；
 *   新级别数字更小时升级（数字小=更紧急），否则 SKIPPED（不通知）。
 *
 * 【单向事件链】trigger/autoRecover 事务提交后发布
 *   AlarmEventTriggeredEvent / AlarmEventRecoveredEvent（仅向前传播至分发）。
 * */
public interface AlarmEventService extends IService<AlarmEvent> {

    /*
     * 方法功能:产生或升级告警（活动告警去重，新级别更小时升级）
     * 参数:context-触发上下文（设备/指标/类型/级别/文案/组织/机房）
     * 返回数据:AlarmTriggerResult-事件与kind（CREATED/UPGRADED/SKIPPED）
     * */
    AlarmTriggerResult trigger(AlarmTriggerContext context);

    /*
     * 方法功能:系统自动恢复活动告警（值恢复正常/设备恢复上线），操作人标记为系统
     * 参数:deviceId-设备ID, pointId-指标ID（通用告警为null）, alarmType-告警类型
     * 返回数据:boolean-是否执行了恢复（无活动告警返回false）
     * */
    boolean autoRecover(Integer deviceId, Long pointId, String alarmType);

    /*
     * 方法功能:查询指定类型的全部活动告警（离线检测反向扫描用）
     * 参数:alarmType-告警类型（如 OFFLINE）
     * 返回数据:List<AlarmEvent>-活动态告警列表（state NOT IN RECOVERED/CLOSED）
     * */
    List<AlarmEvent> listActiveByAlarmType(String alarmType);

    /*
     * 方法功能:按用户数据范围聚合告警统计
     * 参数:currentUserId-当前用户ID, start-开始时间, end-结束时间
     * 返回数据:AlarmStatsVO-状态、级别和设备排名统计
     * */
    AlarmStatsVO countStats(Long currentUserId, LocalDateTime start, LocalDateTime end);

    /*
     * 方法功能:按用户数据范围聚合告警统计（限定机房）
     * 参数:currentUserId-当前用户ID, start-开始时间, end-结束时间, cloudRoomId-机房ID
     * 返回数据:AlarmStatsVO-状态、级别和设备排名统计
     * */
    AlarmStatsVO countStats(Long currentUserId, LocalDateTime start, LocalDateTime end, Long cloudRoomId);

    /*
     * 方法功能:按用户数据范围聚合告警趋势
     * 参数:currentUserId-当前用户ID, start-开始日期, end-结束日期
     * 返回数据:Map<String, Long>-日期到触发数量的映射
     * */
    Map<String, Long> trendStats(Long currentUserId, LocalDate start, LocalDate end);

    /*
     * 方法功能:按用户数据范围聚合告警趋势（限定机房）
     * 参数:currentUserId-当前用户ID, start-开始日期, end-结束日期, cloudRoomId-机房ID
     * 返回数据:Map<String, Long>-日期到触发数量的映射
     * */
    Map<String, Long> trendStats(Long currentUserId, LocalDate start, LocalDate end, Long cloudRoomId);

    AlarmEvent acknowledge(Long alarmEventId, Long currentUserId, String note);

    AlarmEvent startHandling(Long alarmEventId, Long currentUserId, String note);

    AlarmEvent recover(Long alarmEventId, Long currentUserId, String note);

    AlarmEvent close(Long alarmEventId, Long currentUserId, String note);

    AlarmEventVO getDetail(Long alarmEventId, Long currentUserId);

    Page<AlarmEventVO> pageEvents(Long currentUserId, Integer current, Integer size,
                                  Integer deviceId, String state, Integer alarmLevel,
                                  Long organizationId, Long cloudRoomId);

    List<AlarmStateTransitionVO> listTransitions(Long alarmEventId, Long currentUserId);

    List<AlarmHandlingRecordVO> listHandlingRecords(Long alarmEventId, Long currentUserId);

    /*
     * 方法功能:校验目标告警事件存在并返回
     * */
    AlarmEvent getEventOrThrow(Long alarmEventId, Long currentUserId);
}