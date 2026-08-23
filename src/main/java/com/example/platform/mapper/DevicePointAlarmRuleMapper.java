package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.DevicePointAlarmRule;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:点位告警规则数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface DevicePointAlarmRuleMapper extends BaseMapper<DevicePointAlarmRule> {
}
