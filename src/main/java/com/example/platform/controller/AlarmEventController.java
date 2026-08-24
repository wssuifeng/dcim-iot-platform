package com.example.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.common.Result;
import com.example.platform.common.annotation.AuditLog;
import com.example.platform.dto.AlarmEventVO;
import com.example.platform.dto.AlarmHandlingActionDTO;
import com.example.platform.dto.AlarmHandlingRecordVO;
import com.example.platform.dto.AlarmStateTransitionVO;
import com.example.platform.security.CurrentUser;
import com.example.platform.security.CurrentUserContext;
import com.example.platform.entity.AlarmEvent;
import com.example.platform.service.AlarmEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 告警事件接口层（第四阶段权威状态机）
 *
 * <p>权限边界：
 * <ul>
 *   <li>处理动作（ACK/HANDLING/RECOVER/CLOSE）：ORG_ADMIN/OPERATOR</li>
 *   <li>查询：ORG_ADMIN/OPERATOR/VIEWER 按授权范围过滤</li>
 * </ul>
 * 安全边界由 Service 层统一校验，前端显隐仅用于交互。
 */
@Tag(name = "告警事件", description = "告警权威状态机接口（ORG_ADMIN/OPERATOR 处理，已认证按范围读）")
@RestController
@RequestMapping("/alarm-events")
@Validated
public class AlarmEventController {

    private final AlarmEventService alarmEventService;
    private final CurrentUserContext currentUserContext;

    public AlarmEventController(AlarmEventService alarmEventService,
                                CurrentUserContext currentUserContext) {
        this.alarmEventService = alarmEventService;
        this.currentUserContext = currentUserContext;
    }

    @Operation(summary = "确认告警", description = "ORG_ADMIN/OPERATOR；状态 TRIGGERED→ACKED")
    @PostMapping("/{id}/ack")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','OPERATOR')")
    @AuditLog(operation = "ACK_ALARM_EVENT", detail = "alarmEventId:#id")
    public Result<AlarmEvent> ack(@Parameter(description = "告警事件ID", required = true)
                                  @PathVariable Long id,
                                  @Valid @RequestBody(required = false) AlarmHandlingActionDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        String note = dto != null ? dto.getNote() : null;
        return Result.success(alarmEventService.acknowledge(id, user.userId(), note));
    }

    @Operation(summary = "开始处理告警", description = "ORG_ADMIN/OPERATOR；状态→HANDLING")
    @PostMapping("/{id}/handling")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','OPERATOR')")
    @AuditLog(operation = "START_HANDLING_ALARM_EVENT", detail = "alarmEventId:#id")
    public Result<AlarmEvent> handling(@Parameter(description = "告警事件ID", required = true)
                                       @PathVariable Long id,
                                       @Valid @RequestBody(required = false) AlarmHandlingActionDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        String note = dto != null ? dto.getNote() : null;
        return Result.success(alarmEventService.startHandling(id, user.userId(), note));
    }

    @Operation(summary = "标记告警已恢复", description = "ORG_ADMIN/OPERATOR；状态→RECOVERED")
    @PostMapping("/{id}/recover")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','OPERATOR')")
    @AuditLog(operation = "RECOVER_ALARM_EVENT", detail = "alarmEventId:#id")
    public Result<AlarmEvent> recover(@Parameter(description = "告警事件ID", required = true)
                                       @PathVariable Long id,
                                       @Valid @RequestBody(required = false) AlarmHandlingActionDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        String note = dto != null ? dto.getNote() : null;
        return Result.success(alarmEventService.recover(id, user.userId(), note));
    }

    @Operation(summary = "关闭告警", description = "ORG_ADMIN/OPERATOR；状态→CLOSED；已关闭重复关闭抛冲突")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','OPERATOR')")
    @AuditLog(operation = "CLOSE_ALARM_EVENT", detail = "alarmEventId:#id")
    public Result<AlarmEvent> close(@Parameter(description = "告警事件ID", required = true)
                                    @PathVariable Long id,
                                    @Valid @RequestBody(required = false) AlarmHandlingActionDTO dto) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        String note = dto != null ? dto.getNote() : null;
        return Result.success(alarmEventService.close(id, user.userId(), note));
    }

    @Operation(summary = "查询告警事件详情", description = "BUSINESS_READ；按授权范围过滤")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','OPERATOR','VIEWER')")
    public Result<AlarmEventVO> detail(@Parameter(description = "告警事件ID", required = true)
                                       @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(alarmEventService.getDetail(id, user.userId()));
    }

    @Operation(summary = "分页查询告警事件", description = "BUSINESS_READ；按授权范围过滤")
    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','OPERATOR','VIEWER')")
    public Result<Page<AlarmEventVO>> page(
            @Parameter(description = "当前页码", example = "1") @RequestParam(defaultValue = "1") Integer current,
            @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") Integer size,
            @Parameter(description = "设备ID（可选）") @RequestParam(required = false) Integer deviceId,
            @Parameter(description = "状态（可选）") @RequestParam(required = false) String state,
            @Parameter(description = "告警级别（可选）") @RequestParam(required = false) Integer alarmLevel,
            @Parameter(description = "组织ID（可选）") @RequestParam(required = false) Long organizationId,
            @Parameter(description = "机房ID（可选）") @RequestParam(required = false) Long cloudRoomId) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(alarmEventService.pageEvents(
                user.userId(), current, size, deviceId, state, alarmLevel,
                organizationId, cloudRoomId));
    }

    @Operation(summary = "查询告警状态流转记录", description = "BUSINESS_READ；返回流转历史")
    @GetMapping("/{id}/transitions")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','OPERATOR','VIEWER')")
    public Result<List<AlarmStateTransitionVO>> transitions(@Parameter(description = "告警事件ID", required = true)
                                                            @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(alarmEventService.listTransitions(id, user.userId()));
    }

    @Operation(summary = "查询告警处理记录", description = "BUSINESS_READ；返回处理动作历史")
    @GetMapping("/{id}/handling-records")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','OPERATOR','VIEWER')")
    public Result<List<AlarmHandlingRecordVO>> handlingRecords(@Parameter(description = "告警事件ID", required = true)
                                                                @PathVariable Long id) {
        CurrentUser user = currentUserContext.requireCurrentUser();
        return Result.success(alarmEventService.listHandlingRecords(id, user.userId()));
    }
}