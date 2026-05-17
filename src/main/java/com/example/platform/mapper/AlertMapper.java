package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.Alert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AlertMapper extends BaseMapper<Alert> {

    /*
     * 方法功能: 根据设备ID统计对应设备的告警数量、最高级别、平均级别、最低级别
     * 参数: Integer deviceId - 设备ID
     * 返回数据: Map<String, Object> - 包含 alertCount(告警总数), maxLevel(最高级别), avgLevel(平均级别), minLevel(最低级别) 四个键
     */
    @Select("SELECT COUNT(*) AS alertCount, " +
            "MAX(alert_level) AS maxLevel, " +
            "AVG(alert_level) AS avgLevel, " +
            "MIN(alert_level) AS minLevel " +
            "FROM alerts WHERE device_id = #{deviceId}")
    Map<String, Object> getStatsByDeviceId(@Param("deviceId") Integer deviceId);

    /*
     * 方法功能: 按时间区间统计区间内告警数量、最高级别、平均级别、最低级别
     * 参数: LocalDateTime startTime - 开始时间（包含）
     *       LocalDateTime endTime   - 结束时间（包含）
     * 返回数据: Map<String, Object> - 包含 alertCount, maxLevel, avgLevel, minLevel 四个键
     */
    @Select("SELECT COUNT(*) AS alertCount, " +
            "MAX(alert_level) AS maxLevel, " +
            "AVG(alert_level) AS avgLevel, " +
            "MIN(alert_level) AS minLevel " +
            "FROM alerts WHERE create_time BETWEEN #{startTime} AND #{endTime}")
    Map<String, Object> getStatsByTimeRange(@Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);

    /*
     * 方法功能: 按时间区间统计所有设备的告警数量，以告警数量降序排序
     * 参数: LocalDateTime startTime - 开始时间（包含）
     *       LocalDateTime endTime   - 结束时间（包含）
     * 返回数据: List<Map<String, Object>> - 每条 Map 包含 deviceId(设备ID) 和 alertCount(告警数量)，按告警数量降序排列
     */
    @Select("SELECT device_id AS deviceId, COUNT(*) AS alertCount " +
            "FROM alerts WHERE create_time BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY device_id ORDER BY alertCount DESC")
    List<Map<String, Object>> listDeviceAlertCountByTimeRange(@Param("startTime") LocalDateTime startTime,
                                                              @Param("endTime") LocalDateTime endTime);
}
