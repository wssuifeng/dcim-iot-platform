package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.SimulationSceneStep;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟场景步骤数据访问层
 */
@Mapper
public interface SimulationSceneStepMapper extends BaseMapper<SimulationSceneStep> {
}
