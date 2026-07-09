package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.CloudRoom;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:云机房数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface CloudRoomMapper extends BaseMapper<CloudRoom> {
}
