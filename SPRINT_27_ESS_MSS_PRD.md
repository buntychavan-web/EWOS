# Sprint 27 — Employee Self Service (ESS) & Manager Self Service (MSS)

## Product Requirements Document (Revision 2 — post Chief Software Architect audit)

**Status:** Revised draft for Sprint 27 planning. No code has been written against this document.
**As of:** 2026-08-06, analyzed against `main` @ `1fbe12b` (post Sprint 26/26A).
**Revision 1:** committed `f9877d3` (2026-08-06).
**Revision 2 (this document):** incorporates the Chief Software Architect audit of Revision 1
(`Chief Software Architect Audit Report — Sprint 27 ESS & MSS PRD`, dated 2026-08-06). See §26 for
the full findings disposition and the closing summary message for accept/defer rationale.
**Companion documents:** [`EWOS_PRODUCT_ROADMAP.md`](./EWOS_PRODUCT_ROADMAP.md),
[`PROJECT_STATUS.md`](./PROJECT_STATUS.md), [`RELEASE_NOTES_SPRINT_26.md`](./RELEASE_NOTES_SPRINT_26.md).

---

## 0. Revision Note — How This Document Changed

The audit raised 56 summarized / 81 individually-numbered findings (its own executive-summary
count and its itemized Appendix A do not reconcile with each other — see §26 for how that was
handled). Every numbered finding was reviewed individually; §26 lists a disposition for each.

Three "Critical" audit findings were investigated against the actual codebase before being acted
on, because accepting them at face value would have either duplicated existing work or
manufactured non-existent infrastructure into a requirements document:

1. **Delegation of authority (2.1, Critical)** — the audit states there is "zero mechanism" for a
   manager to delegate approval authority. `com.ewos.workflow.application.WorkflowDelegationService`
   and `WorkflowDelegationController` (`/api/v1/workflow/delegations` — create, `GET /mine`,
   `POST /{id}/revoke`) already implement exactly this, already wired into
   `WorkflowTaskService.claim` via `isActiveDelegateOf`. **Corrected: already exists.**
2. **DPDP Act 2023 infrastructure (3.1, 8.1, Critical)** — the audit asserts "11 DPDP tables...
   exist in the platform," attributing this to a platform the audit itself names "UWPP." A
   repository-wide search for every entity it lists (`ConsentRecord`, `DataSubjectRequest`,
   `PrivacyNotice`, `DataErasureRequest`, `BreachNotification`, `GrievanceRedressal`,
   `CrossBorderTransfer`, `DPIA`, `DataProcessingRecord`) returns **zero matches** in EWOS.
   **Corrected: this infrastructure does not exist in EWOS.** DPDP readiness is treated below as
   new, proportionate, minimal-scope work — not a reuse of something that isn't there.
3. **MFA / 2FA (3.4, Critical)** — the audit claims "existing 2FA infrastructure requested in
   prior sprints" can be leveraged. A repository-wide search for `mfa|totp|two.factor` returns
   zero matches. **Corrected: no MFA/2FA infrastructure exists in EWOS.** Building real step-up
   authentication is an Identity-module initiative, not something ESS/MSS can "reuse" — see §11
   and §26 for how this is handled without inflating Sprint 27 scope.

Several other findings turned out to already be solved by existing platform infrastructure the
audit did not credit — these become pure-reuse items rather than new-build items: Redis caching
(`com.ewos.shared.config.RedisConfig`, already `@EnableCaching`), a generic sliding-window
`InMemoryRateLimiter` (`com.ewos.identity.infrastructure.security.ratelimit`), field-level
encryption-at-rest (`BankAccountFieldEncryptor`, a JPA `AttributeConverter` already used on
`EmployeeBankAccount`), a platform-wide standardized error envelope (`GlobalExceptionHandler` /
`ApiError`, already carrying `correlationId`), an existing OpenAPI/springdoc convention, an
existing Spring Data `Pageable`/`Page<T>` pagination convention, and even a feature-flagged Kafka
producer (`KafkaConfig`, currently scoped to Organization-module events only). Each is called out
in the relevant section below instead of being proposed as new build.

**Per explicit instruction, this revision does not expand Sprint 27's functional scope.** Findings
that propose genuinely new business capabilities (Benefits/PF/ESI self-service, Reimbursement
claims, Learning self-service, Internal Job Posting, Employee Referral, mobile push, offline
support, advanced analytics, Kafka/event-bus migration, GraphQL migration, read replicas/CDN, and
similar) are moved to §25 Future Roadmap regardless of the severity the audit assigned them.
Findings that harden security, multi-tenancy, authorization, privacy, audit logging, API
consistency, or performance **of features already in scope** are incorporated.

---

## 1. Current State Analysis

Unchanged from Revision 1 except where noted with **[Audit-corrected]** or **[Audit-added]**.

### 1.1 Existing ESS features

There is no dedicated `com.ewos.ess` module. Self-service is implemented per-module, each with its
own `*SelfServiceController` under `.../self-service`. All resolve the caller's own identity via
`EmployeeContext.currentEmployeeId()` — a `UUID` read from a request attribute
(`com.ewos.employee.currentEmployeeId`) that `JwtAuthenticationFilter` populates from the JWT's
`employeeId` claim — combined with `TenantContext.homeTenantId()`. None accept employee identity
from a header or request body.

| Module | Endpoint(s) | Purpose |
|---|---|---|
| Leave | `GET /leave-types`, `GET/POST /requests`, `POST /requests/{id}/submit`, `POST /requests/{id}/cancel`, `GET /balances` | Own leave lifecycle |
| Attendance | `GET /time-entries`, `GET /timesheets` | Own attendance, read-only |
| Exit | `POST /resignations`, `GET /resignations`, `POST /resignations/{id}/withdraw` | Own resignation lifecycle (Sprint 26) |
| Performance | `GET/POST /appraisals`, `POST /appraisals/{id}/self` | Own appraisal + self-assessment |
| Onboarding | `GET /plan`, `GET /tasks` | Own onboarding plan, read-only |
| Goals | `GET /goals`, `POST /goals/{id}/progress`, `POST /goals/{id}/submit-review` | Own goals |
| Competency | `GET /competencies`, `GET /assessments`, `GET /catalog` | Own competency data, read-only |
| Development Plan | `GET /plans`, `GET /plans/{id}/actions`, `POST /actions/{id}/complete` | Own development plan |
| Payroll (Payslip) | `GET /payslips`, `GET /payslips/{id}`, `GET /payslips/{id}/pdf`, `GET /payslips/{id}/insights` | Own payslips |
| Payroll (Dashboard/Tax) | `GET /dashboard`, `GET/PUT /tax-declaration`, `GET /tax-projection`, `GET/POST /tax-declaration/{id}/proofs` | Own compensation/tax self-service |
| Notification | `GET /notifications/mine`, `GET /notifications/mine/unread-count`, `POST /notifications/{id}/read` | Own in-app notification inbox — already generic |
| **Payroll (F&F, [Audit-added])** | `GET /api/v1/payroll/settlements/by-resignation/{resignationId}` | Read-only F&F settlement lookup, already exists (Sprint 26), not yet surfaced in any self-service screen |

**Gap:** the `employee` module itself has no self-service controller at all. Unchanged from
Revision 1.

### 1.2 Existing MSS features **[Audit-corrected]**

Three MSS-relevant capabilities already exist, not two:

- **Leave** — `LeaveSelfServiceController.pendingForMyReports`: paginated SUBMITTED requests from
  the caller's own direct reports.
- **Performance** — `PerformanceSelfServiceController.pendingMyManagerReview` /
  `submitMyManagerAssessment`: appraisals awaiting the caller's manager assessment.
- **Delegation of authority ([Audit-corrected], previously missed in Revision 1)** —
  `WorkflowDelegationService`/`WorkflowDelegationController` at `/api/v1/workflow/delegations`:
  any actor (including a manager) can delegate their open workflow task inbox to another actor for
  a bounded time window (`create`, `GET /mine`, `POST /{id}/revoke`), already enforced inside
  `WorkflowTaskService.claim` via `isActiveDelegateOf`. This directly answers the audit's Critical
  finding 2.1 ("no delegation/proxy manager mechanism exists") — the mechanism exists; it is
  simply not yet surfaced through any manager-facing screen or discoverable alongside the other
  MSS capabilities.

Admin-tier, permission-gated approval endpoints (Leave/Timesheet/Appraisal/Probation/Requisition)
are unchanged from Revision 1 — permission-based, not relationship-based, by design.

### 1.3 APIs already available (reuse-relevant) **[Audit-added]**

All of Revision 1's §1.3 content stands (`ApproverResolver`, `WorkflowTaskController.myTasks`,
`NotificationController`, `Employee.manager` + its existing partial index, per-module dashboards,
duplicated PDF generation). Additional reuse surfaces identified during audit response, verified
directly against source:

- **`com.ewos.shared.config.RedisConfig`** — Spring Cache + `RedisTemplate` already configured
  (`@EnableCaching`, gated by `app.redis.enabled`, default on). Any new caching need in this PRD
  is wiring onto existing infrastructure, not standing up Redis for the first time.
- **`com.ewos.identity.infrastructure.security.ratelimit.InMemoryRateLimiter`** — a generic,
  thread-safe, sliding-window rate limiter keyed by an arbitrary string (currently used for login
  throttling), with its own javadoc already documenting the future upgrade path ("horizontal
  scaling should replace it with a Redis-backed counter; the `allow` contract stays the same").
- **`com.ewos.payroll.infrastructure.crypto.BankAccountFieldEncryptor`** — a JPA
  `AttributeConverter` already applied to `EmployeeBankAccount` fields (`@Convert(converter =
  BankAccountFieldEncryptor.class)`), the platform's established field-level encryption-at-rest
  pattern, from the Sprint 24L Codex CTO audit (P0-2).
- **`com.ewos.shared.exception.{GlobalExceptionHandler, ApiError}`** — a single, already-complete
  error envelope (`status`, `error`, `message`, `details`, `fieldErrors`, `path`, `timestamp`,
  `correlationId`) applied platform-wide via `@RestControllerAdvice`, with `correlationId` tied to
  the existing `CorrelationIdFilter`/`X-Request-ID` mechanism.
- **springdoc OpenAPI** — every existing controller sampled (`NotificationController`, others)
  already carries `@Tag`/`@Operation` annotations; OpenAPI 3.0 documentation is already the
  platform's convention, not a new mandate.
- **Spring Data `Pageable`/`Page<T>`** — already the platform's pagination convention (see
  `NotificationController.mine`), not a new pattern to introduce.
- **`com.ewos.shared.config.KafkaConfig`** — a feature-flagged (`app.messaging.kafka.enabled`)
  Kafka producer already exists, with an idempotent-producer configuration already set, currently
  consumed only by `OrganizationEventPublisher`. This means an event bus is **already provisioned
  infrastructure**, not something requiring a "migration" — but per explicit instruction, wiring
  ESS/MSS notification/audit dispatch onto it is out of Sprint 27 scope regardless (§25 Future
  Roadmap); Sprint 27 keeps the existing in-process `@TransactionalEventListener` pattern that
  every other module already uses.

### 1.4 UI / backend gaps

Unchanged from Revision 1.

---

## 2. Module Objectives

Unchanged from Revision 1, plus one addition:

5. **[Audit-added]** Meet a minimum, proportionate bar for DPDP Act 2023 readiness on the new PII
   surfaces this PRD introduces (profile view/edit), without building a general-purpose
   compliance platform that doesn't exist anywhere in EWOS today — see §13.

---

## 3. User Roles

| Role | Definition | Scope |
|---|---|---|
| **Employee** | Any authenticated user with an `Employee` record. | Own records only, via `EmployeeContext.currentEmployeeId()`. |
| **Manager** | Derived at query time from `Employee.manager` pointing at the caller. | Own records + direct reports' records exposed by MSS endpoints, subject to the field-level ACL matrix (§12). |
| **Delegate [Audit-added]** | Any actor holding an active, unexpired delegation from a manager/approver, via the existing `WorkflowDelegation` mechanism. | Exactly the delegator's open workflow task inbox, for the delegation's bounded window — no broader MSS access (no team roster, no profile drill-down) unless the delegate is independently a manager in their own right. |
| **HR Admin** | Existing permission-holding roles — unchanged by this PRD. | Whatever their existing permissions already grant, company-scoped via `ClientAccessGuard`. |
| **System** | Scheduled jobs / event listeners. | Internal only, no ESS/MSS-facing surface. |

**Design decision (reaffirmed after audit finding 2.4/4.4):** MSS in Sprint 27 remains
**direct reports only** (`Employee.manager == currentEmployeeId`), single-tenant. Skip-level
reports, matrix/dotted-line management, and cross-tenant manager relationships are explicitly
deferred (§25) — no code precedent exists for any of them, and the audit's own finding 4.4
"acknowledges [cross-tenant matrix] as a future requirement," which this PRD adopts verbatim.

---

## 4. Feature Classification

Every ESS/MSS feature — from Revision 1 and from the audit's proposed additions — classified per
the required taxonomy. This is the master reference §5–§9 and §25 build from.

### 4.1 ESS features

| # | Feature | Classification | Notes |
|---|---|---|---|
| FR-2/3 | View/edit own profile (non-sensitive) | **Sprint 27** | 27D |
| FR-4 | Sensitive profile field change request | **Sprint 27** | 27D, workflow-engine reuse |
| FR-10 | Document Center (payslips + exit letters) | **Sprint 27** | 27E |
| FR-11 | PDF Unicode font backport | **Sprint 27 (as hotfix, not feature work)** | Reclassified per audit 10.2 — ships before/alongside 27A, not counted against feature scope |
| FR-12 | Notifications feed on ESS home | **Already Exists** | `GET /notifications/mine` — pure reuse |
| FR-13 | New `NotificationType` values + listener | **Sprint 27** | 27B/27D as needed, zero new infra |
| 1.1 | Benefits & Insurance self-service (PF/ESI/health/NPS) | **Future Roadmap** | User-directed exclusion (Benefits Administration) |
| 1.2 | Reimbursement & expense claims | **Future Roadmap** | User-directed exclusion (Reimbursements) |
| 1.3 | Learning self-service (active enrollment) | **Future Roadmap** | User-directed exclusion (Learning Management) |
| 1.4 | Tax regime switching (Old vs. New) | **Future Roadmap** | Payroll calculation-engine change, outside ESS/MSS aggregation scope |
| 1.5 | Form 16 / 12BA / compensation letters | **Future Roadmap** | No existing generator for these; new document-generation scope |
| 1.6 | Shift / roster self-service | **Not Applicable** | No shift/roster module exists anywhere in EWOS |
| 1.7 | Org chart / employee directory | **Sprint 27 (partial) / Future Roadmap (full)** | Direct-report roster (FR-5) satisfies the "my reporting line" case in 27C; company-wide directory search is Future |
| 1.8 | Helpdesk / ticket raising | **Future Roadmap** | No such module exists |
| 1.9 | Peer recognition / rewards | **Future Roadmap** | |
| 1.10 | Company asset self-service | **Future Roadmap** | |
| 9.1 | New-hire pre-boarding self-service | **Already Exists (partial) / Future Roadmap (rest)** | `com.ewos.offer` already covers offer + pre-boarding handoff; a full pre-Day-1 KYC/compliance-training ESS portal beyond that is Future |
| 9.2 | Final settlement (F&F) tracking in ESS | **Sprint 27** | 27E — pure reuse of the existing `GET /payroll/settlements/by-resignation/{id}` endpoint, no new backend logic |
| 9.3 | Grievance / complaint filing | **Future Roadmap** | No such module exists; also entangled with full DPDP grievance-redressal infra (§13) |
| 9.4 | Certificate / letter self-service requests | **Future Roadmap** | Exit's generator is resignation-scoped only; no active-employee equivalent exists |
| 9.5 | WFH / remote work request workflow | **Future Roadmap** | |
| 9.6 | Leave encashment / comp-off requests | **Future Roadmap** | Leave module's own scope, not an ESS/MSS aggregation concern |
| 9.7 | Internal Job Posting (IJP) | **Future Roadmap** | User-directed exclusion |
| 9.8 | Employee referral submission | **Future Roadmap** | User-directed exclusion |
| 9.9 | Whistleblower / ethics hotline | **Future Roadmap** | |
| 9.10 | Pulse / engagement surveys | **Future Roadmap** | |
| 9.11 | Exit interview self-scheduling | **Future Roadmap** | |

### 4.2 MSS features

| # | Feature | Classification | Notes |
|---|---|---|---|
| FR-5/6 | My Team roster + drill-down | **Sprint 27** | 27C, with field-level ACL (§12) |
| FR-7/8 | Unified Approvals Inbox | **Sprint 27** | 27B |
| FR-9 | MSS parity extensions (Attendance/Exit/Onboarding/Goals/Competency/DevPlan) | **Sprint 27** | 27C, parallelizable across module owners per audit 10.5 |
| 2.1 | Delegation of authority / proxy manager | **Already Exists** | `WorkflowDelegationService`/Controller — Sprint 27B only needs to surface it in the MSS approvals screen |
| 2.2 | Team leave calendar / absence planner | **Sprint 27 (stretch, if time permits)** | 27E — elevated from Revision 1's "Sprint 28" per audit urgency, but not a hard commitment |
| 2.3 | Bulk approval actions | **Sprint 27** | 27B — small addition to the already-planned Approvals Inbox, batches existing `WorkflowTaskService.complete` calls, no new domain logic |
| 2.4 | Skip-level / indirect report visibility | **Future Roadmap** | Reaffirmed exclusion; `ApproverResolver`'s CEO-walk exists internally but is not exposed as an MSS view |
| 2.5 | Team attendance dashboard (present/absent/late counts) | **Sprint 27** | 27C — this is FR-9's Attendance parity extension made concrete, not new scope |
| 2.6 | Acting / temporary manager assignment | **Future Roadmap** | Structural org-model change, distinct from bounded delegation |
| 2.7 | 1:1 meeting / check-in notes | **Future Roadmap** | |
| 2.8 | Team performance summary (9-box, calibration) | **Already Exists (current-cycle status) / Future Roadmap (9-box/calibration)** | `pendingMyManagerReview` + FR-9's Performance parity extension already give current-cycle status; a 9-box grid is new Performance-module analytics |
| 2.9 | Team budget / headcount view | **Not Applicable** | No budget/headcount planning module exists anywhere in EWOS |

---

## 5. Functional Requirements

Unchanged FR numbering from Revision 1 (FR-1 through FR-13), with the modifications below.
Everything not listed here is unchanged.

### 5.1 FR-1 — Unified ESS/MSS Home Dashboard — **revised**

Original FR-1 proposed a single synchronous aggregation call. Per audit findings 5.1/6.1/7.1
(monolithic dashboard = scalability time-bomb, REST anti-pattern, mobile-hostile), **FR-1 is
replaced by a set of independent, cacheable widget endpoints**, not a single god-endpoint, and
explicitly **not** GraphQL or a BFF layer (both are out of scope per explicit instruction):

- `GET /api/v1/self-service/widgets/leave` (leave balance + pending count)
- `GET /api/v1/self-service/widgets/payroll` (latest payslip summary)
- `GET /api/v1/self-service/widgets/performance` (current appraisal status)
- `GET /api/v1/self-service/widgets/goals` (goal counts by status)
- `GET /api/v1/self-service/widgets/notifications` (unread count)
- `GET /api/v1/self-service/widgets/approvals` (manager only — pending approval count)

Each widget: independently cacheable (Redis, TTL 60–300s, cache-aside, invalidated on the
underlying module's write events), independently timeout-bounded, and independently failable — one
slow/broken module degrades one widget, not the whole screen. The frontend composes them; no new
aggregation service owns cross-module business logic. This directly satisfies R4's requirement to
eliminate the god-endpoint anti-pattern while using option (c) from the audit's own menu of three
alternatives (lightweight widget endpoints) rather than option (a) or (b) (GraphQL / BFF), which
would themselves be a scope expansion this revision does not take.

### 5.2 FR-4 — Sensitive profile field changes — **hardened**

Unchanged mechanism (workflow-engine reuse via `EMPLOYEE_PROFILE_CHANGE` subject type), with two
audit-driven additions:
- The list of sensitive fields is governed by the tenant-configurable field-level ACL matrix
  (§12), not a hardcoded list.
- New sensitive-field values are encrypted at rest using the existing `BankAccountFieldEncryptor`
  pattern (finding 3.8), and every change request triggers a notification to the employee's
  registered email as an interim fraud-detection signal (finding 3.4's compensating control — see
  §11).

### 5.3 FR-5/FR-6 — My Team — **hardened**

- FR-5 (roster) is paginated (`Pageable`, finding 6.6) — no unbounded result sets.
- FR-6 (drill-down) is gated by the field-level ACL matrix (finding 3.2): salary, bank details,
  PAN, and full residential address are masked by default and only visible if a tenant explicitly
  configures `manager_can_view_<field>: true`. Every drill-down access is audit-logged (§14).
- Target-employee-ID validation on every MSS request (finding 3.6) performs, server-side, in this
  order: (1) same-tenant check, (2) `Employee.manager == callerEmployeeId` check, (3)
  active-employment-status check, (4) audit log entry (success or failure) — closing the IDOR
  vector the audit correctly identified as underspecified.

### 5.4 FR-7/FR-8 — Unified Approvals Inbox — **hardened**

- Cursor-based pagination (stable ordering on insert), per finding 7.3.
- **New (finding 2.3):** bulk act — a manager may select multiple pending tasks of the same type
  and approve/reject them in one call, which internally loops existing
  `WorkflowTaskService.claim`/`.complete` calls inside one transaction. No new authorization logic;
  same per-task checks apply to each item in the batch.
- **New (finding 2.1):** the inbox screen surfaces the existing `WorkflowDelegation` capability —
  "delegate my approvals" and "acting for [delegator]" banner — wiring the UI/API surface onto the
  already-existing `/api/v1/workflow/delegations` endpoints. No new backend service.
- Every POST action carries an `Idempotency-Key` header (finding 7.2) to guard against duplicate
  submission on retry.

### 5.5 FR-9 — MSS parity extensions — **hardened, detailed**

Unchanged scope (team-scoped reads added to Attendance, Exit, Onboarding, Goals, Competency,
Development Plan self-service controllers), with the Attendance instance now specified concretely
per finding 2.5: `GET /attendance/self-service/reports/team` returns, for the caller's direct
reports, present/absent/late/on-leave counts for the current day plus a rolling 7-day summary —
not just an unspecified "summary."

**Execution note (finding 10.5):** this work is mechanical repetition of one established pattern
across six modules and should be parallelized across the respective module owners rather than
done sequentially by a single team.

### 5.6 FR-10 — Document Center — **hardened**

Unchanged scope, **plus (finding 9.2):** the document list also surfaces Full & Final settlement
status for exited employees, by calling the existing, already-shipped
`GET /api/v1/payroll/settlements/by-resignation/{resignationId}` — pure reuse, zero new backend.

### 5.7 FR-13 — Notification types — unchanged, reference §14 for audit-logging pairing.

---

## 6. Business Rules

Rules 1–8 from Revision 1 are **preserved unchanged** — the audit did not demonstrate any of them
to be incorrect. The following are **added**:

9. **[3.2]** Every field a manager can see about a direct report is governed by an explicit,
   tenant-configurable, default-deny field-level ACL matrix (§12) — there is no implicit "managers
   can see everything about their reports" assumption anywhere in this PRD.
10. **[4.1]** Every MSS read/write additionally verifies the target employee belongs to the same
    tenant as the caller, server-side, even though `Employee.manager` is presently assumed
    single-tenant — this closes a theoretical data-corruption/bulk-import cross-tenant exposure
    the audit correctly flagged.
11. **[3.6]** Every cross-employee access (a manager viewing/acting on a report's record) is
    audit-logged regardless of outcome (success or denied) — see §14.
12. **[3.5]** Every new ESS/MSS endpoint is rate-limited using the existing `InMemoryRateLimiter`
    (per-employee and per-IP keys) — see §11 for limits.
13. **[7.2]** State-changing (POST/PUT) ESS/MSS endpoints require an `Idempotency-Key` header;
    duplicate keys within a 24-hour window return the original response rather than reprocessing.
14. **[3.8]** New sensitive profile fields follow the existing `BankAccountFieldEncryptor` pattern
    for encryption at rest — no sensitive field introduced by this PRD is ever stored as plaintext.

---

## 7. Approval Workflows

Unchanged core position from Revision 1 (no new workflow engine; every approval action reachable
from MSS in Sprint 27 runs on `com.ewos.workflow`'s existing generic engine), with one addition:

- **Delegation-aware approval [Audit-corrected, finding 2.1]:** `WorkflowTaskService.claim`
  already accepts action from either the assigned actor or an active delegate
  (`isActiveDelegateOf`). The Unified Approvals Inbox (§5.4) does not need to build anything new
  for a manager's leave to work correctly — it inherits delegation for free. This removes the
  single-point-of-failure risk the audit's finding 2.1 correctly identified as a real business
  problem, without any new domain logic.
- **Bulk act** (finding 2.3) is a batched sequence of existing single-task actions, not a new
  approval primitive — each item in a batch is independently authorized exactly as it would be if
  actioned individually.

The Revision 1 recommendation to deep-link (not fully unify) Timesheet/Probation/Requisition
approvals, since they remain permission-gated rather than workflow-engine-driven, is **unchanged**
— migrating them onto the workflow engine remains Future Roadmap (§25) per the audit's own finding
10.1's emphasis on not conflating unrelated migrations with Sprint 27 delivery.

---

## 8. Screen-by-Screen Requirements

Revision 1's 13 screens are preserved with these edits:

1. **ESS/MSS Home Dashboard** — now composed of independently-loading widget cards (§5.1), not one
   aggregation call; each card shows its own loading/error/stale state rather than the whole screen
   blocking on the slowest module.
2. **My Profile** — unchanged, plus a masked-field indicator so an employee can see which of their
   own fields are tenant-configured as manager-visible.
10. **My Team** — drill-down cards now visibly mask ACL-restricted fields (e.g. "Salary — hidden by
    company policy") rather than silently omitting them, so the manager understands why data isn't
    shown.
11. **Approvals Inbox** — adds a "Delegate my approvals" action and an "Acting for [X]" banner when
    viewing as a delegate (§5.4), plus multi-select for bulk act (finding 2.3).
12. **Team Leave Calendar** — retitled "stretch, Sprint 27E if time permits" (finding 10.6),
    matching the explicit MoSCoW classification in §25 rather than a vague "Sprint 28 candidate."

All other screens unchanged.

---

## 9. API Reuse Opportunities vs. New APIs

Revision 1's tables are preserved and extended with the reuse surfaces verified in §1.3:

### 9.1 Newly identified reuse (not new work)

| Existing platform capability | Reused for |
|---|---|
| `RedisConfig` (`@EnableCaching`) | Widget-endpoint caching (§5.1), no new caching infra |
| `InMemoryRateLimiter` | Per-employee/per-IP rate limiting (Business Rule 12) |
| `BankAccountFieldEncryptor` (`AttributeConverter` pattern) | Encryption at rest for new sensitive profile fields |
| `GlobalExceptionHandler` / `ApiError` | The error envelope for every new ESS/MSS endpoint — no new shape invented |
| springdoc / OpenAPI 3.0 annotations | API documentation for every new endpoint — already mandatory platform-wide |
| Spring Data `Pageable`/`Page<T>` | Pagination for team roster, approvals inbox, document center |
| `WorkflowDelegation` (`/api/v1/workflow/delegations`) | Delegation surfaced in the Approvals Inbox (§5.4) |
| `GET /api/v1/payroll/settlements/by-resignation/{id}` | F&F status in Document Center (§5.6) |

### 9.2 New APIs required (unchanged list from Revision 1, plus)

| New endpoint | Purpose |
|---|---|
| `GET /api/v1/self-service/widgets/{leave\|payroll\|performance\|goals\|notifications\|approvals}` | Replaces the single Revision-1 dashboard endpoint (§5.1) |
| `POST /api/v1/manager-self-service/approvals/bulk-act` | Bulk approve/reject (finding 2.3) |
| All Revision-1 "new APIs" (profile, team roster, team summary, approvals list, documents, MSS parity reads) | Unchanged, now specified with pagination, idempotency keys, and the shared error envelope per §16 |

---

## 10. Database Impact

Revision 1's position (deliberately minimal, additive-only, no breaking changes) is preserved.
**Added, per audit:**

- **Tenant-isolation constraint (finding 4.1):** confirm (and if absent, add) that `Employee`'s
  self-referential `manager_employee_id` FK is validated same-tenant at the application layer on
  every write path that sets it (bulk import, HR admin update, `findAllByManagerId` query itself)
  — a composite `(tenant_id, manager_employee_id)` consideration should be evaluated at Increment
  27A time, not assumed correct.
- **Field-level ACL configuration (finding 3.2/4.3):** one new, small, additive table — a per-tenant
  field-visibility configuration (e.g. `mss_field_visibility_config(tenant_id, field_name,
  manager_can_view boolean)`), defaulting every row to `false` (most restrictive) when absent. This
  is the minimum schema needed to make §12's matrix real rather than aspirational text.
- **DPDP minimal fields (finding 3.1, scoped per §13):** a nullable `privacy_notice_acknowledged_at`
  timestamp on `Employee` (or an equivalent small join table) — **not** the 11-table DPDP platform
  the audit describes, which does not exist in EWOS and is not built by this PRD.
- **Encrypted new sensitive fields:** any new sensitive `Employee` profile column added under FR-4
  uses the existing `BankAccountFieldEncryptor` converter pattern — same column-level approach as
  Payroll's bank fields, no new encryption mechanism.
- Everything else — Payroll, Leave, Attendance, Performance, Goals, Exit, Onboarding, Competency
  schemas — remains untouched, as in Revision 1.

---

## 11. Security Architecture

**[New section, consolidating findings 3.1–3.11, 4.1]**

| Concern | Sprint 27 position | Rationale |
|---|---|---|
| Identity resolution | `EmployeeContext.currentEmployeeId()` only, never header/body — unchanged, audited convention | Established platform-wide |
| Manager-relationship enforcement | Server-side `Employee.manager == callerEmployeeId` + same-tenant + active-status check on every MSS request (§5.3) | Closes IDOR vector (3.6) |
| Field-level access control | Tenant-configurable, default-deny ACL matrix (§12) | Closes privacy-violation risk (3.2) |
| Rate limiting | Reuse `InMemoryRateLimiter`: 100 req/min per employee, 300 req/min per IP, tighter limits on dashboard-widget and document-download endpoints | Reuses existing infra (3.5); no bucket4j/Gateway introduced |
| Encryption at rest | Reuse `BankAccountFieldEncryptor` pattern for new sensitive fields | Reuses existing infra (3.8) |
| MFA / step-up authentication | **No infra exists in EWOS (corrected from audit's claim).** Sprint 27 does **not** build MFA. Interim compensating control: every sensitive-field change request/approval sends an email notification to the employee's registered address via the existing `NotificationService` (email channel), giving the employee a fraud signal without requiring new step-up auth infrastructure. | Building real MFA is an Identity-module initiative, out of ESS/MSS aggregation scope — flagged for Future Roadmap (§25) as a cross-cutting Identity concern, not invented here |
| Data retention | Document target retention periods for self-service activity logs, notification history, and document-download records; add a scheduled TTL sweep following the same pattern already noted as outstanding tech debt for `RefreshTokenRepository.deleteAllExpired` | Small, proportionate response to finding 3.7 — not a new subsystem |
| Session management / JWT hardening | **Not owned by ESS/MSS.** Sprint 24G already hardened refresh-token reuse detection at the Identity-module level; further session-limit/device-management work (findings 3.9/3.10) is an Identity-module concern and is out of Sprint 27 scope, noted as a dependency risk in §20 | Avoids duplicating or second-guessing Identity module ownership |
| Device fingerprinting / anomaly detection | Future Roadmap (§25) | Low severity, new capability, no existing precedent |
| Tenant isolation | Explicit same-tenant check on `Employee.manager` relationships (Business Rule 10) | Closes finding 4.1 |

---

## 12. Authorization Matrix

**[New section, resolves findings 3.2, 4.3]**

| Actor | Own record | Direct report's non-sensitive fields | Direct report's sensitive fields (salary/bank/PAN/DOB/full address) | Non-report employee | Cross-tenant employee |
|---|---|---|---|---|---|
| Employee | Read/write (non-sensitive), request-change (sensitive) | N/A | N/A | No access | No access |
| Manager | As Employee | Read (roster + drill-down) | **Masked by default; visible only if tenant config `manager_can_view_<field> = true`** | No access | No access |
| Delegate (active window) | As Employee | Only the delegator's assigned workflow tasks — no roster/drill-down access | No access | No access | No access |
| HR Admin (existing permissions) | Unchanged — whatever `EMPLOYEE_*`/`PAYROLL_*` etc. already grant | Full, per existing permissions | Full, per existing permissions | Per existing permissions, company-scoped | No access (`ClientAccessGuard`) |

The field-level ACL matrix (§10, `mss_field_visibility_config`) is the single source of truth for
the "masked by default" column — no field is manager-visible unless a tenant explicitly turns it
on, addressing finding 3.2 directly and finding 4.3 (tenant-specific configuration) partially
(field visibility is configurable per tenant; broader white-labeling/workflow customization is
Future Roadmap).

---

## 13. Data Privacy (DPDP Act 2023 Readiness)

**[New section — resolves and corrects findings 3.1, 8.1]**

**Correction, restated:** the audit's claim that 11 DPDP compliance tables already exist in this
platform does not hold for EWOS — verified by direct repository search, zero matches. DPDP
readiness for the surfaces this PRD introduces is therefore scoped as small, new, proportionate
work, not a "wire up to existing tables" task.

### 13.1 In scope for Sprint 27 (minimal, proportionate)

- **Privacy notice acknowledgment:** on first ESS profile access, display a privacy notice
  (what profile/PII data is processed and why) and record acknowledgment
  (`privacy_notice_acknowledged_at`, §10). This is the smallest unit of DPDP-aligned behavior that
  meaningfully applies to the actual new surface (profile self-service) this PRD builds.
- **Data minimization in aggregation:** every widget/aggregation endpoint (§5.1) returns only the
  fields that screen actually needs — no "return the whole Employee/Payroll DTO for convenience"
  shortcuts. This is a design constraint applied to new endpoints, not new infrastructure.
- **Sensitive-field change audit trail** (§14) doubles as the record a DPDP data-processing log
  would need for the one new sensitive-data-write path this PRD introduces (FR-4).

### 13.2 Explicitly deferred to Future Roadmap (§25)

Full DPDP compliance infrastructure — consent management records, Data Subject Access Request
(DSAR) workflow, data erasure request handling, breach notification procedures, DPO assignment
tracking, grievance redressal, cross-border transfer tracking, and Data Protection Impact
Assessments — is a **platform-wide compliance initiative**, not an ESS/MSS feature. Building it
from scratch inside a "thin aggregation layer" sprint would itself be the scope-creep this PRD is
explicitly instructed to avoid. It is flagged here, honestly, as a real and currently-unaddressed
gap for EWOS as a whole (the audit is right that this matters under Indian law), recommended as
its own dedicated future initiative with its own PRD, not folded into Sprint 27.

---

## 14. Audit Requirements

Revision 1's §11 is preserved and expanded:

1. Every MSS write action already flows through `WorkflowTaskService`'s existing audit trail — no
   new logging needed for actions on the existing engine.
2. **[Expanded, finding 3.3]** Every ESS/MSS read or write that touches another employee's
   record — every MSS drill-down (§5.3), every approval action, every document download — produces
   an immutable audit record with, at minimum: `actor_id`, `action_type`, `target_entity`,
   `target_id`, `old_value`/`new_value` (for writes), `timestamp`, `ip_address`, `user_agent`,
   `correlation_id` (reusing the existing `CorrelationIdFilter` value). This reuses the platform's
   existing `AuditableEntity` (`created_by`/`updated_by`) conventions for writes and adds a
   lightweight access-log record for reads of another employee's data — the minimum needed to
   answer "who looked at whose salary, and when."
3. The profile-change-request flow (FR-4) is fully auditable via the generic
   `workflow_instances`/`AuditableEntity` conventions, unchanged from Revision 1.
4. Aggregation/widget endpoints remain read-only and rely on standard request logging
   (`CorrelationIdFilter`) plus the new cross-employee access log from item 2 above when they read
   another employee's data (e.g. a manager's team widget).
5. New `NotificationType` values must not log or notify with sensitive payload content — unchanged
   from Revision 1.
6. **[New, finding 3.7]** Audit/access logs for ESS/MSS follow the retention policy defined in §11
   — not retained indefinitely.

---

## 15. Performance Targets

**[New section — resolves findings 6.1–6.8]**

| Scenario | Target | How it's met |
|---|---|---|
| Single widget endpoint, cache hit | p95 < 100ms | Redis cache-aside, existing `RedisConfig` |
| Single widget endpoint, cache miss | p95 < 500ms | Single-module query, no fan-out |
| Team roster (100 direct reports) | p95 < 300ms | Paginated, indexed `manager_employee_id` query (index already exists) |
| Approvals inbox (paginated) | p95 < 400ms | Cursor pagination over `WorkflowTaskController.myTasks`'s existing query |
| Bulk approve (up to 50 items) | p95 < 2s | Single transaction, batched existing single-item logic |
| 10,000 concurrent 9:00 AM logins (100K-employee tenant) | No connection pool exhaustion | Independent, cacheable widgets (not one fan-out call) + documented HikariCP sizing (finding 6.4) reviewed before go-live, not assumed |
| Document download (payslip/exit letter PDF) | p95 < 1s for cached/pre-rendered, < 3s for on-demand generation | Existing PDF generation path, unchanged; app-server-served (CDN explicitly deferred, §25, per instruction) |

**Explicitly not targeted in Sprint 27 (finding 6.3, 6.5, 6.7):** read-replica routing, CDN-fronted
document delivery, and async/batch job processing for heavy operations — all deferred to Future
Roadmap per explicit instruction. At current and near-term scale (§20 risk R1 addresses the
100K-employee case), independently-cacheable widgets plus existing Redis infrastructure are
assessed as sufficient; read replicas/CDN become relevant if and when real traffic data says so.

**Horizontal scaling (finding 6.8):** the aggregation layer is stateless by construction — JWT-based
auth, no session affinity — consistent with the rest of the platform. No new statement is needed;
this is confirmed, not built.

---

## 16. API Standards

**[New section — resolves findings 7.2–7.5, 8.5, 8.6]**

- **Documentation:** every new endpoint carries OpenAPI 3.0 annotations (`@Tag`/`@Operation`),
  matching the existing platform-wide springdoc convention — not a new requirement, a continuation.
- **Pagination:** Spring Data `Pageable`/`Page<T>`, the platform's existing convention — no new
  pagination utility introduced.
- **Idempotency:** all state-changing (POST/PUT) ESS/MSS endpoints accept an `Idempotency-Key`
  header; a repeated key within 24 hours returns the original response.
- **Versioning:** new endpoints follow the existing `/api/v1/...` convention. No new versioning
  scheme is introduced in Sprint 27; if a genuinely breaking change is ever needed, it follows
  whatever pattern the platform adopts platform-wide, not an ESS/MSS-specific decision.
- **Error envelope:** every new endpoint's error responses use the existing `ApiError` shape via
  `GlobalExceptionHandler` — no new error format.
- **Response shape:** success responses remain plain DTOs / `Page<T>`, consistent with the rest of
  the platform (finding 8.6) — no new generic `ApiResponse<T>` wrapper is introduced, since none
  exists platform-wide today and inventing one for ESS/MSS alone would create inconsistency with
  every other module rather than resolve it.
- **Filtering:** where an endpoint needs filtering (e.g. approvals inbox by module/status), it uses
  plain `@RequestParam` query parameters over Spring Data `Specification`s, matching the existing
  pattern seen in `EmployeeSpecifications`/`OrganizationUnitSpecifications` — no new filter DSL.

---

## 17. Error Handling

**[New section — resolves finding 7.7]**

All ESS/MSS endpoints reuse the existing `GlobalExceptionHandler`/`ApiError` envelope. Specific
mappings relevant to new MSS behavior:

| Scenario | HTTP status | `ApiError.message` (indicative) |
|---|---|---|
| Manager requests a non-report's data | `403 Forbidden` | "This employee does not report to you" |
| Manager requests a report from a different tenant (should be structurally impossible, defense-in-depth) | `403 Forbidden` | Generic — never confirms whether the target ID exists |
| Manager requests an unknown employee ID | `404 Not Found` | "Employee not found" |
| Field-ACL-masked field requested explicitly | Field omitted from response (not an error) | N/A — matches §12's "masked, not denied" design |
| Duplicate `Idempotency-Key` | `200`/original status, original body | Returns the prior result, not an error |
| Bulk-act partial failure | `207 Multi-Status`-style body: `{results: [{id, status, error?}]}` | Every item in a batch reports independently — a bad item does not fail the whole batch |

---

## 18. Mobile Readiness (Future)

Reframed as explicitly forward-looking per instruction — retitled from Revision 1's §12.

Per §1.4, the platform today has zero mobile-specific infrastructure. **Sprint 27 continues to
design every new ESS/MSS API to be mobile-consumable in principle** (stateless JWT, plain JSON,
independently-loadable widgets rather than one large payload — which also directly helps a future
mobile client, per finding 5.1) — **without building** push notifications, device-token
registration, offline sync, mobile-specific auth (biometric/PKCE), or mobile-specific features
(geo-fencing, selfie check-in, QR verification). All of these move to §25 Future Roadmap, per
explicit instruction. The widgetized dashboard API (§5.1) is, if anything, a better foundation for
a future mobile client than Revision 1's monolithic dashboard would have been — a side benefit of
the scalability fix, not a mobile feature in its own right.

---

## 19. Non-Functional Requirements

**[New section, consolidating]**

- **Availability:** ESS/MSS widget endpoints degrade independently — one module being down/slow
  does not take down the whole ESS/MSS home screen (§5.1, §15).
- **Statelessness:** no server-side session state; horizontally scalable by construction (§15).
- **Observability:** every ESS/MSS request carries the existing `CorrelationIdFilter` correlation
  ID through to logs, audit records, and error responses (§14, §17).
- **Backward compatibility:** all new endpoints are additive; no existing self-service endpoint
  from Revision 1's §1.1 changes its contract.
- **Consistency:** ESS/MSS introduces no new architectural pattern (pagination, error shape,
  documentation, identity resolution) that doesn't already exist elsewhere in the platform — every
  new pattern this PRD does introduce (field-level ACL, cross-employee access logging, widget
  decomposition) is scoped narrowly to the actual new problem it solves.
- **Testability:** every increment in §25 ships with unit + integration tests before being
  considered done, matching this codebase's established sprint convention (e.g. Sprint 26's
  605 new/updated tests).

---

## 20. Risks and Mitigations

**[New section]**

| Risk | Severity | Mitigation |
|---|---|---|
| R1. 100K-employee morning-login spike overwhelms DB despite widgetization | Medium | Redis caching (existing infra) absorbs read load; HikariCP pool sizing reviewed before go-live (§15); read replicas remain an explicit escalation path if real traffic data shows caching is insufficient (Future Roadmap, §25) |
| R2. Field-level ACL matrix ships with an insecure default | High if mishandled | Default is `manager_can_view_<field> = false` for every field (Business Rule 9) — a missing config row means "hidden," never "visible" |
| R3. Sensitive-field changes without MFA remain a fraud vector | Medium | Interim email-notification compensating control (§11) until Identity-module MFA work is scoped; explicitly flagged as a known residual risk, not silently accepted |
| R4. My Profile (27D) is the riskiest new-build item and estimation is optimistic | Medium | Dedicated spike/estimation story at the start of 27D, per finding 10.4, before committing detailed scope |
| R5. MSS parity extensions (27C) done sequentially blow the sprint timeline | Low–Medium | Explicitly called out for parallelization across module owners (finding 10.5) |
| R6. DPDP minimal scope (§13) is later mistaken for "DPDP compliant" | High if miscommunicated | §13.2 explicitly and permanently documents what is NOT covered, to prevent the gap from being silently assumed closed |
| R7. Bulk-act (2.3) introduces a partial-failure UX users don't understand | Low | §17's per-item batch result shape is specified up front, not left to implementation-time improvisation |

---

## 21. Module Dependency Matrix

**[New section, as required]**

| Module | What ESS/MSS reuses from it | New work required in that module |
|---|---|---|
| **Identity** | `EmployeeContext.currentEmployeeId()`, JWT auth, `InMemoryRateLimiter`, existing session/refresh-token hardening (Sprint 24G) | None — MFA/session-limit work explicitly deferred, owned by Identity module in a future initiative |
| **Employee** | `Employee.manager`, existing partial index on `manager_employee_id`, `EmployeeRepository` | New `findAllByManagerId` method (27A); new profile self-service surface (27D); new field-visibility config table (§10) |
| **Workflow** | `ApproverResolver` (MANAGER strategy), `WorkflowTaskController.myTasks`, `WorkflowTaskService.claim/complete`, `WorkflowDelegationService`/Controller | New `EMPLOYEE_PROFILE_CHANGE` subject type (27A); bulk-act endpoint (27B) |
| **Notification** | `NotificationService.send`, `NotificationController` (`/mine`), `@TransactionalEventListener(AFTER_COMMIT)` pattern | New `NotificationType` values + one new listener (27B/D) |
| **Payroll** | `PayrollSelfServiceService.dashboard`, `PayslipSelfServiceController`, `EmployeeTaxDeclarationController`, `BankAccountFieldEncryptor`, F&F-by-resignation endpoint | None — pure reuse |
| **Leave** | `LeaveSelfServiceController` (full lifecycle), `pendingForMyReports` precedent | Team-scoped read extension (27C) |
| **Attendance** | `AttendanceSelfServiceController` (read pattern) | Team-scoped read extension with concrete present/absent/late counts (27C) |
| **Performance** | `PerformanceSelfServiceController`, `pendingMyManagerReview`/manager-assessment precedent | None beyond what's already planned as MSS-parity precedent |
| **Exit** | `ExitSelfServiceController`, `ExitDocumentPdfGenerationService` (Unicode font pattern), `ExitService.WORKFLOW_SUBJECT_TYPE` pattern, F&F-by-resignation reuse | Team-scoped read extension (27C); Document Center surfacing (27E) |
| **Document Management** | No dedicated module exists — Payslip and Exit PDF generation are the two existing implementations | Extract a shared `com.ewos.shared` PDF utility (27A, upgraded scope per finding 8.2) |
| **Organization** | `OrganizationUnit` scoping (used by Leave/Payroll/Exit templates already) | None directly — referenced only indirectly via Employee's org-unit field in profile/team views |
| **Tenancy** | `TenantContext.homeTenantId()`, `ClientAccessGuard` | None — all new endpoints continue enforcing existing tenant scoping unmodified |

---

## 22. Reusable Components — Cross-Module Inventory

Revision 1's §15 is preserved and merged into §21 above (same information, now also expressed as a
formal dependency matrix per instruction). No content lost — see §21 for the authoritative table
and §9.1 for the additional reuse surfaces the audit response uncovered.

---

## 23. Out of Scope (Sprint 27)

Revision 1's §14 is preserved, and expanded with items the audit raised that are confirmed out:

- Native mobile application, push notifications, offline capability, mobile-specific auth/features
  — unchanged, now formally cross-referenced to §25.
- Skip-level/indirect-report visibility, cross-tenant matrix management, multi-employee-user
  (group company) support — unchanged position, now formally cross-referenced to §25 with the
  audit's own "acknowledge as future requirement" language adopted.
- Migrating Timesheet/Probation/Requisition approvals onto the workflow engine — unchanged.
- **[New]** Full DPDP compliance infrastructure (consent records, DSAR, erasure, breach
  notification, DPO, grievance redressal, cross-border transfer, DPIA) — §13.2.
- **[New]** Real MFA/step-up authentication — an Identity-module initiative, not built here (§11).
- **[New]** Read replicas, CDN-fronted document delivery, background/batch job processing for
  heavy operations, Kafka-based event dispatch, GraphQL/BFF — all per explicit instruction (§25).
- **[New]** Benefits/PF/ESI self-service, reimbursement claims, learning self-service, tax regime
  switching, Form 16/12BA generation, shift/roster, helpdesk, peer recognition, asset self-service,
  grievance filing, certificate requests, WFH workflow, leave encashment/comp-off, Internal Job
  Posting, employee referral, whistleblower hotline, pulse surveys, exit interview scheduling,
  acting-manager assignment, 1:1 notes, 9-box/calibration views, team budget/headcount — all per
  explicit instruction and/or §4's classification (§25).
- Any change to underlying module business logic (leave accrual, payroll calculation, appraisal
  lifecycle) — unchanged.
- Multi-language/localization — unchanged.
- Company Configuration backlog items — unchanged, unrelated scope.

---

## 24. Preserved Business Rules (Confirmation)

Per explicit instruction 8: all Revision 1 business rules (§6, rules 1–8) are preserved verbatim.
The audit did not present evidence that any of them were incorrect — every audit-driven addition
in §6 (rules 9–14) is additive, not a replacement or contradiction of Revision 1's original rules.

---

## 25. Future Roadmap

Everything below is explicitly **not** Sprint 27 work. Grouped by why it's deferred.

### 25.1 Explicitly excluded by instruction (regardless of audit severity)

- Benefits Administration (PF passbook, ESI, health insurance nominee, gratuity nomination, NPS)
- Reimbursements & Expense Claims
- Learning Management (active course enrollment/certification tracking)
- Internal Job Posting (IJP)
- Employee Referral submission/tracking
- Mobile Push Notifications (FCM/APNs, device-token registration)
- Offline Mobile Support
- Advanced Analytics (beyond what already exists per-module)
- Kafka/Event Bus migration for ESS/MSS dispatch (note: `KafkaConfig` already exists and is
  provisioned for Organization events — this is a scoping decision, not an infrastructure gap)
- GraphQL migration (including BFF-pattern alternatives for the dashboard)
- Read replicas / CDN implementation for document delivery

### 25.2 Deferred pending further product/design decisions

- Skip-level / indirect-report MSS visibility (audit 2.4) — `ApproverResolver`'s CEO-walk exists
  internally; exposing it as an MSS view requires a product decision on scope and a new recursive
  query, deferred per §3.
- Cross-tenant / matrix-organization manager relationships (audit 4.4) — acknowledged as a future
  requirement, no current architecture supports it.
- Multi-employee-user / Group Company `EmployeeContext` support (audit 4.2) — requires Identity
  module redesign of the single-`employeeId`-per-JWT model; open question (§27).
- Acting/temporary manager assignment (audit 2.6) — distinct from bounded delegation (already
  shipped), this is a structural reassignment feature requiring its own design.
- Tenant-specific ESS/MSS feature-flagging beyond field-level ACL (audit 4.3) — the ACL matrix
  (§12) covers field visibility; broader per-tenant workflow/UX configuration is a larger initiative.
- Data residency / regional document storage (audit 4.5).
- 1:1 meeting/check-in notes, 9-box/calibration team performance views, team budget/headcount
  views (audit 2.7/2.8/2.9).
- Org-wide employee directory search beyond "my direct reports" (audit 1.7, partial).
- New-hire pre-boarding beyond what `com.ewos.offer` already provides (audit 9.1, partial).
- Grievance/complaint filing, certificate/letter self-service requests, WFH workflow, leave
  encashment/comp-off requests, whistleblower hotline, pulse surveys, exit interview
  self-scheduling, Form 16/12BA generation, tax regime switching, shift/roster self-service,
  helpdesk/ticketing, peer recognition, company asset self-service (audit §1, §9, various).
- **Full DPDP Act 2023 compliance infrastructure** (§13.2) — a dedicated, platform-wide compliance
  initiative with its own PRD, not an ESS/MSS feature.
- **Real MFA/step-up authentication** — an Identity-module initiative (§11).
- Device fingerprinting / login anomaly detection (audit 3.11).
- Session management / concurrent-session limits, further JWT hardening beyond Sprint 24G (audit
  3.9/3.10) — Identity-module ownership.
- Webhook/SSE/real-time push for approvals and notifications (audit 7.6) — current design keeps
  the existing poll/fetch (`GET /notifications/mine`) model.
- API Gateway / BFF, partial-response field selection (audit 7.8/7.9) — tied to the GraphQL/BFF
  exclusion above.
- Background/batch job processing for heavy bulk operations (audit 6.7) — no bulk-report feature
  is in Sprint 27's core scope to justify this yet.

---

## 26. Audit Findings Disposition

**[New section, satisfies "carefully review every finding" — every individually-numbered finding
from the audit's Appendix A, with disposition and one-line rationale. Note: the audit's executive
summary states 56 total findings, but its own itemized Appendix A lists 81 uniquely-numbered items
(10+9+11+5+6+8+9+6+11+6 across ten categories) — these two counts do not reconcile with each other
within the source document. Every uniquely-numbered item from Appendix A is addressed below,
regardless of which summary total is "correct."]**

| # | Finding | Disposition | Rationale |
|---|---|---|---|
| 1.1 | Benefits & Insurance self-service | Deferred — Future Roadmap | Explicitly excluded by instruction |
| 1.2 | Reimbursement & expense claims | Deferred — Future Roadmap | Explicitly excluded by instruction |
| 1.3 | Learning self-service | Deferred — Future Roadmap | Explicitly excluded by instruction |
| 1.4 | Tax regime switching | Deferred — Future Roadmap | Payroll calculation-engine scope, not ESS/MSS aggregation |
| 1.5 | Form 16/12BA/comp letters | Deferred — Future Roadmap | No existing generator; new scope |
| 1.6 | Shift/roster self-service | Not Applicable | No such module exists in EWOS |
| 1.7 | Org chart/directory | Accepted (partial) | Direct-report roster = Sprint 27; full directory = Future |
| 1.8 | Helpdesk/ticketing | Deferred — Future Roadmap | No such module exists |
| 1.9 | Peer recognition | Deferred — Future Roadmap | Engagement feature, not core ESS/MSS |
| 1.10 | Company asset self-service | Deferred — Future Roadmap | No such module exists |
| 2.1 | Delegation of authority | **Corrected — Already Exists** | `WorkflowDelegationService`/Controller verified in codebase |
| 2.2 | Team leave calendar | Accepted (stretch) | Elevated to Sprint 27E "if time permits" |
| 2.3 | Bulk approval actions | Accepted | Small addition to already-planned Approvals Inbox |
| 2.4 | Skip-level visibility | Deferred — Future Roadmap | Reaffirmed exclusion, no code precedent |
| 2.5 | Team attendance dashboard | Accepted | Detail added to already-planned FR-9 |
| 2.6 | Acting/temporary manager | Deferred — Future Roadmap | Structural change, distinct from delegation |
| 2.7 | 1:1 meeting notes | Deferred — Future Roadmap | New capability, no precedent |
| 2.8 | Team performance summary/9-box | Accepted (partial) / Deferred (9-box) | Current-cycle status already covered; 9-box is new analytics |
| 2.9 | Team budget/headcount | Not Applicable | No such module exists |
| 3.1 | DPDP compliance | **Corrected + Accepted (minimal)** | Audit's "existing infra" claim is false for EWOS; minimal proportionate scope added (§13) |
| 3.2 | Field-level data masking | Accepted | New ACL matrix (§12) |
| 3.3 | Comprehensive audit logging | Accepted | Expanded §14 |
| 3.4 | MFA/2FA | **Corrected + Accepted (interim control only)** | No MFA infra exists in EWOS; real MFA deferred to Identity module, interim email-notice control added |
| 3.5 | Rate limiting | Accepted (reuse) | Reuses existing `InMemoryRateLimiter`, not new infra |
| 3.6 | MSS target ID validation | Accepted | Defense-in-depth check specified (§5.3) |
| 3.7 | Data retention/purging | Accepted (light) | Policy documented, small scheduled-sweep addition |
| 3.8 | Encryption at rest | Accepted (reuse) | Reuses existing `BankAccountFieldEncryptor` |
| 3.9 | Session management | Deferred — Identity module | Not ESS/MSS scope |
| 3.10 | JWT token strategy | Deferred — Identity module | Sprint 24G already hardened refresh-token reuse |
| 3.11 | Device fingerprinting | Deferred — Future Roadmap | Low severity, new capability |
| 4.1 | Tenant isolation for `Employee.manager` | Accepted | Explicit check added (Business Rule 10) |
| 4.2 | Multi-employee users (group co.) | Deferred — Future Roadmap | Identity-model redesign, open question |
| 4.3 | Tenant-specific config | Accepted (partial via ACL matrix) | Broader config Future |
| 4.4 | Cross-tenant manager relationships | Deferred — Future Roadmap | Audit itself calls this future work |
| 4.5 | Data residency | Deferred — Future Roadmap | Infra-level, tied to read-replica/CDN exclusion |
| 5.1 | Monolithic dashboard bad for mobile | Accepted | Resolved via widgetization (§5.1) |
| 5.2 | Push notifications | Deferred — Future Roadmap | Explicitly excluded by instruction |
| 5.3 | Offline capability | Deferred — Future Roadmap | Explicitly excluded by instruction |
| 5.4 | Mobile auth patterns | Deferred — Future Roadmap | Tied to mobile-app exclusion |
| 5.5 | Mobile-specific features | Deferred — Future Roadmap / Not Applicable | No precedent anywhere in platform |
| 5.6 | Responsive document handling | Deferred — Future Roadmap | Low severity |
| 6.1 | Dashboard scalability time-bomb | Accepted | Resolved via widgetization + caching (§5.1, §15) |
| 6.2 | Caching strategy | Accepted (reuse) | Reuses existing `RedisConfig` |
| 6.3 | Read replica utilization | Deferred — Future Roadmap | Explicitly excluded by instruction |
| 6.4 | Connection pool tuning | Accepted (light) | Config review, not new build (§15) |
| 6.5 | CDN for documents | Deferred — Future Roadmap | Explicitly excluded by instruction |
| 6.6 | Pagination for team lists | Accepted | `Pageable`, reused convention |
| 6.7 | Background job processing | Deferred — Future Roadmap | No bulk feature in core scope to justify it yet |
| 6.8 | Horizontal scaling | Accepted (confirmed) | Already true by construction, documented not built |
| 7.1 | Dashboard REST anti-pattern | Accepted | Same resolution as 6.1; widget endpoints, not GraphQL/BFF |
| 7.2 | Idempotency keys | Accepted | New header requirement, low cost |
| 7.3 | Pagination/sorting/filtering spec | Accepted (reuse) | Existing `Pageable` convention mandated |
| 7.4 | OpenAPI/Swagger requirement | Accepted (confirmed) | Already existing convention, no new work |
| 7.5 | API versioning strategy | Accepted (light) | Documented: follow existing `/api/v1/` convention |
| 7.6 | Webhook/SSE/real-time | Deferred — Future Roadmap | Tied to push-notification exclusion |
| 7.7 | Standardized error envelope | Accepted (confirmed) | Already exists (`GlobalExceptionHandler`/`ApiError`), mandated for new endpoints |
| 7.8 | API Gateway/BFF | Deferred — Future Roadmap | Tied to GraphQL/BFF exclusion |
| 7.9 | Partial response/field selection | Deferred — Future Roadmap | Tied to GraphQL exclusion, low severity |
| 8.1 | DPDP infra reuse | **Corrected + Accepted (minimal)** | Same correction as 3.1 |
| 8.2 | PDF generation duplicated | Accepted (scope upgraded) | Increment 27A now extracts a real shared utility, not just a font backport |
| 8.3 | Audit trail infra reuse | Accepted | Mandated `AuditableEntity` reuse (§14) |
| 8.4 | Event bus for cross-module comms | **Corrected + Deferred** | `KafkaConfig` already exists (feature-flagged); wiring ESS/MSS onto it is explicitly out of Sprint 27 scope regardless, per instruction |
| 8.5 | Shared pagination/filtering utilities | Accepted (light) | Existing `Pageable` convention is the answer, no new utility |
| 8.6 | Shared response envelope/DTO | Accepted (light) | `ApiError` covers errors; success shapes stay as plain DTOs, consistent with platform |
| 9.1 | Pre-boarding self-service | **Corrected (partial) + Deferred (rest)** | `com.ewos.offer` already covers offer/pre-boarding handoff |
| 9.2 | Final settlement tracking (ESS) | Accepted | Pure reuse of existing F&F-by-resignation endpoint (§5.6) |
| 9.3 | Grievance/complaint filing | Deferred — Future Roadmap | No such module exists |
| 9.4 | Certificate/letter requests | Deferred — Future Roadmap | No active-employee generator exists |
| 9.5 | WFH/remote work request | Deferred — Future Roadmap | New workflow, no precedent |
| 9.6 | Leave encashment/comp-off | Deferred — Future Roadmap | Leave module's own scope |
| 9.7 | Internal Job Posting | Deferred — Future Roadmap | Explicitly excluded by instruction |
| 9.8 | Referral submission | Deferred — Future Roadmap | Explicitly excluded by instruction |
| 9.9 | Whistleblower/ethics hotline | Deferred — Future Roadmap | No precedent |
| 9.10 | Pulse surveys | Deferred — Future Roadmap | No precedent |
| 9.11 | Exit interview self-scheduling | Deferred — Future Roadmap | Low severity, new scope |
| 10.1 | Phased plan with dependencies | Accepted | Delivered as §25/§27's Sprint 27A–27E breakdown |
| 10.2 | FR-11 should be hotfix not feature | Accepted | Reclassified (§4.1) |
| 10.3 | MVP vs. full scope (MoSCoW) | Accepted | Applied throughout §27 |
| 10.4 | Increment 5/My Profile risky, late | Accepted (light) | Spike/estimation story added at start of 27D |
| 10.5 | Increment 4 parallelization | Accepted | Noted explicitly (§5.5, §27) |
| 10.6 | Increment 8 contingency | Accepted | Explicit "if time permits" tag (§4.2, §8) |

---

## 27. Implementation Roadmap — Sprint 27A–27E

Each sub-sprint below is independently shippable. MoSCoW tags per finding 10.3.

### Sprint 27A — Foundation & Security Baseline

**Objectives:** stand up everything later sub-sprints depend on; close the highest-severity
security/multi-tenancy gaps before any new employee-facing surface ships.

**Features (Must-have unless noted):**
- Hotfix (ships first, not counted as feature work per finding 10.2): backport the Unicode
  `PDType0Font`/FreeSans fix from `ExitDocumentPdfGenerationService` into
  `PayslipPdfGenerationService`.
- Extract a genuine shared `com.ewos.shared` PDF text-layout utility (upgraded scope, finding 8.2).
- `EmployeeRepository.findAllByManagerId` (paginated).
- Tenant-isolation validation on `Employee.manager` relationships (Business Rule 10).
- Field-level ACL matrix: `mss_field_visibility_config` table + service, default-deny (§12).
- Register `EMPLOYEE_PROFILE_CHANGE` workflow subject type.
- Wire `InMemoryRateLimiter` into a reusable ESS/MSS filter/interceptor; define per-endpoint limits.
- Cross-employee access-logging helper (used by every MSS read in later sub-sprints).
- Privacy-notice-acknowledgment field + minimal DPDP hook (§13.1) (Should-have).
- Document HikariCP pool-sizing target and Redis TTL conventions for later widget work (Should-have).

**Dependencies:** Employee module (schema), Workflow module (subject type), Identity module
(rate limiter reuse), Payroll/Exit modules (PDF utility extraction touches both).

**Acceptance Criteria:**
- `findAllByManagerId` returns correct, paginated results and is covered by an integration test
  against the existing partial index.
- A request for a field with no ACL config row returns `false` (masked) — verified by test.
- Cross-tenant `Employee.manager` assignment is rejected at the service layer — verified by test.
- Shared PDF utility renders Devanagari/Indian-name text correctly from both Payslip and Exit
  document paths — verified by the existing font-rendering regression test pattern, extended to
  Payroll.
- Rate limiter interceptor returns `429` past the configured threshold — verified by test.
- Zero changes to any existing module's business logic outside the stated foundation work.

---

### Sprint 27B — Unified Approvals Inbox + Delegation Surfacing (MSS)

**Objectives:** ship the highest-business-value, lowest-risk MSS capability — pure reuse of the
existing workflow engine, including the delegation mechanism the audit didn't know existed.

**Features (Must-have unless noted):**
- `GET /api/v1/manager-self-service/approvals` — wraps `WorkflowTaskController.myTasks`, cursor
  paginated, enriched with subject-employee name and module/type.
- Act-through (approve/reject/claim) for Leave and Performance tasks.
- Read-only summary cards with deep links for Timesheet/Probation/Requisition tasks (unchanged
  from Revision 1's §6/§7 position).
- Surface existing `WorkflowDelegation` capability in the inbox UI/API ("delegate my approvals",
  "acting for [X]" banner) — zero new backend.
- Bulk act endpoint (`POST .../approvals/bulk-act`) with per-item result reporting (§17) (Should-have).
- `Idempotency-Key` support on all new POST endpoints.
- New `NotificationType` values + `EssMssNotificationEventListener` for approval-pending/approved
  events.

**Dependencies:** Workflow module (task/delegation APIs, unchanged), Notification module (listener
pattern), Sprint 27A's rate limiter and access-logging helper.

**Acceptance Criteria:**
- A manager sees Leave and Performance tasks assigned to them in one paginated list.
- Approving/rejecting from the inbox produces the identical outcome as the existing per-module
  endpoint — verified by integration test asserting no divergent behavior.
- A manager with an active delegation from a peer sees and can act on the peer's tasks; the
  banner correctly identifies whose inbox is being acted on.
- Bulk act on 10 items where 1 is invalid returns 9 successes + 1 per-item error, not a whole-batch
  failure.
- Every approval action produces the existing `WorkflowTaskService` audit trail entry — no gap.

---

### Sprint 27C — My Team (MSS Roster + Drill-Down) + MSS Parity Extensions

**Objectives:** give managers team visibility, governed by the field-level ACL matrix from 27A;
extend the "team-scoped read" pattern (already proven in Leave/Performance) to the remaining
modules.

**Features (Must-have unless noted):**
- `GET /api/v1/manager-self-service/team` (paginated roster).
- `GET /api/v1/manager-self-service/team/{employeeId}/summary` (drill-down), field-masked per §12,
  every access logged per §14.
- MSS parity read endpoints added to Attendance (concrete present/absent/late/on-leave counts, per
  finding 2.5), Exit, Onboarding, Goals, Competency, Development Plan self-service controllers —
  **parallelized across module owners** (finding 10.5), not built sequentially by one team.

**Dependencies:** Sprint 27A's `findAllByManagerId`, field-level ACL matrix, and access-logging
helper. Attendance, Exit, Onboarding, Goals, Competency, Development Plan module owners (parallel
work streams).

**Acceptance Criteria:**
- Roster returns only true direct reports (`Employee.manager == callerEmployeeId`), same-tenant,
  active-status filtered by default — verified by test including a deliberately-planted
  cross-tenant record that must not appear.
- Drill-down correctly masks salary/bank/PAN/DOB/address unless the tenant's ACL config explicitly
  allows it — verified by test with both configurations.
- Every one of the six MSS parity endpoints returns team-scoped (not company-wide) data and is
  independently testable/deployable — no cross-module coupling introduced.
- Every drill-down and parity-read access produces a cross-employee access-log entry.

---

### Sprint 27D — My Profile (ESS) + Minimal DPDP Compliance Hooks

**Objectives:** deliver the one genuinely new domain surface in this PRD — profile self-service —
with the interim security compensating control and minimal privacy-notice behavior from §11/§13.

**Features (Must-have unless noted):**
- Estimation spike (finding 10.4) at sub-sprint start: confirm field list, ACL defaults, and
  workflow-integration approach before committing detailed stories.
- `GET/PUT /api/v1/employees/self-service/profile` — direct write for non-sensitive fields.
- Sensitive-field change-request flow via `EMPLOYEE_PROFILE_CHANGE` workflow subject type
  (27A groundwork).
- New sensitive fields encrypted at rest via `BankAccountFieldEncryptor` pattern.
- Email notification (existing `NotificationService`) on every sensitive-field change
  request/approval, as the interim fraud-detection compensating control (§11) (Should-have — if
  slipped, sensitive-field changes still require approval, just without the extra notice, so this
  degrades safely rather than blocking).
- Privacy notice display + acknowledgment on first profile access (§13.1).

**Dependencies:** Sprint 27A's workflow subject type, ACL matrix (governs which fields are
"sensitive" vs. directly editable), encryption pattern, Notification module.

**Acceptance Criteria:**
- Non-sensitive field edits apply immediately and are captured via standard `AuditableEntity`
  `updated_by`/`updated_at`.
- Sensitive field edits never apply directly — always produce a pending change request, verified
  by test attempting a direct write and asserting rejection.
- A sensitive-field change request/approval sends an email to the employee's registered address —
  verified by test (mocked notification sender).
- Privacy notice is shown and acknowledgment recorded on first access, not shown again on
  subsequent visits — verified by test.

---

### Sprint 27E — Widgetized Dashboard, Document Center, Stretch Items

**Objectives:** ship the aggregation-dependent capabilities last, since they consume outputs of
27B/27C/27D; close out with the lowest-urgency convenience feature and the explicitly-stretch item.

**Features:**
- Widget endpoints (§5.1): leave, payroll, performance, goals, notifications, approvals — each
  independently cacheable (Redis, TTL 60–300s) and independently timeout-bounded (Must-have).
- Document Center (`GET /api/v1/self-service/documents`) — payslips + exit letters, **plus F&F
  settlement status** via the existing by-resignation endpoint (finding 9.2) (Should-have).
- Team Leave Calendar — **stretch, if time permits, not a hard Sprint 27 commitment** (finding
  10.6) (Could-have).

**Dependencies:** Sprint 27B (approvals count widget), 27C (team data for calendar), 27D (profile
widget data), Sprint 27A (Redis wiring, shared PDF utility for documents).

**Acceptance Criteria:**
- Each widget endpoint responds independently — a simulated failure/timeout in one module's widget
  does not affect any other widget's response.
- Cache hit vs. miss latency meets the targets in §15, verified by load test.
- Document Center correctly omits F&F status for employees who haven't exited, and correctly shows
  it (read-only) for those who have.
- If Team Leave Calendar does not ship in 27E, its omission does not block sign-off on the rest of
  27E's acceptance criteria (per its explicit Could-have/stretch classification).

---

## 28. Open Questions for Sprint 27 Kickoff

Revision 1's four open questions are preserved. Added:

5. **[Audit-driven]** What are the tenant defaults for the field-level ACL matrix (§12) at
   rollout — does every existing tenant start with everything masked (safest, but may surprise
   tenants expecting current informal manager visibility), or is there a one-time migration
   decision needed per tenant? Needs a decision before Sprint 27C ships.
6. **[Audit-driven]** Who owns scoping the full DPDP compliance initiative (§13.2) as its own
   future workstream — this PRD flags the gap honestly but does not assign it an owner or sprint.
7. **[Audit-driven]** Who owns the Identity-module MFA initiative this PRD's interim compensating
   control (§11) is standing in for — needs a named owner so the interim control doesn't become
   permanent by default.
