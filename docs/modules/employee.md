# Employee module

**Package:** `com.ewos.employee`
**Introduced:** WP-005
**Depends on:** `shared`, `identity`, `organization`
**Consumed by (future):** `attendance`, `leave`, `payroll`, `recruitment`, `talent`, `analytics`

## Purpose

Employee master — the linkage aggregate between a Person (future biographic module) and an
Organization Unit. Represents the employment relationship of one individual with one company inside
one tenant, over time.

## Domain model

### `EmploymentType` — per-tenant metadata dictionary

- FULL_TIME, PART_TIME, CONTRACT, INTERN, APPRENTICE, ... anything the tenant needs.
- Same shape as `OrganizationUnitType` — `code`, `name`, `description`, `sortOrder`, `active`.
- Soft-deleted, partial unique `(tenant_id, LOWER(code))`.

### `Employee` — aggregate root

- Belongs to exactly one tenant + company.
- Optional soft `personId` → Person module (future).
- Business identifier: `employee_number` (unique per tenant + company).
- Denormalised biographic snapshot: `first_name`, `middle_name`, `last_name`, `display_name`,
  `work_email`, `personal_email`, `phone`, `date_of_birth`, `gender_code`.
- Placement: `primary_org_unit_id → organization_units`.
- Reporting: self-ref `manager_employee_id`.
- Employment: `employment_type_id → employment_types`.
- Lifecycle: `hire_date`, optional `termination_date`, `status ∈ {ACTIVE, ON_LEAVE, SUSPENDED,
  TERMINATED}`.
- Optimistic-locked, soft-deleted.

## Domain policy — `EmployeeLifecyclePolicy`

Framework-neutral rule enforcer:

- Manager must share tenant + company with the employee.
- Manager cannot be TERMINATED.
- No reporting cycles.
- Terminated employees are immutable (`assertMutable`).
- Termination requires a date on/after `hireDate`.

## Public API — base `/api/v1/employees`

### Employees

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/` | `EMP_ADMIN` | Hire (create + activate) |
| `GET` | `/{id}` | `EMP_READ` | Fetch (Redis-cached) |
| `PATCH` | `/{id}` | `EMP_WRITE` | Update mutable fields |
| `POST` | `/{id}/status?target=ON_LEAVE\|SUSPENDED\|ACTIVE` | `EMP_ADMIN` | Change status |
| `POST` | `/{id}/terminate` | `EMP_ADMIN` | Terminate (body: `terminationDate`, `reason`) |
| `DELETE` | `/{id}` | `EMP_ADMIN` | Soft-delete (fails 409 if has reports) |
| `GET` | `/` | `EMP_READ` | Paged search |

### Employment types

| Method | Path | Auth |
|---|---|---|
| `POST/GET/PATCH/DELETE` | `/employment-types[/{id}]` | `EMP_WRITE / EMP_READ / EMP_ADMIN` |

All read/write endpoints require `X-Tenant-Id` header. Tenant boundary enforced at the repository
layer via `findByIdAndTenantId`.

## Persistence — Flyway `V10__employee_engine.sql`

- Two tables (`employment_types`, `employees`).
- `person_id` is a soft reference (FK deferred until Person module ships).
- CHECK constraints:
  - `status IN ('ACTIVE','ON_LEAVE','SUSPENDED','TERMINATED')`
  - `termination_date IS NULL OR termination_date >= hire_date`
  - `status <> 'TERMINATED' OR termination_date IS NOT NULL`
  - `manager_employee_id IS NULL OR manager_employee_id <> id`
- Partial unique on `(tenant_id, company_id, LOWER(employee_number))` and
  `(tenant_id, company_id, LOWER(work_email))`.
- Seeds `EMP_READ`, `EMP_WRITE`, `EMP_ADMIN` permissions; grants to `SYSTEM_ADMIN`.

## Events

Every write emits an `EmployeeEvent` on Spring's `ApplicationEventPublisher`, forwarded to Kafka
topic `ewos.employee.employee` on `AFTER_COMMIT`.

Event types: `HIRED`, `UPDATED`, `REASSIGNED_ORG_UNIT`, `REASSIGNED_MANAGER`, `STATUS_CHANGED`,
`TERMINATED`, `DELETED`.

## Caching

`GET /employees/{id}` is cached in Redis under `employee.byId::{tenantId}:{id}` with the default
10-minute TTL. All write paths (`update`, `changeStatus`, `terminate`, `delete`) evict the key.

## Testing

- Unit — `EmployeeLifecyclePolicyTest` (8), `EmployeeMapperTest` (2), `EmployeeServiceTest` (6).
- Integration — Testcontainers Postgres end-to-end; deferred to CI where Docker is available
  (same environmental constraint as WP-004).

## Deferred to later work packages

- **PositionAssignment** aggregate — one employee → many active position assignments (project
  matrix orgs). Not needed for MVP.
- **CompensationBand** on hire — will come with Payroll module.
- **Onboarding checklist** state machine — Workflow module owns it.
- **Person module FK** — once Person is built, migration will add
  `ALTER TABLE employees ADD CONSTRAINT fk_employees_person FOREIGN KEY (person_id) REFERENCES persons(id)`.
