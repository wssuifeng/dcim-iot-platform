# 注册与多组织身份基础实施计划

> **面向执行 Agent：** 必须按任务逐项执行，采用测试先行；每个任务独立提交，完成门控前不得把后续业务任务分发给 Open 或 Trae。

**目标：** 将现有“用户表单一角色 + 单组织归属”升级为“全局账号 + 平台角色 + 组织成员关系”，提供后续注册和审批流程依赖的稳定数据与鉴权契约。

**架构：** JWT 只承载全局账号和可选平台角色；组织角色由活动成员关系按当前 `X-Organization-Id` 动态解析。现有 `sys_user.role`、`sys_user.organization_id` 只用于迁移兼容，完成回填后不再作为授权事实来源。

**技术栈：** Spring Boot 4.0.6、Spring Security、MyBatis-Plus 3.5.16、MySQL 9.6、Flyway、JUnit 5、Mockito。

## 全局约束

- 基线：`main@6b34b43`，开始编码前先确保该提交已推送到 `origin/main`。
- 迁移只新增 `V7__registration_identity_and_settings.sql` 和 `V8__organization_membership_and_applications.sql`，禁止修改 `V1` 至 `V6`。
- `email` 与 `username` 全局唯一；邮箱统一 `trim().toLowerCase(Locale.ROOT)`。
- 普通成员可以加入多个组织；活动 `ORG_ADMIN` 不得在其他组织持有任何活动成员关系。
- 所有成员新增、角色提升、退出和移除必须锁定目标 `sys_user` 行并在同一事务内校验。
- 最后一名活动组织管理员不得退出、移除或降级。
- `PLATFORM_ADMIN` 不默认拥有任何组织业务数据权限。
- 任何业务请求不得根据客户端提交的角色决定权限。

---

### Task 1：创建 V7 身份与平台注册设置迁移

**Files:**
- Create: `src/main/resources/db/migration/V7__registration_identity_and_settings.sql`
- Test: `src/test/java/com/example/platform/migration/RegistrationIdentityMigrationContractTest.java`

**Interfaces:**
- Produces: `sys_user.email`、`email_verified`、`email_verified_time`、`platform_role`。
- Produces: 单例表 `platform_registration_settings`。
- Produces: `sys_organization.join_application_enabled` 与组织名称唯一约束。

- [ ] **Step 1: 写迁移文本契约测试**

```java
@Test
void v7MustAddIdentitySettingsAndOrganizationConstraints() throws Exception {
    String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V7__registration_identity_and_settings.sql"));
    assertThat(sql).contains("ADD COLUMN email VARCHAR(254)")
            .contains("ADD COLUMN platform_role VARCHAR(20)")
            .contains("CREATE TABLE platform_registration_settings")
            .contains("ADD COLUMN join_application_enabled")
            .contains("UNIQUE KEY uk_org_name (org_name)");
}
```

- [ ] **Step 2: 运行失败测试**

Run: `.\mvnw.cmd -Dtest=RegistrationIdentityMigrationContractTest test`

Expected: FAIL，因为 `V7__registration_identity_and_settings.sql` 尚不存在。

- [ ] **Step 3: 创建 V7 迁移**

```sql
ALTER TABLE sys_user
    ADD COLUMN email VARCHAR(254) NULL COMMENT '规范化后的全局唯一邮箱' AFTER username,
    ADD COLUMN email_verified TINYINT NOT NULL DEFAULT 0 AFTER email,
    ADD COLUMN email_verified_time DATETIME NULL AFTER email_verified,
    ADD COLUMN platform_role VARCHAR(20) NULL AFTER role,
    ADD UNIQUE KEY uk_user_email (email);

ALTER TABLE sys_organization
    ADD COLUMN join_application_enabled TINYINT NOT NULL DEFAULT 1 AFTER status,
    ADD UNIQUE KEY uk_org_name (org_name);

CREATE TABLE platform_registration_settings (
    id TINYINT PRIMARY KEY,
    account_registration_enabled TINYINT NOT NULL DEFAULT 1,
    organization_registration_mode VARCHAR(20) NOT NULL DEFAULT 'REVIEW',
    updated_by BIGINT NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_registration_settings_singleton CHECK (id = 1),
    CONSTRAINT fk_registration_settings_user FOREIGN KEY (updated_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO platform_registration_settings
    (id, account_registration_enabled, organization_registration_mode)
VALUES (1, 1, 'REVIEW');

UPDATE sys_user
SET platform_role = 'PLATFORM_ADMIN'
WHERE role = 'PLATFORM_ADMIN';
```

- [ ] **Step 4: 运行迁移契约和上下文测试**

Run: `.\mvnw.cmd -Dtest=RegistrationIdentityMigrationContractTest test`

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add src/main/resources/db/migration/V7__registration_identity_and_settings.sql src/test/java/com/example/platform/migration/RegistrationIdentityMigrationContractTest.java
git commit -m "feat(auth): add global identity registration migration"
```

### Task 2：创建 V8 成员关系与申请迁移

**Files:**
- Create: `src/main/resources/db/migration/V8__organization_membership_and_applications.sql`
- Test: `src/test/java/com/example/platform/migration/OrganizationMembershipMigrationContractTest.java`

**Interfaces:**
- Produces: `organization_membership(user_id, organization_id, membership_role, status)`。
- Produces: `organization_creation_application` 和 `organization_join_application`。
- Backfill: 将旧组织用户转换为活动成员关系，将旧范围记录保留为机房范围来源。

- [ ] **Step 1: 写迁移契约测试**

```java
@Test
void v8MustCreateMembershipApplicationsAndBackfill() throws Exception {
    String sql = Files.readString(Path.of(
            "src/main/resources/db/migration/V8__organization_membership_and_applications.sql"));
    assertThat(sql).contains("CREATE TABLE organization_membership")
            .contains("UNIQUE KEY uk_membership_user_org (user_id, organization_id)")
            .contains("CREATE TABLE organization_creation_application")
            .contains("CREATE TABLE organization_join_application")
            .contains("INSERT INTO organization_membership");
}
```

- [ ] **Step 2: 运行失败测试**

Run: `.\mvnw.cmd -Dtest=OrganizationMembershipMigrationContractTest test`

Expected: FAIL，因为 V8 尚不存在。

- [ ] **Step 3: 创建 V8 核心表和兼容回填**

```sql
CREATE TABLE organization_membership (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    membership_role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    approved_by BIGINT NULL,
    approved_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_membership_user_org (user_id, organization_id),
    KEY idx_membership_org_status_role (organization_id, status, membership_role),
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_membership_org FOREIGN KEY (organization_id) REFERENCES sys_organization(id),
    CONSTRAINT fk_membership_approver FOREIGN KEY (approved_by) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE organization_creation_application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    applicant_user_id BIGINT NOT NULL,
    proposed_org_name VARCHAR(100) NOT NULL,
    proposed_org_code VARCHAR(50) NOT NULL,
    description VARCHAR(200) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewer_user_id BIGINT NULL,
    review_note VARCHAR(500) NULL,
    review_time DATETIME NULL,
    resulting_organization_id BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_org_creation_applicant_status (applicant_user_id, status),
    KEY idx_org_creation_status_time (status, create_time),
    CONSTRAINT fk_org_creation_applicant FOREIGN KEY (applicant_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_org_creation_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_org_creation_result FOREIGN KEY (resulting_organization_id) REFERENCES sys_organization(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE organization_join_application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    applicant_user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    request_note VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    assigned_membership_role VARCHAR(20) NULL,
    reviewer_user_id BIGINT NULL,
    review_note VARCHAR(500) NULL,
    review_time DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_org_join_applicant_org_status (applicant_user_id, organization_id, status),
    KEY idx_org_join_org_status_time (organization_id, status, create_time),
    CONSTRAINT fk_org_join_applicant FOREIGN KEY (applicant_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_org_join_org FOREIGN KEY (organization_id) REFERENCES sys_organization(id),
    CONSTRAINT fk_org_join_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO organization_membership
    (user_id, organization_id, membership_role, status, approved_time)
SELECT id, organization_id,
       CASE WHEN role = 'ADMIN' THEN 'ORG_ADMIN' ELSE role END,
       'ACTIVE', CURRENT_TIMESTAMP
FROM sys_user
WHERE organization_id IS NOT NULL
  AND role IN ('ADMIN', 'ORG_ADMIN', 'OPERATOR', 'VIEWER');
```

Service 层只写入 `PENDING/APPROVED/REJECTED/CANCELLED`；`assigned_membership_role` 只允许 `ORG_ADMIN/OPERATOR/VIEWER`。MySQL 迁移集成测试还必须验证重复待处理申请会被服务层事务拒绝。

- [ ] **Step 4: 运行迁移契约测试**

Run: `.\mvnw.cmd -Dtest=OrganizationMembershipMigrationContractTest test`

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add src/main/resources/db/migration/V8__organization_membership_and_applications.sql src/test/java/com/example/platform/migration/OrganizationMembershipMigrationContractTest.java
git commit -m "feat(auth): add organization membership schema"
```

### Task 3：建立全局账号、成员关系和平台注册设置实体

**Files:**
- Modify: `src/main/java/com/example/platform/entity/SysUser.java`
- Modify: `src/main/java/com/example/platform/mapper/SysUserMapper.java`
- Create: `src/main/java/com/example/platform/entity/OrganizationMembership.java`
- Create: `src/main/java/com/example/platform/entity/PlatformRegistrationSettings.java`
- Create: `src/main/java/com/example/platform/mapper/OrganizationMembershipMapper.java`
- Create: `src/main/java/com/example/platform/mapper/PlatformRegistrationSettingsMapper.java`
- Test: `src/test/java/com/example/platform/entity/GlobalIdentityContractTest.java`

**Interfaces:**
- Produces: `SysUserMapper.findByLoginIdentifier(String identifier)`。
- Produces: `SysUserMapper.selectByIdForUpdate(Long userId)`。
- Produces: `OrganizationMembershipMapper.selectActiveByUserAndOrganization(...)`。

- [ ] **Step 1: 写全局身份实体测试**

```java
@Test
void sysUserExposesGlobalIdentityInsteadOfOrganizationRoleAuthority() {
    SysUser user = new SysUser();
    user.setEmail("user@example.com");
    user.setEmailVerified(true);
    user.setPlatformRole(null);
    assertThat(user.getEmail()).isEqualTo("user@example.com");
    assertThat(user.getEmailVerified()).isTrue();
    assertThat(user.getPlatformRole()).isNull();
}
```

- [ ] **Step 2: 实现实体和锁查询签名**

```java
@Select("SELECT * FROM sys_user WHERE id = #{userId} FOR UPDATE")
SysUser selectByIdForUpdate(@Param("userId") Long userId);

@Select("""
    SELECT * FROM sys_user
    WHERE username = #{identifier} OR email = #{identifier}
    LIMIT 1
    """)
SysUser findByLoginIdentifier(@Param("identifier") String identifier);
```

`OrganizationMembership` 字段固定为 `id/userId/organizationId/membershipRole/status/approvedBy/approvedTime/createTime/updateTime`。

- [ ] **Step 3: 运行实体和 Mapper 测试**

Run: `.\mvnw.cmd -Dtest=GlobalIdentityContractTest,SysUserMapperScopeTest test`

Expected: PASS。

- [ ] **Step 4: 提交**

```powershell
git add src/main/java/com/example/platform/entity src/main/java/com/example/platform/mapper src/test/java/com/example/platform/entity/GlobalIdentityContractTest.java
git commit -m "feat(auth): model global accounts and memberships"
```

### Task 4：实现成员关系不变量服务

**Files:**
- Create: `src/main/java/com/example/platform/service/OrganizationMembershipService.java`
- Create: `src/main/java/com/example/platform/service/impl/OrganizationMembershipServiceImpl.java`
- Create: `src/main/java/com/example/platform/common/exception/MembershipConflictException.java`
- Test: `src/test/java/com/example/platform/service/impl/OrganizationMembershipServiceImplTest.java`

**Interfaces:**
- Produces: `activateMembership(Long userId, Long organizationId, String role, Long approverId)`。
- Produces: `changeRole(Long userId, Long organizationId, String role, Long operatorId)`。
- Produces: `removeMembership(Long userId, Long organizationId, Long operatorId)`。
- Produces: `listActiveMemberships(Long userId)`。

- [ ] **Step 1: 写管理员独占和最后管理员保护测试**

```java
@Test
void promotingToOrgAdminRejectsAnyOtherActiveMembership() {
    when(userMapper.selectByIdForUpdate(7L)).thenReturn(user(7L));
    when(membershipMapper.countOtherActiveMemberships(7L, 10L)).thenReturn(1L);
    assertThatThrownBy(() -> service.changeRole(7L, 10L, "ORG_ADMIN", 1L))
            .isInstanceOf(MembershipConflictException.class);
}

@Test
void removingLastOrgAdminIsRejected() {
    when(membershipMapper.countActiveOrgAdminsForUpdate(10L)).thenReturn(1L);
    assertThatThrownBy(() -> service.removeMembership(7L, 10L, 1L))
            .isInstanceOf(MembershipConflictException.class);
}
```

- [ ] **Step 2: 实现事务边界**

```java
@Transactional(rollbackFor = Exception.class)
public void changeRole(Long userId, Long organizationId, String role, Long operatorId) {
    sysUserMapper.selectByIdForUpdate(userId);
    OrganizationMembership membership = requireMembershipForUpdate(userId, organizationId);
    if ("ORG_ADMIN".equals(role)
            && membershipMapper.countOtherActiveMemberships(userId, organizationId) > 0) {
        throw new MembershipConflictException("组织管理员不能同时属于其他组织");
    }
    if ("ORG_ADMIN".equals(membership.getMembershipRole())
            && !"ORG_ADMIN".equals(role)
            && membershipMapper.countActiveOrgAdminsForUpdate(organizationId) <= 1) {
        throw new MembershipConflictException("必须先完成组织管理员交接");
    }
    membershipMapper.updateRole(membership.getId(), role, operatorId);
}
```

- [ ] **Step 3: 运行并发不变量单元测试**

Run: `.\mvnw.cmd -Dtest=OrganizationMembershipServiceImplTest test`

Expected: PASS，覆盖新增、提升、降级、退出、移除和重复审批。

- [ ] **Step 4: 提交**

```powershell
git add src/main/java/com/example/platform/service/OrganizationMembershipService.java src/main/java/com/example/platform/service/impl/OrganizationMembershipServiceImpl.java src/main/java/com/example/platform/common/exception/MembershipConflictException.java src/test/java/com/example/platform/service/impl/OrganizationMembershipServiceImplTest.java
git commit -m "feat(auth): enforce organization membership invariants"
```

### Task 5：把 JWT 改为全局身份并引入当前组织上下文

**Files:**
- Modify: `src/main/java/com/example/platform/common/util/JwtUtil.java`
- Modify: `src/main/java/com/example/platform/security/JwtAuthenticationService.java`
- Modify: `src/main/java/com/example/platform/security/CurrentUser.java`
- Modify: `src/main/java/com/example/platform/security/CurrentUserContext.java`
- Create: `src/main/java/com/example/platform/security/CurrentOrganizationContext.java`
- Create: `src/main/java/com/example/platform/security/OrganizationContextFilter.java`
- Modify: `src/main/java/com/example/platform/config/SecurityConfig.java`
- Test: `src/test/java/com/example/platform/security/OrganizationContextFilterTest.java`

**Interfaces:**
- JWT claims: `sub`、`username`、可选 `platformRole`；禁止写入组织角色。
- Request header: `X-Organization-Id`。
- Produces: `CurrentOrganizationContext.requireOrganizationId()`。

- [ ] **Step 1: 写组织上下文过滤测试**

```java
@Test
void activeMembershipAddsRequestScopedOrganizationAuthority() throws Exception {
    when(membershipService.requireActiveMembership(7L, 10L))
            .thenReturn(membership(7L, 10L, "OPERATOR"));
    request.addHeader("X-Organization-Id", "10");
    filter.doFilter(request, response, chain);
    assertThat(capturedAuthentication().getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .contains("ROLE_OPERATOR");
}
```

- [ ] **Step 2: 实现两段认证**

```java
// JwtAuthenticationService 只授予全局身份和可选平台角色。
List<GrantedAuthority> authorities = new ArrayList<>();
authorities.add(new SimpleGrantedAuthority("ROLE_AUTHENTICATED_USER"));
if ("PLATFORM_ADMIN".equals(platformRole)) {
    authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
}
```

`OrganizationContextFilter` 在 JWT 过滤器后运行；没有组织头时不授予组织角色，有组织头时必须查到活动成员关系才授予 `ROLE_ORG_ADMIN/ROLE_OPERATOR/ROLE_VIEWER`。

- [ ] **Step 3: 公开端点和平台端点规则**

```java
auth.requestMatchers("/setup/**", "/auth/login", "/auth/register",
        "/auth/registration-codes").permitAll();
auth.requestMatchers("/platform/**").hasRole("PLATFORM_ADMIN");
auth.requestMatchers("/organization-applications/**", "/organizations/public")
        .hasRole("AUTHENTICATED_USER");
```

- [ ] **Step 4: 运行安全测试**

Run: `.\mvnw.cmd -Dtest=JwtAuthenticationServiceTest,JwtAuthenticationFilterTest,OrganizationContextFilterTest,SecurityConfigRoleContractTest test`

Expected: PASS；普通账号无组织头可访问账号与申请端点，但不能访问组织业务端点。

- [ ] **Step 5: 提交**

```powershell
git add src/main/java/com/example/platform/common/util/JwtUtil.java src/main/java/com/example/platform/security src/main/java/com/example/platform/config/SecurityConfig.java src/test/java/com/example/platform/security src/test/java/com/example/platform/config/SecurityConfigRoleContractTest.java
git commit -m "feat(auth): resolve roles from current organization"
```

### Task 6：按当前组织重构数据范围与 WebSocket 上下文

**Files:**
- Modify: `src/main/java/com/example/platform/service/DataScopeService.java`
- Modify: `src/main/java/com/example/platform/service/impl/DataScopeServiceImpl.java`
- Modify: `src/main/java/com/example/platform/dto/DataScopeContext.java`
- Modify: `src/main/java/com/example/platform/controller/CurrentScopeController.java`
- Modify: `src/main/java/com/example/platform/security/WebSocketAuthenticationInterceptor.java`
- Modify: `src/main/java/com/example/platform/service/DeviceScopeHelper.java`
- Modify: `src/main/java/com/example/platform/service/impl/AlarmEventServiceImpl.java`
- Modify: `src/main/java/com/example/platform/service/impl/CloudRoomServiceImpl.java`
- Modify: `src/main/java/com/example/platform/service/impl/DashboardAlarmStatsServiceImpl.java`
- Modify: `src/main/java/com/example/platform/service/impl/DeviceServiceImpl.java`
- Modify: `src/main/java/com/example/platform/service/impl/UserDataScopeServiceImpl.java`
- Modify: `src/main/java/com/example/platform/service/impl/SimulationSceneServiceImpl.java`
- Modify: `src/main/java/com/example/platform/service/impl/SimulationRunServiceImpl.java`
- Modify: `src/main/java/com/example/platform/service/impl/OrganizationServiceImpl.java`
- Test: `src/test/java/com/example/platform/service/impl/DataScopeServiceTest.java`
- Test: `src/test/java/com/example/platform/security/WebSocketAuthenticationInterceptorTest.java`

**Interfaces:**
- Replaces: `resolveDataScope(Long userId)`。
- Produces: `resolveDataScope(Long userId, Long organizationId)`。
- `DataScopeContext` 只包含当前 `organizationId`，不再把多个组织合并成一个请求范围。

- [ ] **Step 1: 写跨组织拒绝测试**

```java
@Test
void resolveScopeRejectsInactiveMembershipForSelectedOrganization() {
    when(membershipMapper.selectActiveByUserAndOrganization(7L, 20L)).thenReturn(null);
    assertThatThrownBy(() -> service.resolveDataScope(7L, 20L))
            .isInstanceOf(DataScopeViolationException.class);
}
```

- [ ] **Step 2: 修改服务签名和解析逻辑**

```java
public interface DataScopeService {
    DataScopeContext resolveDataScope(Long userId, Long organizationId);
    DataScopeContext resolvePlatformScope(Long userId);
}
```

`ORG_ADMIN` 返回当前组织 `ORG_ALL`；`OPERATOR/VIEWER` 只读取当前组织的 `sys_data_scope_assignment`；平台范围不得用于组织业务查询。

- [ ] **Step 3: WebSocket 要求当前组织**

WebSocket 握手或 STOMP CONNECT 必须提供 `organizationId`，服务端验证活动成员关系后再建立 Principal；订阅和推送按用户与当前组织双重隔离。

- [ ] **Step 4: 运行数据范围与 WebSocket 测试**

Run: `.\mvnw.cmd -Dtest=DataScopeServiceTest,CurrentUserContextTest,CurrentScopeControllerTest,WebSocketAuthenticationInterceptorTest,UserScopedWebSocketPublisherTest test`

Expected: PASS，且跨组织头、缺失组织头、停用成员关系均被拒绝。

- [ ] **Step 5: 提交**

```powershell
git add src/main/java/com/example/platform/service src/main/java/com/example/platform/service/impl/DataScopeServiceImpl.java src/main/java/com/example/platform/dto/DataScopeContext.java src/main/java/com/example/platform/controller/CurrentScopeController.java src/main/java/com/example/platform/security/WebSocketAuthenticationInterceptor.java src/test/java/com/example/platform
git commit -m "refactor(auth): scope business access to selected organization"
```

### Task 7：冻结共享错误契约并执行基础门控

**Files:**
- Modify: `src/main/java/com/example/platform/common/ResultCode.java`
- Modify: `src/main/java/com/example/platform/common/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/example/platform/common/RegistrationErrorContractTest.java`

**Interfaces:**
- Produces stable errors: `REGISTRATION_CLOSED`、`EMAIL_ALREADY_EXISTS`、`VERIFICATION_*`、`ORGANIZATION_CREATION_*`、`JOIN_APPLICATION_*`、`MEMBERSHIP_CONFLICT`、`LAST_ORG_ADMIN_REQUIRED`。

- [ ] **Step 1: 写稳定错误码测试**

```java
@Test
void registrationAndMembershipErrorsRemainStable() {
    assertThat(ResultCode.findStableError("MEMBERSHIP_CONFLICT")).isPresent();
    assertThat(ResultCode.findStableError("LAST_ORG_ADMIN_REQUIRED")).isPresent();
    assertThat(ResultCode.findStableError("REGISTRATION_CLOSED")).isPresent();
}
```

- [ ] **Step 2: 扩展错误契约并运行全量后端测试**

Run: `.\mvnw.cmd clean test`

Expected: BUILD SUCCESS；现有组织、设备、告警、模拟、通知与 WebSocket 测试全部通过。

- [ ] **Step 3: 检查迁移边界**

Run: `git diff origin/main --name-only -- src/main/resources/db/migration`

Expected: 只出现 V7 和 V8。

- [ ] **Step 4: 提交**

```powershell
git add src/main/java/com/example/platform/common src/test/java/com/example/platform/common
git commit -m "feat(auth): freeze registration error contracts"
```

## 基础包合并门控

1. CodeX 分支名固定为 `feature/auth-codex-foundation`。
2. `V7/V8` 可从已执行 V1-V6 的数据库继续迁移；不得重写历史 checksum。
3. `mvnw.cmd clean test` 通过。
4. JWT 不含组织角色；无 `X-Organization-Id` 时不能访问组织业务 API。
5. `ORG_ADMIN` 独占与最后管理员保护均有并发事务测试。
6. 合入 `origin/main` 后，Open 与 Trae 才能从新 main 创建各自新分支。
