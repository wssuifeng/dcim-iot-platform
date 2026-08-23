package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.AlarmNotificationLogVO;
import com.example.platform.entity.AlarmNotificationLog;

/*
 * 类功能:告警发送日志服务接口（按数据范围查询发送结果，支持状态追溯）
 * */
public interface AlarmNotificationLogService extends IService<AlarmNotificationLog> {

    /*
     * 方法功能:分页查询告警发送日志（按数据范围过滤）
     * 参数:currentUserId-当前用户, current/size-分页, alarmEventId-告警事件ID, channel-通道, status-状态
     * 返回数据:Page<AlarmNotificationLogVO>-日志分页
     * */
    Page<AlarmNotificationLogVO> pageLogs(Long currentUserId, Integer current, Integer size,
                                          Long alarmEventId, String channel, Integer status);
}
