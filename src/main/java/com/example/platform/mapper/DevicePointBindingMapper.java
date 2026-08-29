package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.DevicePointBinding;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:设备指标绑定数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface DevicePointBindingMapper extends BaseMapper<DevicePointBinding> {
}
