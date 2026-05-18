package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.DeviceType;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:设备类型数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface DeviceTypeMapper extends BaseMapper<DeviceType> {
}
