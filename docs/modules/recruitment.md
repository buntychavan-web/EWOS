# Recruitment module

**Package:** `com.ewos.recruitment` | **Depends on:** `shared`, `identity`, `employee`, `organization`, `workflow`
**Introduced:** T1 (Business Bible Volume 5 — Talent Management)

## Purpose
The hiring pipeline. Owns the long-lived catalogue of positions in the org chart and the
short-lived requisitions raised against them. Approval routes through the workflow engine and
lifecycle events feed downstream Talent milestones (ATS, interviews, offers, onboarding).

## Milestones

| Milestone | Migration | Scope |
|---|---|---|
| T1 Recruitment & Requisitions | V21 | Job positions, requisitions, workflow-integrated approval |

## Domain

* `JobPosition` — a named seat (title + department + grade + salary band). Long-lived.
* `JobRequisition` — a hiring request for one or more headcount against a position.
* `RequisitionStatus` — `DRAFT → PENDING_APPROVAL → APPROVED → OPEN → (FILLED | CLOSED)`,
  with `REJECTED`, `ON_HOLD`, and `CANCELLED` branches.
* `RequisitionPriority` — `LOW / MEDIUM / HIGH / URGENT`.
* `EmploymentType` — `FULL_TIME / PART_TIME / CONTRACT / TEMPORARY / INTERN / CONSULTANT`.
* `RequisitionPolicy` — lifecycle guards (submittable, decidable, openable, fillable, closeable, ...).

## Lifecycle

```
DRAFT ──submit──▶ PENDING_APPROVAL ──approve──▶ APPROVED ──open──▶ OPEN
                                     ──reject──▶ REJECTED
OPEN  ──hold──▶ ON_HOLD ──resume──▶ OPEN
OPEN  ──fill──▶ (FILLED when headcount met)
OPEN  ──close──▶ CLOSED
*     ──cancel──▶ CANCELLED  (from any non-terminal state)
```

## Workflow integration
Submission starts a workflow instance with `subject_type = "recruitment.requisition"`. Terminal
`approve` / `reject` calls flip the requisition; a `cancel` on a `PENDING_APPROVAL` requisition
also cancels the workflow instance.

## Permissions
| Code | Grants |
|---|---|
| `RECRUITMENT_READ` | View positions and requisitions |
| `RECRUITMENT_WRITE` | Create / edit positions, create / edit / submit / open / hold / resume / fill / close / cancel requisitions |
| `RECRUITMENT_APPROVE` | Approve or reject submitted requisitions |
| `RECRUITMENT_ADMIN` | Administrative operations (delete positions) |

## REST surface

| Base | Purpose |
|---|---|
| `/api/v1/recruitment/positions` | Job position catalogue (CRUD) |
| `/api/v1/recruitment/requisitions` | Requisition lifecycle |

Requisition lifecycle endpoints:

| Method | Path | Action |
|---|---|---|
| POST | `/requisitions` | Create DRAFT |
| PUT | `/requisitions/{id}` | Edit DRAFT |
| POST | `/requisitions/{id}/submit` | Start workflow, transition to PENDING_APPROVAL |
| POST | `/requisitions/{id}/approve` | Approve (from PENDING_APPROVAL) |
| POST | `/requisitions/{id}/reject` | Reject (from PENDING_APPROVAL) |
| POST | `/requisitions/{id}/open` | APPROVED → OPEN |
| POST | `/requisitions/{id}/hold` | OPEN → ON_HOLD |
| POST | `/requisitions/{id}/resume` | ON_HOLD → OPEN |
| POST | `/requisitions/{id}/fill` | Record fills; auto-terminal FILLED when headcount met |
| POST | `/requisitions/{id}/close` | Terminal CLOSED (with reason) |
| POST | `/requisitions/{id}/cancel` | Terminal CANCELLED (with reason) |
| GET | `/requisitions/{id}` | Read |
| GET | `/requisitions?companyId=&status=` | List by status |

All requests require the `X-Tenant-Id` header.

## Events — topic `ewos.recruitment.event`
Publisher is kafka-optional (`app.messaging.kafka.enabled=false` by default) and publishes on
`AFTER_COMMIT`. Event codes are in `RecruitmentEventType`:

`POSITION_CREATED / POSITION_UPDATED / POSITION_ACTIVATED / POSITION_DEACTIVATED /
REQUISITION_CREATED / REQUISITION_UPDATED / REQUISITION_SUBMITTED / REQUISITION_APPROVED /
REQUISITION_REJECTED / REQUISITION_OPENED / REQUISITION_HELD / REQUISITION_RESUMED /
REQUISITION_FILLED / REQUISITION_CLOSED / REQUISITION_CANCELLED`.

## Persistence

Tables (V21):

* `job_positions` — per-company position catalogue with partial unique index on `(tenant_id, company_id, LOWER(code))`.
* `job_requisitions` — per-company requisitions with partial unique index on `(tenant_id, company_id, LOWER(requisition_number))`.
  Check-constraints enforce lifecycle status enum, priority enum, employment-type enum,
  `filled_count ≤ headcount`, and non-negative budget.

Both tables use soft delete (`deleted_at`) + `@Version` optimistic locking.

## What T1 does NOT yet include
Downstream Talent milestones handle the pieces below; the schema and events here are prepared for
them but the code isn't shipped in T1:

* **T2 ATS** — candidates, applications, sourcing.
* **T3 Interview Management** — panels, scheduling, feedback.
* **T4 Offers & Pre-Boarding** — offer letters, negotiation, acceptance.
* **T5 Onboarding** — hand-off from requisition-fill to employee create.
