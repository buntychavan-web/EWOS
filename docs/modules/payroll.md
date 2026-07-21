# Payroll module

**Package:** `com.ewos.payroll` | **Depends on:** `shared`, `identity`, `employee` | **Introduced:** WP-009

## Purpose
Per-tenant / per-company payroll: pay periods (calendar windows), pay components (metadata
catalogue), employee compensation (effective-dated salary structure), payroll runs (compute cycle
over a locked period), and payslips (immutable per-employee snapshot with line items).

## Domain
- **`PayComponent`** — metadata dictionary (BASIC, HRA, PROVIDENT_FUND, INCOME_TAX, …). Combines
  `PayComponentKind` (`EARNING` / `DEDUCTION`) with `PayComponentCalculationType`
  (`FIXED` / `PERCENT_OF_BASIC`). Taxable flag + default amount/percentage.
- **`PayrollPeriod`** — per-company window with `PayrollFrequency`
  (`MONTHLY` / `SEMI_MONTHLY` / `BI_WEEKLY` / `WEEKLY`) and lifecycle
  `OPEN` → `LOCKED` → `CLOSED`. Runs are only allowed against `LOCKED` periods.
- **`EmployeeCompensation`** — effective-dated salary structure per employee: basic salary,
  currency, frequency, `active` flag. At most one active row per employee (partial unique index);
  creating a new one supersedes the previous (`active = false`, `effectiveTo` set).
- **`EmployeeCompensationLine`** — per-employee override of a specific pay component
  (fixed amount or percentage of basic).
- **`PayrollRun`** — one compute cycle for a (period, company). Lifecycle
  `PENDING` → `PROCESSING` → `COMPLETED` → `FINALIZED` (or `FAILED`). Aggregate totals
  (`employeesProcessed`, `totalGross`, `totalDeductions`, `totalNet`) captured on completion.
- **`Payslip`** — per-employee immutable snapshot for a run: employee number/name snapshot, period
  window snapshot, currency, gross / deductions / net, `DRAFT` → `FINALIZED`.
- **`PayslipLine`** — line item with component code/name/kind/calculation-type snapshotted so
  historical payslips remain reproducible even if the catalogue is edited.
- **`PayrollCalculator`** — framework-neutral: computes lines + totals from a compensation record.
  Percentage components resolve against basic. Amounts scaled to 2 decimal places (HALF_UP). Net
  floors at zero.
- **`PayrollPolicy`** — rule enforcer: `assertEditable`, `assertLockable`, `assertClosable`,
  `assertRunnable`, `assertStartable`, `assertFinalizable`, `assertFailable`.

## Lifecycles

### PayrollPeriod
```
OPEN (edits allowed, no runs)
  → LOCKED (edits blocked, runs allowed)
  → CLOSED (nothing allowed)
```

### PayrollRun
```
PENDING → PROCESSING → COMPLETED → FINALIZED
                              ↘  FAILED
```

### Payslip
```
DRAFT (created during PROCESSING)
  → FINALIZED (when the parent run is finalized)
```

## API — base `/api/v1/payroll`

### Components — `/components`
| Method | Path | Auth |
|---|---|---|
| POST | `/components` | PAYROLL_ADMIN |
| GET | `/components/{id}` | PAYROLL_READ |
| GET | `/components` | PAYROLL_READ |
| PATCH | `/components/{id}` | PAYROLL_ADMIN |
| DELETE | `/components/{id}` | PAYROLL_ADMIN |

Redis-cached under `payroll.component::{tenant}:{id}`.

### Periods — `/periods`
| Method | Path | Auth |
|---|---|---|
| POST | `/periods` | PAYROLL_WRITE |
| GET | `/periods/{id}` | PAYROLL_READ |
| GET | `/periods/company/{companyId}` | PAYROLL_READ |
| GET | `/periods?status=` | PAYROLL_READ |
| PATCH | `/periods/{id}` | PAYROLL_WRITE |
| POST | `/periods/{id}/lock` | PAYROLL_RUN |
| POST | `/periods/{id}/close` | PAYROLL_ADMIN |

### Compensations — `/compensations`
| Method | Path | Auth |
|---|---|---|
| POST | `/compensations` | PAYROLL_WRITE |
| GET | `/compensations/{id}` | PAYROLL_READ |
| GET | `/compensations/employee/{employeeId}` | PAYROLL_READ |

Creating a new compensation for an employee marks the previous active record inactive and sets its
`effectiveTo` to `newRecord.effectiveFrom - 1` (if not already set).

### Runs — `/runs`
| Method | Path | Auth |
|---|---|---|
| POST | `/runs` | PAYROLL_RUN |
| POST | `/runs/{id}/finalize` | PAYROLL_RUN |
| GET | `/runs/{id}` | PAYROLL_READ |
| GET | `/runs/period/{periodId}` | PAYROLL_READ |

Starting a run runs the whole pipeline: reserves in `PENDING`, transitions to `PROCESSING`,
iterates every active compensation in the company, generates a `DRAFT` payslip per employee, and
lands in `COMPLETED` with aggregate totals.

### Payslips — `/payslips`
| Method | Path | Auth |
|---|---|---|
| GET | `/payslips/{id}` | PAYROLL_READ |
| GET | `/payslips/run/{runId}` | PAYROLL_READ |
| GET | `/payslips/employee/{employeeId}` | PAYROLL_READ |

All endpoints require an `X-Tenant-Id` header.

## Persistence — `V14__payroll_engine.sql`
Seven tables: `pay_components`, `payroll_periods`, `employee_compensations`,
`employee_compensation_lines`, `payroll_runs`, `payslips`, `payslip_lines`. CHECK constraints on
status enums, non-negative money amounts, currency length = 3, percentage 0–100, date ranges.
Partial unique indexes on
- `(tenant_id, code)` for `pay_components`
- `(tenant_id, company_id, code)` for `payroll_periods`
- `(employee_id)` where `active = TRUE` for `employee_compensations`
- `(run_id, employee_id)` for `payslips`.

Seeds four permissions: `PAYROLL_READ`, `PAYROLL_WRITE`, `PAYROLL_RUN`, `PAYROLL_ADMIN`.

## Events — topic `ewos.payroll.event`
`COMPONENT_CHANGED`, `PERIOD_OPENED`, `PERIOD_LOCKED`, `PERIOD_CLOSED`, `COMPENSATION_CHANGED`,
`RUN_STARTED`, `RUN_COMPLETED`, `RUN_FINALIZED`, `RUN_FAILED`, `PAYSLIP_GENERATED`,
`PAYSLIP_FINALIZED`. Published on `AFTER_COMMIT`; kafka-optional.

## Caching
`PayComponentService.getById` Redis-cached under `payroll.component::{tenant}:{id}` (10-min TTL).
Writes evict the whole cache to keep it consistent with catalogue edits.
