# Career & Succession Planning (T11)

Talent Management milestone T11 — Volume 5 of the EWOS Business Bible.

## Scope

- **Career Paths** — pathways from a source designation to a target designation.
- **Promotion Eligibility** — per-employee eligibility record (tenure, rating, competency gap).
- **Talent Pool** — named collections of employees (HiPo, high-performer, technical leadership, etc.).
- **Successor Planning** — plans for key positions with ranked candidates.
- **HiPo Identification** — via `talent_tier=HIGH_POTENTIAL` on pool + readiness assessment.
- **Readiness Assessment** — per-employee 9-box style (performance + potential + tier + readiness).
- **Succession Dashboard, Reports** — aggregate counts by tier and ready-now candidates.

## Data model

Migration `V31__career_succession.sql`.

- `career_paths`
- `promotion_eligibility`
- `talent_pools` + `talent_pool_members`
- `successor_plans` + `successor_candidates`
- `readiness_assessments`

## Endpoints

Under `/api/v1/career-paths` and `/api/v1/succession`; every endpoint requires `X-Tenant-Id` and a
`SUCCESSION_*` authority.

## Permissions

| Code | Purpose |
| --- | --- |
| `SUCCESSION_READ` | View career paths, pools, plans, assessments, dashboards |
| `SUCCESSION_WRITE` | Manage career paths, pools, successor plans + candidates |
| `SUCCESSION_ASSESS` | Record eligibility + readiness assessments |
| `SUCCESSION_ADMIN` | Administer succession module |

All four are seeded onto `SYSTEM_ADMIN` by the V31 migration.

## Events

`SuccessionEvent` on `ewos.succession.event` (kafka-optional). Covers career path CRUD, eligibility
+ readiness assessments, pool + successor-plan lifecycle.
