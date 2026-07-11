package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.OrganizationCreationApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrganizationCreationApplicationMapper
        extends BaseMapper<OrganizationCreationApplication> {
    @Select("SELECT * FROM organization_creation_application WHERE id = #{id} FOR UPDATE")
    OrganizationCreationApplication selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM organization_creation_application WHERE applicant_user_id = #{userId} AND status = 'PENDING'")
    long countPendingByApplicant(@Param("userId") Long userId);
}
