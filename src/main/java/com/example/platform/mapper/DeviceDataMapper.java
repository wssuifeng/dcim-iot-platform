package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.DeviceData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/*
 * 类功能:设备数据访问接口，提供设备数据的自定义统计查询方法
 * */
@Mapper
public interface DeviceDataMapper extends BaseMapper<DeviceData> {

    /*
     * 方法功能:按设备ID和时间范围统计数据值的最小值、最大值、平均值和记录数
     * 参数:deviceId-设备ID, startTime-开始时间, endTime-结束时间
     * 返回数据:Map<String, Object>-包含count/minVal/maxVal/avgVal统计字段
     * */
    Map<String, Object> getStatisticsByDeviceIdAndTimeRange(
            @Param("deviceId") Integer deviceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /*
     * 方法功能:按小时聚合统计设备数据
     * 参数:deviceId-设备ID, startTime-开始时间, endTime-结束时间
     * 返回数据:List<Map<String, Object>>-每个Map包含time_bucket/count/min_val/max_val/avg_val
     * */
    @Select("SELECT DATE_FORMAT(collect_time, '%Y-%m-%d %H:00:00') AS time_bucket, " +
            "COUNT(*) AS count, MIN(data_value) AS min_val, MAX(data_value) AS max_val, AVG(data_value) AS avg_val " +
            "FROM device_data WHERE device_id = #{deviceId} " +
            "AND collect_time BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY time_bucket ORDER BY time_bucket")
    List<Map<String, Object>> aggregateByHour(@Param("deviceId") Integer deviceId,
                                               @Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);

    /*
     * 方法功能:按天聚合统计设备数据
     * 参数:deviceId-设备ID, startTime-开始时间, endTime-结束时间
     * 返回数据:List<Map<String, Object>>-每个Map包含time_bucket/count/min_val/max_val/avg_val
     * */
    @Select("SELECT DATE_FORMAT(collect_time, '%Y-%m-%d') AS time_bucket, " +
            "COUNT(*) AS count, MIN(data_value) AS min_val, MAX(data_value) AS max_val, AVG(data_value) AS avg_val " +
            "FROM device_data WHERE device_id = #{deviceId} " +
            "AND collect_time BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY time_bucket ORDER BY time_bucket")
    List<Map<String, Object>> aggregateByDay(@Param("deviceId") Integer deviceId,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    /*
     * 方法功能:按周聚合统计设备数据
     * 参数:deviceId-设备ID, startTime-开始时间, endTime-结束时间
     * 返回数据:List<Map<String, Object>>-每个Map包含time_bucket/count/min_val/max_val/avg_val
     * */
    @Select("SELECT DATE_FORMAT(DATE_SUB(collect_time, INTERVAL WEEKDAY(collect_time) DAY), '%Y-%m-%d') AS time_bucket, " +
            "COUNT(*) AS count, MIN(data_value) AS min_val, MAX(data_value) AS max_val, AVG(data_value) AS avg_val " +
            "FROM device_data WHERE device_id = #{deviceId} " +
            "AND collect_time BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY time_bucket ORDER BY time_bucket")
    List<Map<String, Object>> aggregateByWeek(@Param("deviceId") Integer deviceId,
                                               @Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);
}
