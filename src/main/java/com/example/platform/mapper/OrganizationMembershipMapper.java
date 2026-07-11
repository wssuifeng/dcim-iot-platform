package com.example.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.platform.entity.OrganizationMembership;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrganizationMembershipMapper extends BaseMapper<OrganizationMembership> {

    @Select("""
            SELECT * FROM organization_membership
            WHERE user_id = #{userId} AND organization_id = #{organizationId} AND status = 'ACTIVE'
            """)
    OrganizationMembership selectActiveByUserAndOrganization(
            @Param("userId") Long userId, @Param("organizationId") Long organizationId);

    @Select("""
            SELECT * FROM organization_membership
            WHERE user_id = #{userId} AND status = 'ACTIVE'
            ORDER BY organization_id
            """)
    List<OrganizationMembership> selectActiveByUser(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*) FROM organization_membership
            WHERE user_id = #{userId} AND status = 'ACTIVE' AND organization_id <> #{organizationId}
            """)
    long countOtherActiveMemberships(
            @Param("userId") Long userId, @Param("organizationId") Long organizationId);

    @Select("""
            SELECT COUNT(*) FROM organization_membership
            WHERE user_id = #{userId} AND status = 'ACTIVE' AND membership_role = 'ORG_ADMIN'
            """)
    long countActiveAdminMemberships(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*) FROM organization_membership
            WHERE organization_id = #{organizationId} AND status = 'ACTIVE' AND membership_role = 'ORG_ADMIN'
            FOR UPDATE
            """)
    long countActiveOrgAdminsForUpdate(@Param("organizationId") Long organizationId);

    @Select("""
            SELECT * FROM organization_membership
            WHERE user_id = #{userId} AND organization_id = #{organizationId}
            FOR UPDATE
            """)
    OrganizationMembership selectByUserAndOrganizationForUpdate(
            @Param("userId") Long userId, @Param("organizationId") Long organizationId);

    @Update("""
            UPDATE organization_membership
            SET membership_role = #{role}, approved_by = #{operatorId}, approved_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateRole(@Param("id") Long id, @Param("role") String role,
                   @Param("operatorId") Long operatorId);
}
