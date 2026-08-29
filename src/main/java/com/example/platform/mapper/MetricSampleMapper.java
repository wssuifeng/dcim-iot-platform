package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.dto.MetricSampleTrendPointVO;
import com.example.platform.entity.MetricSample;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/*
 * 类功能:指标采样历史数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD与分批清理
 * */
@Mapper
public interface MetricSampleMapper extends BaseMapper<MetricSample> {

    /*
     * 方法功能:分批删除指定指标早于阈值时间的采样记录（保留清理任务用，避免大表长事务）
     * 参数:pointId-指标ID, before-删除阈值时间, limit-单批删除上限
     * 返回数据:int-本批实际删除行数
     * */
    @Delete("DELETE FROM metric_sample WHERE point_id = #{pointId} AND sample_time < #{before} LIMIT #{limit}")
    int deleteBatchBefore(@Param("pointId") Long pointId,
                          @Param("before") LocalDateTime before,
                          @Param("limit") int limit);

    /*
     * 方法功能:按时间桶聚合采样值（趋势图降采样：桶内均值/最小/最大）
     * 参数:deviceId-设备ID, pointId-指标ID, startTime/endTime-时间范围, bucketSeconds-桶宽秒数
     * 返回数据:List<MetricSampleTrendPointVO>-按桶起始时间升序
     * */
    @Select("SELECT FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(sample_time) / #{bucketSeconds}) * #{bucketSeconds}) AS time, "
            + "AVG(sample_value) AS avg_value, MIN(sample_value) AS min_value, MAX(sample_value) AS max_value "
            + "FROM metric_sample "
            + "WHERE device_id = #{deviceId} AND point_id = #{pointId} "
            + "AND sample_time >= #{startTime} AND sample_time <= #{endTime} "
            + "GROUP BY time ORDER BY time")
    List<MetricSampleTrendPointVO> selectTrendBuckets(@Param("deviceId") Integer deviceId,
                                                      @Param("pointId") Long pointId,
                                                      @Param("startTime") LocalDateTime startTime,
                                                      @Param("endTime") LocalDateTime endTime,
                                                      @Param("bucketSeconds") long bucketSeconds);
}
