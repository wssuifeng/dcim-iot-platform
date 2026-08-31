package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.dto.MetricSampleTrendPointVO;
import com.example.platform.entity.MetricSampleHourly;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/*
 * 类功能:指标小时聚合数据访问接口（S8-4）——窗口聚合 upsert（幂等）与长跨度趋势查询
 * */
@Mapper
public interface MetricSampleHourlyMapper extends BaseMapper<MetricSampleHourly> {

    /*
     * 方法功能:聚合原始采样写入小时桶（唯一键冲突时整桶覆盖，幂等可重跑）
     * 参数:windowStart/windowEnd-聚合窗口 [start, end)
     * 返回数据:int-影响行数（含 upsert）
     * */
    @Insert("INSERT INTO metric_sample_hourly (device_id, point_id, bucket_start, min_value, max_value, avg_value, cnt) "
            + "SELECT device_id, point_id, DATE_FORMAT(sample_time, '%Y-%m-%d %H:00:00'), "
            + "MIN(sample_value), MAX(sample_value), AVG(sample_value), COUNT(*) "
            + "FROM metric_sample "
            + "WHERE sample_time >= #{windowStart} AND sample_time < #{windowEnd} "
            + "GROUP BY device_id, point_id, DATE_FORMAT(sample_time, '%Y-%m-%d %H:00:00') "
            + "ON DUPLICATE KEY UPDATE min_value = VALUES(min_value), max_value = VALUES(max_value), "
            + "avg_value = VALUES(avg_value), cnt = VALUES(cnt)")
    int aggregateWindow(@Param("windowStart") LocalDateTime windowStart,
                        @Param("windowEnd") LocalDateTime windowEnd);

    /*
     * 方法功能:小时桶再聚合为趋势点（>48h 跨度路由目标；桶宽小于 1h 时等效小时粒度）
     * 参数:deviceId-设备ID, pointId-指标ID, startTime/endTime-时间范围, bucketSeconds-目标桶宽秒数
     * 返回数据:List<MetricSampleTrendPointVO>-按桶起始时间升序（均值按 cnt 加权）
     * */
    @Select("SELECT FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(bucket_start) / #{bucketSeconds}) * #{bucketSeconds}) AS time, "
            + "SUM(avg_value * cnt) / SUM(cnt) AS avg_value, "
            + "MIN(min_value) AS min_value, MAX(max_value) AS max_value "
            + "FROM metric_sample_hourly "
            + "WHERE device_id = #{deviceId} AND point_id = #{pointId} "
            + "AND bucket_start >= #{startTime} AND bucket_start <= #{endTime} "
            + "GROUP BY time ORDER BY time")
    List<MetricSampleTrendPointVO> selectTrendBucketsFromHourly(@Param("deviceId") Integer deviceId,
                                                                @Param("pointId") Long pointId,
                                                                @Param("startTime") LocalDateTime startTime,
                                                                @Param("endTime") LocalDateTime endTime,
                                                                @Param("bucketSeconds") long bucketSeconds);

    /*
     * 方法功能:删除早于阈值时间的整小时桶（聚合表保留清理，行级小批量）
     * 参数:before-删除阈值时间, limit-单批删除上限
     * 返回数据:int-本批实际删除行数
     * */
    @Delete("DELETE FROM metric_sample_hourly WHERE bucket_start < #{before} LIMIT #{limit}")
    int deleteBatchBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
