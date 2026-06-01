package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.Alert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/*
 * 类功能:告警数据访问接口，提供告警的自定义统计查询方法
 * */
@Mapper
public interface AlertMapper extends BaseMapper<Alert> {

    /*
     * 方法功能:按设备ID统计告警数量和各级别分布
     * 参数:deviceId-设备ID
     * 返回数据:Map<String, Object>-包含total/unhandled/level1/level2/level3等统计字段
     * */
    @Select("SELECT COUNT(*) AS total, " +
            "SUM(CASE WHEN is_handled = 0 THEN 1 ELSE 0 END) AS unhandled, " +
            "SUM(CASE WHEN alert_level = 1 THEN 1 ELSE 0 END) AS level1, " +
            "SUM(CASE WHEN alert_level = 2 THEN 1 ELSE 0 END) AS level2, " +
            "SUM(CASE WHEN alert_level = 3 THEN 1 ELSE 0 END) AS level3 " +
            "FROM alerts WHERE device_id = #{deviceId}")
    Map<String, Object> getStatsByDeviceId(@Param("deviceId") Integer deviceId);

    /*
     * 方法功能:按时间段统计全局告警数量和各级别分布
     * 参数:startTime-开始时间, endTime-结束时间
     * 返回数据:Map<String, Object>-包含total/unhandled/level1/level2/level3等统计字段
     * */
    @Select("SELECT COUNT(*) AS total, " +
            "SUM(CASE WHEN is_handled = 0 THEN 1 ELSE 0 END) AS unhandled, " +
            "SUM(CASE WHEN alert_level = 1 THEN 1 ELSE 0 END) AS level1, " +
            "SUM(CASE WHEN alert_level = 2 THEN 1 ELSE 0 END) AS level2, " +
            "SUM(CASE WHEN alert_level = 3 THEN 1 ELSE 0 END) AS level3 " +
            "FROM alerts WHERE create_time BETWEEN #{startTime} AND #{endTime}")
    Map<String, Object> getStatsByTimeRange(@Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);

    /*
     * 方法功能:按时间段统计各设备的告警数量，返回设备维度的告警排行
     * 参数:startTime-开始时间, endTime-结束时间
     * 返回数据:List<Map<String, Object>>-每个Map包含device_id和alert_count
     * */
    @Select("SELECT device_id, COUNT(*) AS alert_count " +
            "FROM alerts WHERE create_time BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY device_id ORDER BY alert_count DESC")
    List<Map<String, Object>> listDeviceAlertCountByTimeRange(@Param("startTime") LocalDateTime startTime,
                                                              @Param("endTime") LocalDateTime endTime);
}
