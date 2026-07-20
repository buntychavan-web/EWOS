# Attendance module

**Package:** `com.ewos.attendance`
**Introduced:** WP-007
**Depends on:** `shared`, `identity`, `employee`, `workflow`
**Consumed by (future):** `leave`, `payroll`, `analytics`

## Purpose

Per-employee time capture and period-scoped timesheet approval. Time entries are the immutable
record of clock events; timesheets roll them up per period and drive approval through the
Workflow module.

## Domain model

### `AttendancePolicy` — per-tenant metadata
- Standard hours per day / per week, working days (MON,TUE,...), grace minutes, overtime
  multiplier, timesheet period length.
- Optional `companyId` scopes the policy to one legal entity; the tenant-wide fallback wins when
  no company-specific policy is active.
- Soft-deleted, optimistic-locked.

### `TimeEntry` — immutable clock record
- Kinds: `IN`, `OUT`, `BREAK_START`, `BREAK_END`.
- Sources: `MANUAL`, `KIOSK`, `MOBILE`, `BADGE`, `SYSTEM`, `CORRECTION`.
- Corrections file a **new** row with `correctionOf` pointing at the original — mutations are
  never applied to historical rows so the audit trail stays complete.

### `Timesheet` — period rollup
- One per `(employee, periodStart, periodEnd)` — enforced by partial unique index.
- Statuses: `DRAFT` → `SUBMITTED` → `APPROVED` / `REJECTED` / `CANCELLED`.
- `SUBMITTED` starts a Workflow instance (`subjectType = "attendance.timesheet"`); the
  instance's terminal state is what flips the row to APPROVED / REJECTED.
- Rollups (`worked`, `overtime`, `break`, `absence`) computed by `TimesheetCalculator` from raw
  entries + effective policy.

## Domain service — `TimesheetCalculator`

Framework-neutral. Given policy + period + list of entries:
- Pairs `IN → OUT` and `BREAK_START → BREAK_END` chronologically.
- Deducts break minutes from worked minutes.
- Computes `standardExpected = standardHoursPerDay × workingDayCount(period)`.
- Overtime = `max(0, worked − standardExpected)`.
- Absence = `max(0, standardExpected − worked)` (zero when no entries at all — that's a full
  absence event that the Leave module owns, not raw attendance).

## Public API — base `/api/v1/attendance`

### Policies
| Method | Path | Auth |
|---|---|---|
| `POST` | `/policies` | `ATT_ADMIN` |
| `GET` | `/policies[/{id}]` | `ATT_READ` |
| `PATCH` | `/policies/{id}` | `ATT_ADMIN` |
| `DELETE` | `/policies/{id}` | `ATT_ADMIN` |

Fetch-by-id is Redis-cached under `attendance.policy::{tenant}:{id}`.

### Time entries
| Method | Path | Auth |
|---|---|---|
| `POST` | `/time-entries` | `ATT_WRITE` |
| `GET` | `/time-entries/{id}` | `ATT_READ` |
| `GET` | `/time-entries/employee/{employeeId}?from=&to=` | `ATT_READ` |
| `GET` | `/time-entries/employee/{employeeId}/recent` | `ATT_READ` |

### Timesheets
| Method | Path | Auth |
|---|---|---|
| `POST` | `/timesheets` (open or return existing DRAFT) | `ATT_WRITE` |
| `GET` | `/timesheets/{id}` | `ATT_READ` |
| `POST` | `/timesheets/{id}/recompute` | `ATT_WRITE` |
| `POST` | `/timesheets/{id}/submit` | `ATT_WRITE` |
| `POST` | `/timesheets/{id}/approve` | `ATT_APPROVE` |
| `POST` | `/timesheets/{id}/reject` | `ATT_APPROVE` |
| `POST` | `/timesheets/{id}/cancel` | `ATT_ADMIN` |
| `GET` | `/timesheets/employee/{employeeId}` | `ATT_READ` |
| `GET` | `/timesheets?status=` | `ATT_READ` |

All read/write endpoints require an `X-Tenant-Id` header.

## Persistence — Flyway `V12__attendance_engine.sql`

- 3 tables: `attendance_policies`, `time_entries`, `timesheets`.
- Partial unique on `(employee_id, period_start, period_end)` prevents duplicate timesheets.
- CHECK constraints for status enums, non-negative hours, hire/termination pairing.
- FKs into `employees` (from both `time_entries` and `timesheets`); soft `person_id`-style
  reference into `attendance_policies` (nullable FK).
- Seeds `ATT_READ`, `ATT_WRITE`, `ATT_APPROVE`, `ATT_ADMIN` and grants to `SYSTEM_ADMIN`.

## Events

Domain events published on `AFTER_COMMIT` to Kafka topic `ewos.attendance.event`:
`TIME_ENTRY_RECORDED`, `TIME_ENTRY_CORRECTED`, `TIMESHEET_CREATED`, `TIMESHEET_SUBMITTED`,
`TIMESHEET_APPROVED`, `TIMESHEET_REJECTED`, `TIMESHEET_CANCELLED`.

## Workflow integration

Submission calls
`WorkflowInstanceService.start(new StartInstanceRequest(tenantId, companyId,
workflowDefinitionId, "attendance.timesheet", timesheetId, "attendance.timesheet:{id}"))`
and stores the returned `instance.id()` on the timesheet. Downstream (Payroll, Analytics)
listens for `INSTANCE_COMPLETED` on the workflow topic OR `TIMESHEET_APPROVED` on the attendance
topic, whichever is more convenient.

## Caching

`AttendancePolicyService.getById` is Redis-cached under `attendance.policy::{tenant}:{id}`
(10-min TTL). All write paths evict all entries — safe because policy churn is rare.

## Testing

- Unit: `TimesheetCalculatorTest` (7), `AttendanceMapperTest` (3). Total 10 new.
- Integration: deferred to CI (Testcontainers).

## Deferred

- **Late / early exception rules** using the policy's `grace_minutes`. Column captured; not yet
  surfaced in the rollup.
- **Overnight-shift boundary** handling — currently the calculator assumes IN/OUT within one
  UTC day-window; extending across midnight is straightforward but not yet wired.
- **Kafka consumer** for `ewos.workflow.event` that auto-flips the timesheet status when the
  workflow instance completes. Currently the controller endpoints (`/approve` / `/reject`)
  are what mutate the row; a listener will close the loop with the workflow.
- **Shift schedules** — this module handles time capture but not shift patterns. A future
  Scheduling module owns that.
