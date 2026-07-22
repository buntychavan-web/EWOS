# Probation & Confirmation (T6)

Talent Management milestone T6 — Volume 5 of the EWOS Business Bible.

## Scope

Owns per-employee probation lifecycle on top of the Employee master:

- **Probation Policy** — per-company policies (default period, max extension, early-confirm switch).
- **Probation Tracking** — one record per employee with start/end/effective-end dates.
- **Extension** — controlled by policy `max_extension_days`, requires reason.
- **Early Confirmation** — gated by policy `allow_early_confirm`.
- **Confirmation Workflow** — bound to the shared workflow engine via subject-type
  `probation.confirmation`.
- **Manager Review** — captured on the record.
- **HR Recommendation** — `CONFIRM` / `EXTEND` / `TERMINATE`.
- **Confirmation Approval** — approve/reject transitions.
- **Confirmation Letter** — URI stored on the record, issued via dedicated endpoint.
- **Notifications** — publishes `ProbationEvent` (Kafka-optional) for downstream notifier services.
- **Dashboard & Reports** — aggregate status counts, due-date report.

## Handoff from T5

`OnboardingPlanCompletedListener` subscribes to `OnboardingEventType.PLAN_COMPLETED` and opens a
probation record for the employee (idempotent — one record per employee, per tenant).

## Data model

Migration `V26__probation_confirmation.sql`.

- `probation_policies` (per-company code + period + extension + early-confirm)
- `probation_records` (per-employee lifecycle, workflow linkage, review + recommendation fields,
  confirmation-letter URI)

Both tables carry the standard `tenant_id`, `company_id`, `version_no`, and soft-delete columns.

## Endpoints

Under `/api/v1/probation-policies` and `/api/v1/probation`:

| Method | Path | Permission | Purpose |
| --- | --- | --- | --- |
| POST | `/probation-policies` | `PROBATION_ADMIN` | Create policy |
| PUT | `/probation-policies/{id}` | `PROBATION_ADMIN` | Update policy |
| GET | `/probation-policies` | `PROBATION_READ` | List active |
| POST | `/probation/records` | `PROBATION_WRITE` | Open record |
| POST | `/probation/records/{id}/extend` | `PROBATION_WRITE` | Extend |
| POST | `/probation/records/{id}/manager-review` | `PROBATION_RECOMMEND` | Manager review |
| POST | `/probation/records/{id}/hr-recommendation` | `PROBATION_RECOMMEND` | HR recommendation |
| POST | `/probation/records/{id}/submit-confirmation` | `PROBATION_WRITE` | Start workflow |
| POST | `/probation/records/{id}/approve` | `PROBATION_APPROVE` | Approve |
| POST | `/probation/records/{id}/reject` | `PROBATION_APPROVE` | Reject (returns to IN_PROBATION) |
| POST | `/probation/records/{id}/confirm` | `PROBATION_APPROVE` | Confirm (final) |
| POST | `/probation/records/{id}/confirmation-letter` | `PROBATION_WRITE` | Attach letter URI |
| POST | `/probation/records/{id}/terminate` | `PROBATION_APPROVE` | Terminate (final) |
| POST | `/probation/records/{id}/cancel` | `PROBATION_WRITE` | Cancel |
| GET | `/probation/records/{id}` | `PROBATION_READ` | Fetch by id |
| GET | `/probation/records/by-employee/{employeeId}` | `PROBATION_READ` | Fetch by employee |
| GET | `/probation/records/by-status` | `PROBATION_READ` | List by status |
| GET | `/probation/dashboard` | `PROBATION_READ` | Aggregate counts |
| GET | `/probation/reports/by-status` | `PROBATION_READ` | Report rows |
| GET | `/probation/reports/due` | `PROBATION_READ` | Records due by given date |

All endpoints require the `X-Tenant-Id` header (except conversion endpoints that accept the tenant
in the body).

## Statuses

`IN_PROBATION` → `EXTENDED` → `PENDING_APPROVAL` → { `CONFIRMED` | `TERMINATED` | `CANCELLED` }.

`CONFIRMED`, `TERMINATED`, `CANCELLED` are terminal.

## Events

`ProbationEvent` is published on `ewos.probation.event` when Kafka is enabled
(`app.messaging.kafka.enabled=true`).

Event types cover open/extend/review/recommendation/submit/approve/reject/confirm/terminate/cancel
and confirmation-letter issue, plus policy CRUD.

## Permissions

- `PROBATION_READ` — view records + policies + dashboards + reports
- `PROBATION_WRITE` — open, extend, submit for confirmation, cancel, attach letter URI
- `PROBATION_RECOMMEND` — manager review, HR recommendation
- `PROBATION_APPROVE` — approve/reject confirmation, terminate, confirm
- `PROBATION_ADMIN` — administer policies

All five are seeded onto the `SYSTEM_ADMIN` role by the V26 migration.
