# 注册与组织审批后端实施计划

> **面向执行 Agent：** 必须在 `AUTH-CODEX-01/02/03` 基础包合入 `origin/main` 后执行。Open 与 Trae 只能修改各自任务文件；共享迁移、安全过滤器、`ResultCode` 和成员关系不变量服务由 CodeX 持有。

**目标：** 实现邮箱验证码注册、首次平台管理员初始化、平台注册策略、组织创建申请、组织加入审批和成员管理。

**架构：** Open 持有邮件验证、全局账号注册、平台初始化和组织创建审批；Trae 持有组织加入申请与成员管理。两者共同依赖 CodeX 已冻结的 `OrganizationMembershipService`，不得复制管理员独占校验。

**技术栈：** Spring Mail、QQ SMTP、Redis `CacheService`、Spring Security、MyBatis-Plus、JUnit 5、Mockito。

## 全局约束

- 公开注册创建全局账号，不自动创建组织或成员关系。
- 验证码 6 位数字，TTL 5 分钟，最多失败 5 次。
- 同 IP 60 秒最多发送 1 封，同邮箱 60 秒最多发送 1 封。
- 同邮箱每小时最多 10 封，同 IP 每小时最多 30 封。
- 验证码只在 Redis 保存 HMAC 摘要，日志不得出现明文验证码、密码、SMTP 授权码或 Token。
- QQ SMTP 授权码和 HMAC 密钥只从环境变量读取。
- `organizationRegistrationMode` 为 `CLOSED/REVIEW/OPEN`；支付、套餐和订阅不在本轮实现。

---

### Task 1（Open）：实现邮箱验证码服务

**Files:**
- Create: `src/main/java/com/example/platform/service/EmailVerificationService.java`
- Create: `src/main/java/com/example/platform/service/impl/EmailVerificationServiceImpl.java`
- Create: `src/main/java/com/example/platform/service/VerificationMailSender.java`
- Create: `src/main/java/com/example/platform/service/impl/QqVerificationMailSender.java`
- Create: `src/main/java/com/example/platform/common/constant/VerificationRateLimitScript.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/resources/application-dev.yaml`
- Modify: `.env.example`
- Test: `src/test/java/com/example/platform/service/impl/EmailVerificationServiceImplTest.java`

**Interfaces:**
- `void sendCode(VerificationPurpose purpose, String email, String ip)`。
- `void verifyAndConsume(VerificationPurpose purpose, String email, String code)`。
- `VerificationPurpose`: `REGISTRATION`、`PLATFORM_SETUP`、`PASSWORD_RESET`。

- [ ] **Step 1: 写 TTL、冷却、失败次数和邮件失败测试**

```java
@Test
void sixthInvalidAttemptIsRejectedAsExhausted() {
    when(cacheService.executeLua(anyString(), eq(List.class), anyList(), any()))
            .thenReturn(List.of(2L, 0L));
    assertThatThrownBy(() -> service.verifyAndConsume(
            VerificationPurpose.REGISTRATION, "user@example.com", "000000"))
            .hasMessageContaining("验证码尝试次数已耗尽");
}

@Test
void mailFailureKeepsCooldownButRemovesPendingDigest() {
    doThrow(new MailSendException("smtp unavailable"))
            .when(mailSender).send(anyString(), anyString());
    assertThatThrownBy(() -> service.sendCode(
            VerificationPurpose.REGISTRATION, "user@example.com", "127.0.0.1"));
    verify(cacheService).delete(startsWith("verify:pending:"));
    verify(cacheService, never()).delete(startsWith("verify:cooldown:"));
}
```

- [ ] **Step 2: 使用 Lua 原子执行配额与冷却**

```java
List<Long> result = cacheService.executeLua(
        VerificationRateLimitScript.ACQUIRE_SEND_PERMISSION,
        List.class,
        List.of(ipCooldownKey, emailCooldownKey, ipHourlyKey, emailHourlyKey),
        60, 3600, 30, 10);
```

- [ ] **Step 3: 配置 QQ SMTP**

```yaml
spring:
  mail:
    host: ${MAIL_HOST:smtp.qq.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_AUTH_CODE:}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
verification:
  hmac-secret: ${VERIFICATION_CODE_HMAC_SECRET:}
  code-ttl-seconds: 300
```

- [ ] **Step 4: 运行验证码测试并提交**

Run: `.\mvnw.cmd -Dtest=EmailVerificationServiceImplTest test`

Expected: PASS。

```powershell
git add src/main/java/com/example/platform/service src/main/java/com/example/platform/service/impl src/main/java/com/example/platform/common/constant src/main/resources .env.example src/test/java/com/example/platform/service/impl/EmailVerificationServiceImplTest.java
git commit -m "feat(auth): add secure email verification"
```

### Task 2（Open）：实现普通账号注册和邮箱/用户名登录

**Files:**
- Create: `src/main/java/com/example/platform/dto/RegistrationCodeDTO.java`
- Create: `src/main/java/com/example/platform/dto/RegisterDTO.java`
- Modify: `src/main/java/com/example/platform/dto/LoginDTO.java`
- Modify: `src/main/java/com/example/platform/service/AuthService.java`
- Modify: `src/main/java/com/example/platform/service/impl/AuthServiceImpl.java`
- Modify: `src/main/java/com/example/platform/controller/AuthController.java`
- Modify: `src/main/java/com/example/platform/service/SysUserService.java`
- Modify: `src/main/java/com/example/platform/service/impl/SysUserServiceImpl.java`
- Test: `src/test/java/com/example/platform/service/impl/PublicRegistrationServiceTest.java`
- Test: `src/test/java/com/example/platform/service/impl/AuthServiceImplTest.java`

**Interfaces:**
- `POST /auth/registration-codes`。
- `POST /auth/register`。
- Login field renamed from `username` to `identifier`，接受邮箱或全局唯一用户名。

- [ ] **Step 1: 写注册关闭、重复身份和双标识登录测试**

```java
@Test
void publicRegistrationCreatesVerifiedGlobalAccountWithoutMembership() {
    RegisterDTO dto = register("user@example.com", "normal_user");
    service.register(dto, "127.0.0.1");
    verify(userService).createRegisteredUser(argThat(user ->
            user.getEmailVerified() && user.getPlatformRole() == null));
    verifyNoInteractions(membershipService);
}
```

- [ ] **Step 2: 实现注册事务**

```java
@Transactional(rollbackFor = Exception.class)
public void register(RegisterDTO dto, String ip) {
    settingsService.requireAccountRegistrationEnabled();
    String email = identityNormalizer.normalizeEmail(dto.getEmail());
    identityUniquenessService.requireAvailable(email, dto.getUsername().trim());
    verificationService.verifyAndConsume(VerificationPurpose.REGISTRATION,
            email, dto.getVerificationCode());
    userService.createRegisteredUser(toGlobalUser(dto, email));
}
```

- [ ] **Step 3: 修改登录查询**

`identifier` 包含 `@` 时按规范化邮箱查找，否则按全局用户名查找；失败提示保持统一“账号或密码错误”，不得暴露账号存在性。

- [ ] **Step 4: 运行测试并提交**

Run: `.\mvnw.cmd -Dtest=PublicRegistrationServiceTest,AuthServiceImplTest test`

Expected: PASS。

```powershell
git add src/main/java/com/example/platform/dto src/main/java/com/example/platform/service src/main/java/com/example/platform/service/impl/AuthServiceImpl.java src/main/java/com/example/platform/service/impl/SysUserServiceImpl.java src/main/java/com/example/platform/controller/AuthController.java src/test/java/com/example/platform/service/impl
git commit -m "feat(auth): register and login global accounts"
```

### Task 3（Open）：实现一次性平台管理员初始化

**Files:**
- Create: `src/main/java/com/example/platform/controller/PlatformSetupController.java`
- Create: `src/main/java/com/example/platform/service/PlatformSetupService.java`
- Create: `src/main/java/com/example/platform/service/impl/PlatformSetupServiceImpl.java`
- Create: `src/main/java/com/example/platform/dto/PlatformSetupDTO.java`
- Test: `src/test/java/com/example/platform/service/impl/PlatformSetupServiceImplTest.java`

**Interfaces:**
- `GET /setup/status`。
- `POST /setup/verification-codes`。
- `POST /setup/platform-admin`。

- [ ] **Step 1: 写并发初始化拒绝测试**

```java
@Test
void setupFailsClosedWhenPlatformAdminAlreadyExists() {
    when(settingsMapper.selectSingletonForUpdate()).thenReturn(settings());
    when(userMapper.countPlatformAdmins()).thenReturn(1L);
    assertThatThrownBy(() -> service.initialize(dto(), "127.0.0.1"))
            .hasMessageContaining("平台管理员已初始化");
}
```

- [ ] **Step 2: 实现单例行锁与常量时间 Token 比较**

```java
@Transactional(rollbackFor = Exception.class)
public void initialize(PlatformSetupDTO dto, String ip) {
    settingsMapper.selectSingletonForUpdate();
    if (userMapper.countPlatformAdmins() > 0) {
        throw new BusinessException(ResultCode.SETUP_ALREADY_COMPLETED);
    }
    bootstrapTokenVerifier.requireValid(dto.getBootstrapToken());
    verificationService.verifyAndConsume(VerificationPurpose.PLATFORM_SETUP,
            dto.getEmail(), dto.getVerificationCode());
    userService.createPlatformAdministrator(dto);
}
```

- [ ] **Step 3: 运行测试并提交**

Run: `.\mvnw.cmd -Dtest=PlatformSetupServiceImplTest test`

Expected: PASS，两个并发请求最多一个成功。

```powershell
git add src/main/java/com/example/platform/controller/PlatformSetupController.java src/main/java/com/example/platform/service/PlatformSetupService.java src/main/java/com/example/platform/service/impl/PlatformSetupServiceImpl.java src/main/java/com/example/platform/dto/PlatformSetupDTO.java src/test/java/com/example/platform/service/impl/PlatformSetupServiceImplTest.java
git commit -m "feat(auth): add one-time platform setup"
```

### Task 4（Open）：实现平台注册策略和组织创建申请

**Files:**
- Create: `src/main/java/com/example/platform/controller/PlatformRegistrationSettingsController.java`
- Create: `src/main/java/com/example/platform/controller/OrganizationCreationApplicationController.java`
- Create: `src/main/java/com/example/platform/service/OrganizationProvisioningService.java`
- Create: `src/main/java/com/example/platform/service/OrganizationCreationApplicationService.java`
- Create: `src/main/java/com/example/platform/service/PlatformRegistrationSettingsService.java`
- Create: `src/main/java/com/example/platform/service/impl/OrganizationProvisioningServiceImpl.java`
- Create: `src/main/java/com/example/platform/service/impl/OrganizationCreationApplicationServiceImpl.java`
- Create: `src/main/java/com/example/platform/service/impl/PlatformRegistrationSettingsServiceImpl.java`
- Create: `src/main/java/com/example/platform/entity/OrganizationCreationApplication.java`
- Create: `src/main/java/com/example/platform/mapper/OrganizationCreationApplicationMapper.java`
- Create: `src/main/java/com/example/platform/dto/OrganizationCreationApplicationDTO.java`
- Create: `src/main/java/com/example/platform/dto/OrganizationApplicationReviewDTO.java`
- Create: `src/main/java/com/example/platform/dto/PlatformRegistrationSettingsDTO.java`
- Create: `src/main/java/com/example/platform/dto/OrganizationCreationApplicationVO.java`
- Test: `src/test/java/com/example/platform/service/impl/OrganizationCreationApplicationServiceImplTest.java`

**Interfaces:**
- `GET/PUT /platform/registration-settings`。
- `POST /organization-applications`、`GET /organization-applications/mine`。
- `GET /platform/organization-applications`、approve、reject。
- `OrganizationProvisioningService.provision(applicationId, reviewerId)` 是审核和 OPEN 模式共用入口。

- [ ] **Step 1: 写三态策略和回滚测试**

```java
@Test
void reviewApprovalCreatesOrganizationAdminMembershipAtomically() {
    service.approve(31L, 1L, "approved");
    verify(organizationMapper).insert(argThat(org -> org.getId() != null));
    verify(membershipService).activateMembership(
            eq(7L), anyLong(), eq("ORG_ADMIN"), eq(1L));
    verify(applicationMapper).markApproved(eq(31L), anyLong(), eq(1L), any());
}
```

- [ ] **Step 2: 实现统一 provisioning 事务**

申请提交和审批时都校验申请人没有任何活动成员关系；`OPEN` 模式直接调用同一 provisioning 服务，不复制建组织逻辑。

- [ ] **Step 3: 运行测试并提交 Open 工作包**

Run: `.\mvnw.cmd -Dtest=OrganizationCreationApplicationServiceImplTest test`

Expected: PASS，`CLOSED/REVIEW/OPEN`、重复名称/编码、审批回滚均覆盖。

```powershell
git add src/main/java/com/example/platform/controller src/main/java/com/example/platform/service src/main/java/com/example/platform/service/impl src/main/java/com/example/platform/entity src/main/java/com/example/platform/mapper src/main/java/com/example/platform/dto src/test/java/com/example/platform/service/impl/OrganizationCreationApplicationServiceImplTest.java
git commit -m "feat(org): add organization registration review"
```

### Task 5（Trae，与 Open Task 1-4 并行）：实现组织发现与加入申请

**Files:**
- Create: `src/main/java/com/example/platform/controller/OrganizationJoinApplicationController.java`
- Create: `src/main/java/com/example/platform/service/OrganizationJoinApplicationService.java`
- Create: `src/main/java/com/example/platform/service/impl/OrganizationJoinApplicationServiceImpl.java`
- Create: `src/main/java/com/example/platform/entity/OrganizationJoinApplication.java`
- Create: `src/main/java/com/example/platform/mapper/OrganizationJoinApplicationMapper.java`
- Create: `src/main/java/com/example/platform/dto/OrganizationJoinApplicationDTO.java`
- Create: `src/main/java/com/example/platform/dto/OrganizationJoinReviewDTO.java`
- Create: `src/main/java/com/example/platform/dto/OrganizationJoinApplicationVO.java`
- Create: `src/main/java/com/example/platform/dto/PublicOrganizationVO.java`
- Modify: `src/main/java/com/example/platform/controller/OrganizationController.java` only for minimal public search endpoint
- Test: `src/test/java/com/example/platform/service/impl/OrganizationJoinApplicationServiceImplTest.java`

**Interfaces:**
- `GET /organizations/public` 只返回 ID、名称、编码和是否开放申请。
- `POST /organizations/{id}/join-applications`。
- `GET /organizations/{id}/join-applications`。
- approve、reject endpoints。

- [ ] **Step 1: 写申请开关、重复申请和管理员禁止申请测试**

```java
@Test
void activeOrgAdminCannotSubmitAnotherJoinApplication() {
    when(membershipMapper.existsActiveOrgAdminMembership(7L)).thenReturn(true);
    assertThatThrownBy(() -> service.submit(7L, 20L, "request"))
            .hasMessageContaining("组织管理员不能加入其他组织");
}
```

- [ ] **Step 2: 实现提交与审批**

审批人必须是目标组织当前 `ORG_ADMIN`。审批为 `OPERATOR/VIEWER` 时允许申请人已有普通成员关系；审批为 `ORG_ADMIN` 时必须通过共享 `OrganizationMembershipService` 的独占校验。

- [ ] **Step 3: 运行测试并提交**

Run: `.\mvnw.cmd -Dtest=OrganizationJoinApplicationServiceImplTest test`

Expected: PASS。

```powershell
git add src/main/java/com/example/platform/controller/OrganizationJoinApplicationController.java src/main/java/com/example/platform/controller/OrganizationController.java src/main/java/com/example/platform/service src/main/java/com/example/platform/service/impl/OrganizationJoinApplicationServiceImpl.java src/main/java/com/example/platform/entity src/main/java/com/example/platform/mapper src/main/java/com/example/platform/dto src/test/java/com/example/platform/service/impl/OrganizationJoinApplicationServiceImplTest.java
git commit -m "feat(org): add interactive join applications"
```

### Task 6（Trae）：实现组织成员管理和管理员交接

**Files:**
- Create: `src/main/java/com/example/platform/controller/OrganizationMemberController.java`
- Create: `src/main/java/com/example/platform/dto/MembershipRoleUpdateDTO.java`
- Create: `src/main/java/com/example/platform/dto/OrganizationMemberVO.java`
- Modify: `src/main/java/com/example/platform/service/OrganizationMembershipService.java` only through approved interface extension
- Test: `src/test/java/com/example/platform/controller/OrganizationMemberControllerTest.java`

**Interfaces:**
- `GET /organizations/{id}/members`。
- `PUT /organizations/{id}/members/{userId}/role`。
- `DELETE /organizations/{id}/members/{userId}`。
- `PUT /organizations/{id}/join-setting`。

- [ ] **Step 1: 写越权与最后管理员测试**

```java
@Test
void orgAdminCannotManageMembersOfAnotherOrganization() throws Exception {
    mockMvc.perform(put("/organizations/20/members/7/role")
            .header("X-Organization-Id", "10")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\"OPERATOR\"}"))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: 控制器只委托共享不变量服务**

```java
@PutMapping("/{organizationId}/members/{userId}/role")
@PreAuthorize("hasRole('ORG_ADMIN')")
public Result<Void> changeRole(@PathVariable Long organizationId,
                               @PathVariable Long userId,
                               @Valid @RequestBody MembershipRoleUpdateDTO dto) {
    currentOrganizationContext.requireMatches(organizationId);
    membershipService.changeRole(userId, organizationId, dto.getRole(),
            currentUserContext.requireCurrentUser().userId());
    return Result.success();
}
```

- [ ] **Step 3: 运行 Trae 工作包测试并提交**

Run: `.\mvnw.cmd -Dtest=OrganizationJoinApplicationServiceImplTest,OrganizationMemberControllerTest,OrganizationMembershipServiceImplTest test`

Expected: PASS。

```powershell
git add src/main/java/com/example/platform/controller/OrganizationMemberController.java src/main/java/com/example/platform/dto/MembershipRoleUpdateDTO.java src/main/java/com/example/platform/dto/OrganizationMemberVO.java src/test/java/com/example/platform/controller/OrganizationMemberControllerTest.java
git commit -m "feat(org): manage members and administrator handoff"
```

### Task 7（CodeX 门控）：集成 Open 与 Trae 后端工作包

**Files:**
- Review only: all changed backend files
- Modify when required by approved SCR: shared contracts only
- Create: `开发控制文档/08-门控证据/GATE-注册与组织后端.md` in local distribution package, never commit it

- [ ] **Step 1: 先合并 Open，再让 Trae 变基到新 main**

```powershell
git merge --no-ff feature/auth-open-registration
.\mvnw.cmd clean test
```

Expected: BUILD SUCCESS。

- [ ] **Step 2: 合并 Trae 并运行全量测试**

```powershell
git merge --no-ff feature/auth-trae-membership
.\mvnw.cmd clean test
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: 安全边界检查**

Run: `git diff 6b34b43..HEAD --name-only`

Expected: 只有 CodeX 基础包的 V7/V8；Open 与 Trae 不新增或修改迁移，不修改 JWT/过滤器或 `ResultCode`，除非存在已批准 SCR。

## 后端工作包合并门控

1. Open 分支：`feature/auth-open-registration`。
2. Trae 分支：`feature/auth-trae-membership`，必须基于已合入 Open 后的最新 main 或在合并前完成无冲突变基。
3. 邮件失败、验证码限流、首次初始化并发、审批回滚、管理员独占和最后管理员保护测试通过。
4. 审计日志中邮箱脱敏，验证码、密码、授权码和 Token 均不可见。
5. 不实现支付表、套餐表、订阅表或伪支付流程。
