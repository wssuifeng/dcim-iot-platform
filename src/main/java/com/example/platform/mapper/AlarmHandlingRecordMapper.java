package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.AlarmHandlingRecord;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:告警处理记录数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface AlarmHandlingRecordMapper extends BaseMapper<AlarmHandlingRecord> {
}
