package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.AlarmTemplate;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:告警模版数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface AlarmTemplateMapper extends BaseMapper<AlarmTemplate> {
}
