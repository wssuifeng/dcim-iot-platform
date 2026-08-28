package com.example.platform.service;

import com.example.platform.dto.RoomComparisonVO;

import java.time.LocalDateTime;
import java.util.List;

/*
 * 类功能:机房对比服务接口——跨机房检索对比页的数据源
 *
 * 【口径】设备统计按 typeId/deviceName 过滤（可空）；告警统计额外按时间范围
 *   （必填，默认由调用方给近 24h）与命中设备集过滤（设备过滤非空时告警只统计命中设备）。
 * */
public interface RoomComparisonService {

    /*
     * 方法功能:多机房对比聚合
     * 参数:currentUserId-当前用户ID, roomIds-机房ID列表(2-4个), typeId-设备类型ID(可空),
     *   deviceName-设备名称模糊(可空), start-告警统计开始时间, end-告警统计结束时间
     * 返回数据:List<RoomComparisonVO>-每机房一条对比条目（按 roomIds 顺序）
     * */
    List<RoomComparisonVO> compareRooms(Long currentUserId, List<Long> roomIds, Integer typeId,
                                        String deviceName, LocalDateTime start, LocalDateTime end);
}
