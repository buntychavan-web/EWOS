# Leave module

**Package:** `com.ewos.leave` | **Depends on:** `shared`, `identity`, `employee`, `workflow` | **Introduced:** WP-008

## Purpose
Employee leave lifecycle: per-tenant leave-type catalogue, yearly allocations, running balances,
and workflow-driven leave requests.

## Domain
- **`LeaveType`** — metadata dictionary (VACATION, SICK, PERSONAL, ...), paid flag, accrual/day,
  carry-forward cap, min-notice days, requires-approval flag.
- **`LeaveAllocation`** — yearly quota per (employee, type, year).
- **`LeaveBalance`** — running tally: accrued + carry + adjust − consumed − pending = available.
- **`LeaveRequest`** — approval-driven submission (DRAFT → SUBMITTED → APPROVED/REJECTED/CANCELLED).
- **`LeavePolicy`** — rule enforcer (past dates, min-notice, sufficient balance for paid leave,
  state transitions, admin-only cancellation of APPROVED).
- **`LeaveBalanceCalculator`** — weekday counter + available-days math.

## Workflow integration
Submit calls `WorkflowInstanceService.start(subject="leave.request", subjectId=requestId)`.
Approval / rejection are driven by the linked workflow instance's terminal state through the
`/approve` and `/reject` endpoints.

## Balance bucket transitions
- **Submit**: `available` → `pending` (reserves the days).
- **Approve**: `pending` → `consumed`.
- **Reject**: `pending` → back to `available` (freed).
- **Cancel (submitted)**: `pending` → freed.
- **Cancel (approved, admin only)**: `consumed` → freed.

## API — base `/api/v1/leave`

### Types
`POST/GET/PATCH/DELETE /types[/{id}]` — `LEAVE_ADMIN` (write) / `LEAVE_READ` (read). Redis-cached.

### Allocations & Balances
`POST /allocations`, `POST /balances/adjust`, `GET /allocations/employee/{id}?year=`,
`GET /balances/employee/{id}?year=` — `LEAVE_ADMIN` (write) / `LEAVE_READ` (read).

### Requests
| Method | Path | Auth |
|---|---|---|
| POST | `/requests` | LEAVE_WRITE |
| GET | `/requests/{id}` | LEAVE_READ |
| POST | `/requests/{id}/submit` | LEAVE_WRITE |
| POST | `/requests/{id}/approve` | LEAVE_APPROVE |
| POST | `/requests/{id}/reject` | LEAVE_APPROVE |
| POST | `/requests/{id}/cancel` | LEAVE_WRITE |
| POST | `/requests/{id}/admin-cancel` | LEAVE_ADMIN |
| GET | `/requests/employee/{id}` | LEAVE_READ |
| GET | `/requests?status=` | LEAVE_READ |

All endpoints require an `X-Tenant-Id` header.

## Persistence — `V13__leave_engine.sql`
Four tables: `leave_types`, `leave_allocations`, `leave_balances`, `leave_requests`.
CHECK constraints on status, date-range, non-negative days. Partial unique indexes on
`(employee, leave_type, year)` for allocations and balances. Seeds four permissions:
`LEAVE_READ`, `LEAVE_WRITE`, `LEAVE_APPROVE`, `LEAVE_ADMIN`.

## Events — topic `ewos.leave.event`
`REQUEST_CREATED`, `REQUEST_SUBMITTED`, `REQUEST_APPROVED`, `REQUEST_REJECTED`,
`REQUEST_CANCELLED`, `ALLOCATION_CHANGED`, `BALANCE_ADJUSTED`. Published on `AFTER_COMMIT`;
kafka-optional.

## Caching
`LeaveTypeService.getById` Redis-cached under `leave.type::{tenant}:{id}` (10-min TTL).
