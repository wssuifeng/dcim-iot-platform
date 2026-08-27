package com.example.platform.ingest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.ingest.entity.IngestDedup;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/*
 * 类功能:上报幂等去重数据访问接口，继承BaseMapper并附窗口清理SQL
 * */
@Mapper
public interface IngestDedupMapper extends BaseMapper<IngestDedup> {

    /*
     * 方法功能:删除窗口之前的过期去重键
     * 参数:before-过期时间边界
     * 返回数据:int-删除行数
     * */
    @Delete("DELETE FROM ingest_dedup WHERE create_time < #{before}")
    int deleteBefore(@Param("before") LocalDateTime before);
}
