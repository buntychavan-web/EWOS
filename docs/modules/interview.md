# Interview Management module

**Package:** `com.ewos.interview` | **Depends on:** `shared`, `identity`, `employee`, `ats`
**Introduced:** T3 (Business Bible Volume 5 — Talent Management)

## Purpose
Owns interview templates, scheduled rounds, panel composition, structured scorecards, and
candidate feedback. Rounds attach to {@link com.ewos.ats.domain.JobApplication ATS applications}
from T2; each round's decision feeds the application pipeline (PROCEED advances, REJECT ends,
HOLD parks).

## Milestone

| Milestone | Migration | Scope |
|---|---|---|
| T3 Interviews | V23 | Templates, rounds, panel, scorecards, candidate feedback |

## Domain

* `InterviewTemplate` — reusable per-tenant config (type + duration + scorecard schema).
* `InterviewRound` — one scheduled interview attached to an application. Numbered per
  application.
* `InterviewParticipant` — panel members (interviewer / panel-lead / observer / coordinator) with
  attendance.
* `InterviewScorecard` — per-interviewer post-interview evaluation.
* `CandidateInterviewFeedback` — candidate's own feedback on the interview experience.
* `ScorecardAggregator` — round-level aggregate over interviewer scorecards.

### Enums

`InterviewType`, `InterviewMode`, `InterviewStatus`, `InterviewDecision`,
`InterviewParticipantRole`, `InterviewParticipantAttendance`, `ScorecardRecommendation`.

## Round lifecycle

```
DRAFT ─schedule─▶ SCHEDULED ─reschedule─▶ RESCHEDULED
                     │
                     ├─start─▶ IN_PROGRESS ─complete─▶ COMPLETED ─(await scorecards)─▶ PENDING_FEEDBACK
                     ├─no-show─▶ NO_SHOW
                     └─cancel─▶ CANCELLED   (from any non-terminal state)

Decision (from COMPLETED / PENDING_FEEDBACK): PENDING → PROCEED | HOLD | REJECT
```

`InterviewPolicy` enforces every guarded transition.

## Panel composition
Rounds carry one or more `InterviewParticipant` rows keyed on `(round, employee)` with a role and
attendance state. Roles: `INTERVIEWER`, `PANEL_LEAD`, `OBSERVER`, `COORDINATOR`. Attendance:
`UNKNOWN` → `ATTENDED` / `ABSENT` / `PARTIAL`.

## Scorecards
Each interviewer submits or updates one scorecard per round. Fields:

* `overallRating` — 0-10 decimal.
* `recommendation` — mapped to a numeric weight for aggregation
  (`STRONG_HIRE=+2`, `HIRE=+1`, `LEAN_HIRE=+1`, `NO_DECISION=0`, `LEAN_NO_HIRE=-1`,
  `NO_HIRE=-1`, `STRONG_NO_HIRE=-2`).
* Structured `criteria_json` (schema-free JSON payload for the template's scorecard schema).
* Strengths / weaknesses / comments.

Aggregate summary endpoint returns count + average rating + weighted-recommendation score +
lean flag.

## Calendar integration
`CalendarIntegration` is a pluggable interface. Default binding `NoOpCalendarIntegration`
returns `null` (no external event created). Deployments override with a `@Primary` bean pointing
at Google Workspace / Microsoft 365 / Zoom / whatever. The returned opaque reference is stored on
`interview_rounds.external_calendar_ref` for reconciliation.

## Notifications
`InterviewNotifier` is a pluggable interface. Default binding `NoOpInterviewNotifier` does
nothing. Real deployments plug in an email / SMS / push provider.

## Permissions

| Code | Grants |
|---|---|
| `INTERVIEW_READ` | View templates, rounds, panel, scorecards, feedback |
| `INTERVIEW_WRITE` | Create / schedule / reschedule / cancel rounds; manage panel; log candidate feedback; record decisions |
| `INTERVIEW_SUBMIT_SCORECARD` | Submit or update a scorecard |
| `INTERVIEW_ADMIN` | Hard-delete templates and configuration |

## REST surface

| Base | Purpose |
|---|---|
| `/api/v1/interviews/templates` | Template CRUD |
| `/api/v1/interviews/rounds` | Round create + schedule / reschedule / start / complete / no-show / cancel / decide + queries |
| `/api/v1/interviews/rounds/{roundId}/participants` | Panel add / list |
| `/api/v1/interviews/participants/{id}` | Attendance update + remove |
| `/api/v1/interviews/rounds/{roundId}/scorecards` | Submit / list / summary |
| `/api/v1/interviews/rounds/{roundId}/candidate-feedback` | Candidate feedback submit / get |

Every request requires the `X-Tenant-Id` header.

## Events — topic `ewos.interview.event`
Kafka-optional publisher on `AFTER_COMMIT`. Codes in `InterviewEventType`:

`TEMPLATE_CREATED / _UPDATED / _ACTIVATED / _DEACTIVATED`,
`ROUND_CREATED / _SCHEDULED / _RESCHEDULED / _STARTED / _COMPLETED / _CANCELLED / _NO_SHOW / _DECIDED`,
`PANEL_ADDED / _REMOVED / _ATTENDANCE_UPDATED`,
`SCORECARD_SUBMITTED / _UPDATED`,
`CANDIDATE_FEEDBACK_SUBMITTED`.

## Persistence

V23 tables:

* `interview_templates` — partial-unique index on `(tenant, company, LOWER(code))` alive.
* `interview_rounds` — partial-unique on `(tenant, application, round_number)`; check-constraints
  enforce status / decision / mode / type enums, positive duration, coherent scheduled and actual
  ranges.
* `interview_participants` — partial-unique on `(tenant, round, employee)`.
* `interview_scorecards` — partial-unique on `(tenant, round, interviewer)`; re-submits update in
  place.
* `candidate_interview_feedback` — partial-unique on `(tenant, round)` — one feedback row per
  round.

Every table except the timeline (owned by ATS) uses soft delete + `@Version`.

## Audit trail
All entities extend `AuditableEntity` (`created_at` / `updated_at` / `created_by` / `updated_by`).
Life-cycle events emitted on `ewos.interview.event` form the append-only audit stream.

## What T3 does NOT include (deferred to later milestones)
* Actual mailbox / SMS integrations — the notifier default no-ops; production ships a real bean.
* Actual calendar-provider clients — the integration default no-ops; production ships a real bean.
* Cross-round-slot conflict detection (interviewer double-book across rounds) — surface as a
  read-only conflicts endpoint in a follow-up.
* Automatic advancement of the ATS application on `PROCEED` — event listener candidate for T4.
