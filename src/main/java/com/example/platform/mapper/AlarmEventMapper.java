package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.AlarmEvent;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:告警事件（权威状态机）数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface AlarmEventMapper extends BaseMapper<AlarmEvent> {
}
