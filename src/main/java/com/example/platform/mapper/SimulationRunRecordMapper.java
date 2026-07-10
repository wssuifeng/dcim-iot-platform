package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.SimulationRunRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟运行记录数据访问层
 */
@Mapper
public interface SimulationRunRecordMapper extends BaseMapper<SimulationRunRecord> {
}
