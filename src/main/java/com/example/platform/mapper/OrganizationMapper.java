package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.Organization;
import org.apache.ibatis.annotations.Mapper;

/*
 * 类功能:组织数据访问接口，继承MyBatis-Plus BaseMapper提供基础CRUD
 * */
@Mapper
public interface OrganizationMapper extends BaseMapper<Organization> {
}
