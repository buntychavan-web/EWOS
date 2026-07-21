# Employee Onboarding module

**Package:** `com.ewos.onboarding` | **Depends on:** `shared`, `identity`, `employee`, `offer` (event only)
**Introduced:** T5 (Business Bible Volume 5 — Talent Management)

## Purpose
Owns the post-joining employee experience: onboarding plans, task assignments to managers /
buddies / HR / IT, and 30/60/90-day surveys. A plan is auto-created when a T4 pre-boarding
checklist reaches `JOINED`, so the full hire → offer → pre-board → onboard chain is stitched
together in one join query.

## Milestone

| Milestone | Migration | Scope |
|---|---|---|
| T5 Onboarding | V25 | Task templates, plans, task instances, surveys, T4 handoff listener |

## Domain

* `OnboardingTaskTemplate` — reusable per-company catalogue (task type + default owner + SLA).
* `OnboardingPlan` — one per newly-joined employee; carries `source_offer_id` + `source_checklist_id`
  so downstream reporting can join the full hire trail.
* `OnboardingTaskInstance` — actual tasks on a plan; auto-materialised from active templates.
* `OnboardingSurvey` — 30/60/90-day feedback + open-ended cadences.
* `OnboardingPolicy` — plan + task transition guards.

### Enums

`OnboardingPlanStatus`, `OnboardingTaskType`, `OnboardingTaskOwner`, `OnboardingTaskStatus`,
`OnboardingSurveyType`.

## Plan lifecycle

```
PLANNED ─start─▶ IN_PROGRESS ─complete─▶ COMPLETED
    │                │
    └────cancel──────┴─────▶ CANCELLED
```

Plan auto-transitions from `PLANNED` to `IN_PROGRESS` on the first task-status change. Completion
requires every mandatory task to be terminal (COMPLETED / SKIPPED / FAILED).

## Task lifecycle

```
PENDING → IN_PROGRESS → COMPLETED | SKIPPED | FAILED
       → WAITING_ON_EMPLOYEE → IN_PROGRESS
       → WAITING_ON_COMPANY → IN_PROGRESS
```

Terminal task statuses count toward the plan's `completion_percent`. Reminders can be fired
on-demand via `POST /tasks/{id}/remind`.

## Task types & owners
Task types cover the standard post-joining activities: `ORIENTATION`, `DOCUMENT_UPLOAD`,
`POLICY_ACKNOWLEDGEMENT`, `TRAINING`, `INTRODUCTION_MEETING`, `TEAM_INTRO`, `SYSTEM_ACCESS`,
`WORKSTATION_SETUP`, `HANDBOOK`, `SURVEY`, `GOAL_SETTING`, `BUDDY_ASSIGNMENT`, `OTHER`.

Owner categories: `EMPLOYEE`, `MANAGER`, `BUDDY`, `HR`, `IT`, `ADMIN`, `OTHER` — each task also
carries an optional `assigned_employee_id` for fine-grained accountability.

## Surveys

* Cadences: `DAY_1`, `WEEK_1`, `DAY_30`, `DAY_60`, `DAY_90`, `EXIT_ONBOARDING`.
* One row per (plan, survey_type). Re-submits update in place.
* Optional 0–10 overall rating; free-form `responsesJson` for the survey payload.

## T4 → T5 handoff
`PreboardingJoinedListener` subscribes to `OfferEvent.PREBOARDING_CHECKLIST_JOINED` and:

1. Looks up the checklist and its `employee_id`.
2. Skips if a plan already exists for the checklist **or** the employee.
3. Creates a `PLANNED` plan carrying `source_offer_id` + `source_checklist_id` + `joining_date`.
4. Materialises tasks from active templates.

The listener never fails the parent transaction — a missing employee link just logs and returns.

## Permissions

| Code | Grants |
|---|---|
| `ONBOARDING_READ` | View plans, tasks, surveys |
| `ONBOARDING_WRITE` | Manage tasks, plans, buddy / manager assignment, reminders |
| `ONBOARDING_SURVEY` | Submit surveys |
| `ONBOARDING_ADMIN` | Administer task templates |

## REST surface

| Base | Purpose |
|---|---|
| `/api/v1/onboarding/task-templates` | Template CRUD |
| `/api/v1/onboarding/plans` | Create / list plans; by employee, by status |
| `/api/v1/onboarding/plans/{id}` | Fetch, start, complete, cancel, assign roles |
| `/api/v1/onboarding/plans/{id}/tasks` | Add task + list plan's tasks |
| `/api/v1/onboarding/tasks/{id}/status` | Update task status |
| `/api/v1/onboarding/tasks/{id}/remind` | Emit a reminder event |
| `/api/v1/onboarding/plans/{planId}/surveys` | Submit + list surveys, fetch by type |

Every request requires the `X-Tenant-Id` header.

## Events — topic `ewos.onboarding.event`
Kafka-optional publisher on `AFTER_COMMIT`. Codes in `OnboardingEventType`:

`PLAN_CREATED / _STARTED / _UPDATED / _COMPLETED / _CANCELLED`,
`TASK_CREATED / _ASSIGNED / _STARTED / _COMPLETED / _SKIPPED / _FAILED / _REMINDER_SENT`,
`SURVEY_SUBMITTED`, `BUDDY_ASSIGNED`, `MANAGER_ASSIGNED`.

## Persistence

V25 tables:

* `onboarding_task_templates` — partial-unique on `(tenant, company, LOWER(code))` alive.
* `onboarding_plans` — partial-unique on `(tenant, employee)` alive; one plan per employee.
* `onboarding_task_instances` — indexed by plan + sort_order, status, due date.
* `onboarding_surveys` — partial-unique on `(tenant, plan, survey_type)` alive.

Every table uses `AuditableEntity` + `@Version`.

## What T5 does NOT include (deferred)
* Deep training-catalog integration — TRAINING tasks store an `external_ref`; the actual LMS
  wiring lands with T9 Learning & Development.
* Probation setup — lives in T6 Probation & Confirmation; T5 emits `PLAN_COMPLETED` for T6 to
  subscribe to.
* Automated survey cadence scheduling — endpoints are ready; scheduling is a deployment concern.
