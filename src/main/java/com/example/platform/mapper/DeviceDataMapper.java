package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.DeviceData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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
}
