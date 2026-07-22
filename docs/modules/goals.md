# Goals / OKR / KPI (T8)

Talent Management milestone T8 — Volume 5 of the EWOS Business Bible.

## Scope

- **Goal Library** — reusable per-company templates classified as KRA / KPI / OKR.
- **Assignment** — per-employee, per-team, per-department, or per-company scope.
- **Individual / Team / Department / Company Goals** — via `scope` enum.
- **KRAs, KPIs, OKRs** — via `goal_type` enum.
- **Weightage** — 0-100 numeric field on both library and assigned goals.
- **Progress Tracking** — `progress_percent` on the goal plus a full history in `goal_progress_updates`.
- **Review Workflow** — status flow DRAFT → ASSIGNED → IN_PROGRESS → UNDER_REVIEW → COMPLETED / CANCELLED, with score + notes.
- **Dashboards** — status counts + scope counts for a company.
- **Reports** — per-goal report filtered by status.

## Data model

Migration `V28__goals_okr_kpi.sql`.

- `goal_library` — reusable goal templates
- `goals` — assigned goals with scope, employee/org-unit ref, performance-cycle ref, weightage, progress, review, priority
- `goal_progress_updates` — append-only history of progress entries

All tables carry the standard `tenant_id`, `company_id`, `version_no`, and soft-delete columns.

## Statuses

`DRAFT` → `ASSIGNED` → `IN_PROGRESS` → `UNDER_REVIEW` → `COMPLETED` / `CANCELLED`.

`COMPLETED` and `CANCELLED` are terminal.

## Endpoints

Under `/api/v1/goal-library` and `/api/v1/goals`; every endpoint requires `X-Tenant-Id` and a `GOAL_*` authority.

## Permissions

| Code | Purpose |
| --- | --- |
| `GOAL_READ` | View library + goals + dashboards + reports |
| `GOAL_WRITE` | Create + update + assign + cancel goals |
| `GOAL_UPDATE_PROGRESS` | Record progress updates |
| `GOAL_REVIEW` | Score + review + complete goals |
| `GOAL_ADMIN` | Administer the library |

All five are seeded onto `SYSTEM_ADMIN` by the V28 migration.

## Events

`GoalEvent` is published on `ewos.goals.event` when Kafka is enabled
(`app.messaging.kafka.enabled=true`). Event types cover library CRUD, goal creation, assignment,
progress updates, review lifecycle, and closure.
