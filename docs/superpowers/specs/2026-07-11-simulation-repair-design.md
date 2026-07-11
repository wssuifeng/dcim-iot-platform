# Simulation Module Repair Design

## Problem

Existing databases already record Flyway version 6 as `add notification log`, while the repository later reused version 6 for `V6__simulation_model.sql`. Flyway therefore skips the simulation schema and the application fails when querying `simulation_scene` and related tables. `validate-on-migrate=false` hides the conflict at startup.

## Approved Design

1. Add append-only `V9__repair_simulation_model.sql`; do not edit V1-V8 or Flyway history.
2. V9 creates the four simulation tables with `CREATE TABLE IF NOT EXISTS` and makes `simulation_generated_data_ref.device_id` nullable for `SLEEP` events.
3. Change run startup to persist a `PENDING` record and return immediately after scheduling execution following transaction commit.
4. Execute runs on the existing Spring async executor in a separate service. Restore `CurrentOrganizationContext` in the worker thread before using data-scope services and clear it in `finally`.
5. Check the persisted run status before and after delays and before each step. A `CANCELLED` run must never be overwritten by `SUCCESS` or `FAILED`.
6. Keep each generated sample and its trace reference in a focused transaction. Keep production simulation writes disabled through `ProductionSafetyGuard`.

## Acceptance Criteria

- Flyway history contains version 9 and all four simulation tables exist.
- V1-V8 remain unchanged.
- `startRun` returns a visible `PENDING` run before execution completes.
- Cancelling a pending/running execution stops later steps and leaves status `CANCELLED`.
- A `SLEEP` step without a target device can be traced without a database constraint failure.
- Existing simulation and full backend tests pass.