package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.DataScopeAssignment;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:用户数据范围分配数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface DataScopeAssignmentMapper extends BaseMapper<DataScopeAssignment> {
}
