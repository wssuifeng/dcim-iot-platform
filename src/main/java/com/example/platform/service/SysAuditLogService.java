package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.entity.SysAuditLog;

import java.time.LocalDateTime;

/*
 * 类功能:安全审计日志服务接口，定义审计日志的记录、查询和过期清理业务方法
 * */
public interface SysAuditLogService extends IService<SysAuditLog> {

    /*
     * 方法功能:按条件分页查询审计日志
     * 参数:current-当前页码, size-每页大小, userId-操作人ID(可选), operation-操作类型(可选), startTime-开始时间(可选), endTime-结束时间(可选)
     * 返回数据:Page<SysAuditLog>-分页审计日志数据
     * */
    Page<SysAuditLog> pageLogs(Integer current, Integer size, Long userId, String operation,
                               LocalDateTime startTime, LocalDateTime endTime);

    /*
     * 方法功能:清理指定时间之前的过期审计日志（数据保留策略）
     * 参数:beforeTime-保留截止时间
     * 返回数据:int-删除的记录条数
     * */
    int cleanExpiredLogs(LocalDateTime beforeTime);

    /*
     * 方法功能:记录一条审计日志（登录成功/失败等安全事件）
     * 参数:userId-操作人ID, username-操作人账号, operation-操作类型, ip-操作IP, detail-操作详情
     * 返回数据:无
     * */
    void log(Long userId, String username, String operation, String ip, String detail);

    /*
     * 方法功能:记录结构化审计日志（产品化扩展，支持角色、数据范围、目标资源、操作结果等结构化字段）
     * 参数:userId-操作人ID, username-操作人账号, role-操作人角色, organizationId-操作人所属组织,
     *      cloudRoomId-操作人所属机房, action-标准化操作动作, targetType-目标类型, targetId-目标ID,
     *      result-操作结果(SUCCESS/FAILURE/REJECTED), errorCode-错误码, ip-客户端IP, traceId-链路追踪ID, detail-详情
     * 返回数据:无
     * */
    void logStructured(Long userId, String username, String role, Long organizationId, Long cloudRoomId,
                       String action, String targetType, String targetId, String result, String errorCode,
                       String ip, String traceId, String detail);
}
