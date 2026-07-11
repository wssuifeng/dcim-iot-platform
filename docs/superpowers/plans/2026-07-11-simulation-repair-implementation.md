# Simulation Module Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the missing simulation schema and make simulation execution cancellable, asynchronous, and safe for device-less sleep events.

**Architecture:** Add an append-only V9 migration, retain `SimulationRunServiceImpl` as the command/query coordinator, and move step execution to a separate async executor service. Schedule only after the run-creation transaction commits and restore the selected organization context in the worker thread.

**Tech Stack:** Java 17, Spring Boot 4.0.6, Spring transactions and `@Async`, MyBatis-Plus 3.5.16, Flyway, MySQL 9.6, JUnit 5, Mockito.

## Global Constraints

- Do not modify V1-V8 or `flyway_schema_history` manually.
- Simulation writes remain disabled by default and forbidden in production.
- All device and point writes continue through `MetricSampleService`.
- Current organization data scope must be restored and cleared in async threads.
- Use tests before implementation and keep commits logically separated.

---

### Task 1: Repair the simulation schema

**Files:**
- Create: `src/main/resources/db/migration/V9__repair_simulation_model.sql`
- Create: `src/test/java/com/example/platform/migration/SimulationModelRepairMigrationContractTest.java`

- [ ] Write a migration contract test asserting V9 creates all four simulation tables, uses `IF NOT EXISTS`, and permits a nullable generated-data device for sleep events.
- [ ] Run the test and confirm it fails because V9 is missing.
- [ ] Add V9 without changing V1-V8.
- [ ] Run the migration contract test and existing migration tests.
- [ ] Commit as `fix(db): repair simulation model migration`.

### Task 2: Define asynchronous execution contracts

**Files:**
- Create: `src/main/java/com/example/platform/service/SimulationRunExecutionService.java`
- Create: `src/test/java/com/example/platform/service/impl/SimulationRunExecutionServiceImplTest.java`
- Modify: `src/test/java/com/example/platform/service/impl/SimulationRunServiceImplTest.java`

- [ ] Add failing tests for post-commit scheduling, immediate `PENDING` return, cancellation preservation, and device-less `SLEEP` tracing.
- [ ] Run the focused tests and confirm the new expectations fail.

### Task 3: Implement the background executor

**Files:**
- Create: `src/main/java/com/example/platform/service/impl/SimulationRunExecutionServiceImpl.java`
- Modify: `src/main/java/com/example/platform/service/impl/SimulationRunServiceImpl.java`
- Modify: `src/main/java/com/example/platform/entity/SimulationGeneratedDataRef.java`

- [ ] Persist the run and schedule execution after commit.
- [ ] Restore `CurrentOrganizationContext` in the async worker and clear it in `finally`.
- [ ] Poll cancellation before each step and after delays.
- [ ] Keep `CANCELLED` final and allow `SLEEP` references without a device.
- [ ] Run focused simulation tests.
- [ ] Commit as `fix(simulation): make runs cancellable and asynchronous`.

### Task 4: Verify Flyway and the complete application

**Files:**
- Modify only if a test exposes a defect in the files above.

- [ ] Run `./mvnw.cmd test` and expect zero failures and errors.
- [ ] Build the executable jar.
- [ ] Start a temporary application instance against the configured development database so Flyway applies V9.
- [ ] Query `flyway_schema_history` and `information_schema.tables` to confirm version 9 and all four tables.
- [ ] Confirm the repository worktree contains no secrets or generated logs.
- [ ] Update public control documents, push the branch, and report remaining runtime limitations.