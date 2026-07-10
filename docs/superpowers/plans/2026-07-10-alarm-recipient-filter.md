# Alarm Recipient Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Execute this plan task-by-task with test-first checkpoints.

**Goal:** Restrict alarm notifications and WebSocket summaries to enabled `ORG_ADMIN`, `OPERATOR`, and `VIEWER` users explicitly assigned to the alarm's organization and cloud room.

**Architecture:** Add a focused `AlarmRecipientResolver` that queries explicit room assignments and filters enabled business users. Resolve once in `AlarmEventServiceImpl`, then pass the same deduplicated user IDs to in-app notification persistence and per-user WebSocket delivery.

**Tech Stack:** Java 17, Spring Boot 4.0.6, MyBatis-Plus 3.5.16, JUnit 5, Mockito, AssertJ.

## Global Constraints

- Do not change database migrations or frontend files.
- Do not modify or commit `开发控制文档/`.
- Fail closed when `organizationId` or `cloudRoomId` is null.
- Only enabled `ORG_ADMIN`, `OPERATOR`, and `VIEWER` recipients are eligible.
- `ORG_ALL` alone is not an explicit room assignment and must not receive alarms.
- Alarm state transitions must not roll back when notification delivery fails.

---

### Task 1: Central Recipient Resolver

**Files:**
- Create: `src/main/java/com/example/platform/service/AlarmRecipientResolver.java`
- Create: `src/main/java/com/example/platform/service/impl/AlarmRecipientResolverImpl.java`
- Test: `src/test/java/com/example/platform/service/impl/AlarmRecipientResolverImplTest.java`

**Interfaces:**
- Produces: `Set<Long> resolveRecipientUserIds(Long organizationId, Long cloudRoomId)`.
- Consumes: `DataScopeAssignmentMapper` and `SysUserService`.

- [ ] **Step 1: Write failing resolver tests**

Cover null scope, exact organization/room selection, duplicate assignments, and user filtering for status and the three allowed roles.

- [ ] **Step 2: Verify tests fail**

Run: `mvn test -Dtest=AlarmRecipientResolverImplTest`

Expected: compilation failure because the resolver types do not exist.

- [ ] **Step 3: Implement the resolver**

Query assignments with exact `organizationId`, exact `cloudRoomId`, and `scopeType IN ('ROOMS', 'READ_ONLY')`. Deduplicate assignment user IDs, then query users with `id IN (...)`, `status = 0`, and `role IN ('ORG_ADMIN', 'OPERATOR', 'VIEWER')` and return their IDs.

- [ ] **Step 4: Verify resolver tests pass**

Run: `mvn test -Dtest=AlarmRecipientResolverImplTest`

Expected: all resolver tests pass.

### Task 2: Share Recipients Across Both Delivery Channels

**Files:**
- Modify: `src/main/java/com/example/platform/service/InAppNotificationService.java`
- Modify: `src/main/java/com/example/platform/service/impl/InAppNotificationServiceImpl.java`
- Modify: `src/main/java/com/example/platform/service/impl/AlarmEventServiceImpl.java`
- Test: `src/test/java/com/example/platform/service/impl/InAppNotificationServiceImplTest.java`
- Test: `src/test/java/com/example/platform/service/impl/AlarmEventServiceImplTest.java`

**Interfaces:**
- Consumes: `AlarmRecipientResolver.resolveRecipientUserIds(organizationId, cloudRoomId)`.
- Changes: `notifyAlarmEvent(Long alarmEventId, Set<Long> recipientUserIds, String title, String content)`.

- [ ] **Step 1: Write failing dispatch tests**

Assert that notification persistence uses only supplied recipient IDs. Assert that an alarm transition resolves recipients once and calls `sendSummary(userId, summary)` once for each returned user, never with `organizationId`.

- [ ] **Step 2: Verify tests fail**

Run: `mvn test -Dtest=InAppNotificationServiceImplTest,AlarmEventServiceImplTest`

Expected: compilation or verification failure against the old notification signature and organization-based WebSocket target.

- [ ] **Step 3: Implement shared dispatch**

Resolve recipients once in `AlarmEventServiceImpl.notifyAndPush`. Pass the set to the notification service, then loop over the same set for WebSocket delivery. Keep failures non-blocking and isolate WebSocket failures per recipient.

- [ ] **Step 4: Verify focused tests pass**

Run: `mvn test -Dtest=AlarmRecipientResolverImplTest,InAppNotificationServiceImplTest,AlarmEventServiceImplTest,UserScopedWebSocketPublisherTest`

Expected: all focused tests pass.

### Task 3: Regression Verification

**Files:**
- No additional files.

**Interfaces:**
- Verifies the existing Spring and MyBatis contracts compile with the changed notification signature.

- [ ] **Step 1: Run compilation**

Run: `mvn clean compile`

Expected: build success.

- [ ] **Step 2: Run the full test suite**

Run: `mvn test`

Expected: all tests pass.

- [ ] **Step 3: Check repository boundaries**

Run: `git diff --check` and `git status --short`

Expected: no whitespace errors, no generated output, and no changes under `开发控制文档/`.
