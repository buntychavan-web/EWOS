# Competency Management (T10)

Talent Management milestone T10 — Volume 5 of the EWOS Business Bible.

## Scope

- **Competency Library** — reusable per-company competencies with configurable scale.
- **Skill Matrix** — combined view of role competencies + employee competencies + assessments.
- **Role Competencies** — required competency + expected level for a designation and/or org unit.
- **Employee Competencies** — per-employee current + target levels with last-assessed timestamp.
- **Competency Assessment** — capture SELF / MANAGER / PEER / EXTERNAL assessments.
- **Gap Analysis** — endpoint compares an employee's current level against required per designation.
- **Development Plan** — DRAFT → ACTIVE → COMPLETED / CANCELLED, with actionable steps.
- **Dashboard, Reports** — active competencies + plan status counts + gap report.

## Data model

Migration `V30__competency_management.sql`.

- `competencies` — reusable library items
- `role_competencies` — required competency + expected level per designation/org-unit
- `employee_competencies` — per-employee current + target levels
- `competency_assessments` — assessment history
- `development_plans` + `development_actions`

All tables carry standard `tenant_id`, `company_id`, `version_no`, and soft-delete columns.

## Endpoints

Under `/api/v1/competencies`, `/api/v1/competency-matrix`, `/api/v1/development-plans`. Every
endpoint requires `X-Tenant-Id` and a `COMPETENCY_*` authority.

## Permissions

| Code | Purpose |
| --- | --- |
| `COMPETENCY_READ` | View library, matrix, plans, reports |
| `COMPETENCY_WRITE` | Set role and employee competencies |
| `COMPETENCY_ASSESS` | Record assessments |
| `COMPETENCY_PLAN` | Create + manage development plans |
| `COMPETENCY_ADMIN` | Administer the library |

All five are seeded onto `SYSTEM_ADMIN` by the V30 migration.

## Events

`CompetencyEvent` on `ewos.competency.event` (kafka-optional). Event types cover library CRUD,
role competency updates, employee competency updates, assessments, plan lifecycle, and action
add/complete.
