package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.MetricSample;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/*
 * 类功能:指标采样历史数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD与分批清理
 * */
@Mapper
public interface MetricSampleMapper extends BaseMapper<MetricSample> {

    /*
     * 方法功能:分批删除指定点位早于阈值时间的采样记录（保留清理任务用，避免大表长事务）
     * 参数:pointId-点位ID, before-删除阈值时间, limit-单批删除上限
     * 返回数据:int-本批实际删除行数
     * */
    @Delete("DELETE FROM metric_sample WHERE point_id = #{pointId} AND sample_time < #{before} LIMIT #{limit}")
    int deleteBatchBefore(@Param("pointId") Long pointId,
                          @Param("before") LocalDateTime before,
                          @Param("limit") int limit);
}
