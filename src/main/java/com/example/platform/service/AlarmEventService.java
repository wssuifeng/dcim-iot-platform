package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.AlarmEventVO;
import com.example.platform.dto.AlarmHandlingRecordVO;
import com.example.platform.dto.AlarmStateTransitionVO;
import com.example.platform.entity.AlarmEvent;

import java.util.List;

/*
 * 类功能:告警事件权威状态机服务接口
 *
 * 【角色与范围】
 *   - 处理动作（ACK/HANDLING/RECOVER/CLOSE）：ORG_ADMIN/OPERATOR（按设备组织/机房范围）
 *   - 查询：ORG_ADMIN/OPERATOR/VIEWER（按授权范围过滤）
 *
 * 【状态机】TRIGGERED→ACKED→HANDLING→RECOVERED→CLOSED
 *   - 非法流转抛 BusinessException(STATE_INVALID_TRANSITION)
 *   - 重复关闭抛 BusinessException(ALARM_STATE_CONFLICT)
 *
 * 【兼容性】v1.0 alerts 表保留不变；Alert.isHandled 仅作派生展示，
 *   AlarmEvent.state 是第四阶段权威状态。
 * */
public interface AlarmEventService extends IService<AlarmEvent> {

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
     * 方法功能:校验当前用户对目标告警事件有权访问；未授权抛 DataScopeViolationException
     * */
    AlarmEvent getEventOrThrow(Long alarmEventId, Long currentUserId);
}