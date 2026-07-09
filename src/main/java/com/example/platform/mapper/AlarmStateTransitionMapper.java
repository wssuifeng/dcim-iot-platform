package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.AlarmStateTransition;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:告警状态流转记录数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface AlarmStateTransitionMapper extends BaseMapper<AlarmStateTransition> {
}
