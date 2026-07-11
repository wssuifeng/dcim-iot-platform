package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Set;

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

    @Select("""
            SELECT * FROM sys_user
            WHERE username = #{identifier} OR email = #{identifier}
            LIMIT 1
            """)
    SysUser findByLoginIdentifier(@Param("identifier") String identifier);

    @Select("SELECT * FROM sys_user WHERE id = #{userId} FOR UPDATE")
    SysUser selectByIdForUpdate(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM sys_user WHERE platform_role = 'PLATFORM_ADMIN' AND status = 0")
    long countPlatformAdmins();

    @Select("""
            <script>
            SELECT u.*
            FROM sys_user u
            WHERE EXISTS (
                SELECT 1
                FROM sys_data_scope_assignment dsa
                WHERE dsa.user_id = u.id
                  AND dsa.deleted = 0
                  AND dsa.organization_id IN
                  <foreach collection="organizationIds" item="organizationId"
                           open="(" separator="," close=")">
                      #{organizationId}
                  </foreach>
            )
            <if test="keyword != null and keyword != ''">
                AND u.username LIKE CONCAT('%', #{keyword}, '%')
            </if>
            ORDER BY u.create_time DESC
            </script>
            """)
    Page<SysUser> selectPageByOrganizationScope(
            Page<SysUser> page,
            @Param("keyword") String keyword,
            @Param("organizationIds") Set<Long> organizationIds);
}
