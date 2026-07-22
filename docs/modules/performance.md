# Performance Management (T7)

Talent Management milestone T7 — Volume 5 of the EWOS Business Bible.

## Scope

- **Performance Cycle** — per-company cycles with period, status, stage due-dates, bell-curve toggle.
- **Appraisal Templates** — reusable templates with rating scale + weighted sections.
- **Self / Manager / Reviewer Assessment** — three-stage appraisal with rating + comments.
- **Final Rating** — resolved from calibration or reviewer stage.
- **Calibration** — per-appraisal calibrated rating + band; grouped in `calibration_sessions`.
- **Bell Curve (Configurable)** — cycle-level config (`bell_curve_config_json`) + `/reports/bell-curve` endpoint.
- **Increment Recommendation** — `NONE / STANDARD / HIGH / WITHHOLD / SPECIAL` with percent + notes.
- **Promotion Recommendation** — `NONE / READY_NOW / READY_NEXT_CYCLE / NOT_READY` with notes.
- **Confirmation Workflow** — bound to shared workflow engine via subject-type `performance.appraisal`.
- **Notifications** — publishes `PerformanceEvent` (Kafka-optional) for downstream notifier services.
- **Dashboard & Reports** — status counts per cycle, per-employee report, bell-curve distribution.

## Data model

Migration `V27__performance_appraisal.sql`.

- `performance_cycles` — per-company cycle configuration + status
- `appraisal_templates` + `appraisal_template_sections` — reusable templates with weighted sections
- `appraisals` — per-employee, per-cycle appraisal record with self/manager/reviewer/calibration stages and increment/promotion recommendation
- `appraisal_ratings` — per-section rating history keyed by stage
- `calibration_sessions` — group calibration meetings scoped to a cycle

All tables carry standard `tenant_id`, `company_id`, `version_no`, soft-delete columns.

## Statuses

**Cycle** — `DRAFT` → `OPEN` → `SELF_REVIEW` → `MANAGER_REVIEW` → `REVIEWER_REVIEW` → `CALIBRATION` → `CLOSED` / `CANCELLED`

**Appraisal** — `PENDING_SELF` → `PENDING_MANAGER` → `PENDING_REVIEWER` → `CALIBRATION` → `PENDING_APPROVAL` → `FINALISED` / `CANCELLED`

`FINALISED` and `CANCELLED` are terminal.

## Endpoints

Under `/api/v1/performance-cycles`, `/api/v1/appraisal-templates`, `/api/v1/appraisals`, and `/api/v1/calibration-sessions`. Every endpoint requires `X-Tenant-Id` and a `PERF_*` authority.

## Permissions

| Code | Purpose |
| --- | --- |
| `PERF_READ` | View cycles, templates, appraisals, dashboards, reports |
| `PERF_WRITE` | Open appraisals, transition cycles, cancel |
| `PERF_APPRAISE_SELF` | Submit self assessment |
| `PERF_APPRAISE_MANAGER` | Submit manager assessment |
| `PERF_APPRAISE_REVIEWER` | Submit reviewer assessment |
| `PERF_CALIBRATE` | Record calibrated rating, manage calibration sessions, submit for approval |
| `PERF_APPROVE` | Approve / reject / finalise, record increment + promotion |
| `PERF_ADMIN` | Administer cycles + templates |

All eight are seeded onto `SYSTEM_ADMIN` by the V27 migration.

## Events

`PerformanceEvent` is published on `ewos.performance.event` when Kafka is enabled
(`app.messaging.kafka.enabled=true`). Event types cover cycle lifecycle, template CRUD, per-stage
appraisal submissions, calibration, approval/rejection, finalisation, increment/promotion
recommendation, and calibration-session lifecycle.
