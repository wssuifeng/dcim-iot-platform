package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/*
 * 类功能:用户数据访问接口，提供登录认证所需的用户查询方法
 * */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /*
     * 方法功能:根据用户名查询用户信息（含密码、角色、状态、锁定信息），用于登录认证
     * 参数:username-用户名
     * 返回数据:SysUser-用户完整信息，未找到返回null
     * */
    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser findByUsername(@Param("username") String username);
}