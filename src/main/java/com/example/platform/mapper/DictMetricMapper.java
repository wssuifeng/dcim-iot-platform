package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.DictMetric;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:指标字典数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface DictMetricMapper extends BaseMapper<DictMetric> {
}
