package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.DeviceData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Map;

@Mapper
public interface DeviceDataMapper extends BaseMapper<DeviceData> {
    /**
     * 查询指定设备在某个时间段内的数据统计信息。
     * 返回的 Map 包含以下键：
     *   - minVal : 最小值
     *   - maxVal : 最大值
     *   - avgVal : 平均值
     *   - count  : 数据条数
     *
     * @param deviceId  设备ID
     * @param startTime 开始时间（包含）
     * @param endTime   结束时间（包含）
     * @return 统计结果 Map，key 为上述统计项，value 为数值
     */
    @Select("SELECT device_id, MIN(data_value) AS minVal, MAX(data_value) AS maxVal, " +
            "AVG(data_value) AS avgVal, COUNT(*) AS count " +
            "FROM device_data WHERE device_id = #{deviceId} " +
            "AND collect_time BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY device_id")
    Map<String, Object> getStatisticsByDeviceIdAndTimeRange(
            @Param("deviceId") Integer deviceId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
