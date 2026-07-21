# ATS module

**Package:** `com.ewos.ats` | **Depends on:** `shared`, `identity`, `employee`, `organization`, `workflow`, `recruitment`
**Introduced:** T2 (Business Bible Volume 5 — Talent Management)

## Purpose
Applicant Tracking System. Owns candidate identity, resumes / documents / tags / notes / timeline
/ communications, and job applications tying candidates to recruitment requisitions. Applications
drive the hiring pipeline through the workflow engine and feed downstream Talent milestones
(T3 interviews, T4 offers, T5 onboarding).

## Milestone

| Milestone | Migration | Scope |
|---|---|---|
| T2 ATS | V22 | Candidates, resumes, docs, tags, notes, timeline, communications, applications |

## Domain

* `Candidate` — the aggregate root. Tenant-scoped identity + source + status + optional internal-
  employee link.
* `CandidateResume` — one or more resume files; at most one flagged primary.
* `CandidateDocument` — supporting docs (ID, education, offer letters, ...).
* `CandidateTag` — free-form skill / attribute labels.
* `CandidateNote` — typed notes (screening, interview, HR, ...); optional privacy flag.
* `CandidateTimelineEvent` — **append-only** activity log (no soft delete, no version_no).
* `CandidateCommunication` — inbound / outbound records over email, phone, SMS, video, etc.
* `JobApplication` — a candidate applying to a specific requisition. Full pipeline lifecycle.

### Enums

`CandidateStatus`, `CandidateSource`, `CandidateGender`, `DocumentType`, `NoteType`,
`CommunicationChannel`, `CommunicationDirection`, `ApplicationStatus`, `RejectionReason`,
`TimelineEventType`, `DuplicateMatchType`.

### Policies + helpers

* `ApplicationPolicy` — encodes the forward transition map and hold / reject / withdraw guards.
* `DuplicateCandidateDetector` — surfaces potential dupes on email exact-match and normalized
  phone-digit match.
* `PhoneNormalizer` — reduces a phone number to its digit-only form for comparison.
* `CandidateNumberGenerator` — `CAND-YYYYMM-XXXXXX` with retry-on-collision.

## Application pipeline

```
NEW ─▶ SCREENING ─▶ SHORTLISTED ─▶ INTERVIEW_SCHEDULED ─▶ INTERVIEWING
   ─▶ INTERVIEW_COMPLETED ─▶ OFFER_INITIATED ─▶ OFFER_EXTENDED
      ├─▶ OFFER_ACCEPTED ─▶ ONBOARDING ─▶ HIRED
      └─▶ OFFER_DECLINED (terminal)

Branches from most non-terminal states: ON_HOLD, REJECTED, WITHDRAWN.
Resume from ON_HOLD back to any non-hold status.
```

## Workflow integration
Applications carry `workflow_instance_id` and the subject-type `ats.application`. The pipeline can
be driven manually via `/advance` or wired to a workflow that emits the same transitions on
completion of each step.

## Duplicate-candidate detection
`POST /candidates` creates the row unconditionally and returns any potential-duplicate hits in the
response envelope so an approver / workflow can act on it. Independent lookup via
`GET /candidates/duplicates?email=&phone=`.

## Resume parsing framework
`ResumeParser` is a pluggable contract with three methods (`supports`, `parse`, `parserVersion`).
The default binding `NoOpResumeParser` ships in-tree so the API is functional out of the box; real
extraction (Sovren, Textkernel, HireAbility, ...) is added by supplying a `@Primary` bean.

## Permissions

| Code | Grants |
|---|---|
| `ATS_READ` | View candidates, resumes, documents, tags, notes, timeline, applications |
| `ATS_WRITE` | Create / edit candidates, resumes, documents, tags, notes, applications |
| `ATS_COMMUNICATE` | Log candidate communications |
| `ATS_ADMIN` | Administer ATS configuration; hard-delete data |

## REST surface

| Base | Purpose |
|---|---|
| `/api/v1/ats/candidates` | Candidate CRUD, status change, duplicate check |
| `/api/v1/ats/candidates/{id}/resumes` | Resume upload / list |
| `/api/v1/ats/resumes/{id}` | Primary flag, delete |
| `/api/v1/ats/candidates/{id}/documents` | Document upload / list |
| `/api/v1/ats/documents/{id}` | Document delete |
| `/api/v1/ats/candidates/{id}/tags` | Tag add / list / remove |
| `/api/v1/ats/candidates/{id}/notes` | Note add / list |
| `/api/v1/ats/candidates/{id}/timeline` | Candidate timeline |
| `/api/v1/ats/applications/{id}/timeline` | Application timeline |
| `/api/v1/ats/candidates/{id}/communications` | Communication log |
| `/api/v1/ats/applications` | Application create + pipeline (advance/hold/resume/reject/withdraw) + queries |

Every request requires the `X-Tenant-Id` header.

## Events — topic `ewos.ats.event`
Publisher is kafka-optional (`app.messaging.kafka.enabled=false` by default) and publishes on
`AFTER_COMMIT`. Event codes are in `AtsEventType`:

`CANDIDATE_CREATED / _UPDATED / _STATUS_CHANGED / _ARCHIVED / _BLACKLISTED / _TAGGED / _UNTAGGED`,
`RESUME_UPLOADED / _PARSED / _MARKED_PRIMARY`,
`DOCUMENT_UPLOADED / _DELETED`,
`NOTE_ADDED`, `COMMUNICATION_LOGGED`,
`APPLICATION_CREATED / _STATUS_CHANGED / _REJECTED / _WITHDRAWN / _ON_HOLD / _RESUMED / _HIRED /
_OFFER_EXTENDED / _OFFER_ACCEPTED / _OFFER_DECLINED`.

## Persistence

V22 tables:

* `candidates` — partial-unique index on (tenant, LOWER(email)); phone-digits index for fuzzy
  matching; check-constraint enforces internal / employee-id coherence.
* `candidate_resumes` — partial-unique index enforcing at most one `primary=true` per candidate.
* `candidate_documents` — type-enum constrained.
* `candidate_tags` — unique on (tenant, candidate, LOWER(tag)).
* `candidate_notes` — soft-deletable typed notes.
* `candidate_timeline_events` — append-only event log; no soft delete.
* `candidate_communications` — soft-deletable comm history.
* `job_applications` — partial-unique on (tenant, company, candidate, requisition) to prevent
  duplicate live applications; status / source / rejection-reason enum-constrained.

## What T2 does NOT yet include
Downstream Talent milestones handle the pieces below; the schema and events here are prepared for
them:

* **T3 Interview Management** — panels, interview scheduling, feedback capture.
* **T4 Offer Management & Pre-Boarding** — offer letter generation, negotiation, acceptance.
* **T5 Employee Onboarding** — hand-off from HIRED application to onboarded employee record.

## Also deferred (production plumbing)
* File storage — resumes / documents carry a `storage_uri`; the actual bytes live in an external
  object store (S3, GCS, Azure Blob) chosen per tenant.
* Real resume parsing — the default `NoOpResumeParser` no-ops; production ships a vendor bean.
* Full-text keyword search — schema-ready; hook up PostgreSQL FTS or an external index in a later
  milestone if simple attribute filtering isn't sufficient.
