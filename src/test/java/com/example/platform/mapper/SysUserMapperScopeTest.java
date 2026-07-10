package com.example.platform.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.platform.entity.DataScopeAssignment;
import com.example.platform.entity.Organization;
import com.example.platform.entity.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SysUserMapperScopeTest {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private OrganizationMapper organizationMapper;

    @Autowired
    private DataScopeAssignmentMapper dataScopeAssignmentMapper;

    @Test
    void organizationScopeQueryFiltersUsersAndDoesNotDuplicateMultipleAssignments() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Organization ownedOrganization = createOrganization("OWN-" + suffix);
        Organization otherOrganization = createOrganization("OTHER-" + suffix);
        SysUser ownedUser = createUser("scope_owned_" + suffix);
        SysUser otherUser = createUser("scope_other_" + suffix);

        assign(ownedUser.getId(), ownedOrganization.getId(), "ORG_ALL");
        assign(ownedUser.getId(), ownedOrganization.getId(), "READ_ONLY");
        assign(otherUser.getId(), otherOrganization.getId(), "ORG_ALL");

        Page<SysUser> result = sysUserMapper.selectPageByOrganizationScope(
                new Page<>(1, 10), "scope_", Set.of(ownedOrganization.getId()));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords())
                .extracting(SysUser::getId)
                .containsExactly(ownedUser.getId());
    }

    private Organization createOrganization(String code) {
        Organization organization = new Organization();
        organization.setOrgCode(code);
        organization.setOrgName(code);
        organization.setStatus(0);
        assertThat(organizationMapper.insert(organization)).isEqualTo(1);
        return organization;
    }

    private SysUser createUser(String username) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword("$2a$10$testEncodedPasswordForMapperScopeTest0000000000000000");
        user.setNickname(username);
        user.setRole("OPERATOR");
        user.setStatus(0);
        assertThat(sysUserMapper.insert(user)).isEqualTo(1);
        return user;
    }

    private void assign(Long userId, Long organizationId, String scopeType) {
        DataScopeAssignment assignment = new DataScopeAssignment();
        assignment.setUserId(userId);
        assignment.setOrganizationId(organizationId);
        assignment.setScopeType(scopeType);
        assertThat(dataScopeAssignmentMapper.insert(assignment)).isEqualTo(1);
    }
}
