package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.DeviceTypePointTemplate;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:设备类型指标模板数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface DeviceTypePointTemplateMapper extends BaseMapper<DeviceTypePointTemplate> {
}
