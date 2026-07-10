package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.SimulationScene;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟场景数据访问层
 */
@Mapper
public interface SimulationSceneMapper extends BaseMapper<SimulationScene> {
}
