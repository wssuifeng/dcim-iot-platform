package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.OrganizationJoinApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrganizationJoinApplicationMapper extends BaseMapper<OrganizationJoinApplication> {
    @Select("SELECT * FROM organization_join_application WHERE id = #{id} FOR UPDATE")
    OrganizationJoinApplication selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM organization_join_application WHERE applicant_user_id = #{userId} AND organization_id = #{organizationId} AND status = 'PENDING'")
    long countPending(@Param("userId") Long userId, @Param("organizationId") Long organizationId);
}
