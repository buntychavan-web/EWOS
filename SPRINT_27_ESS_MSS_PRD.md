# Sprint 27 — Employee Self Service (ESS) & Manager Self Service (MSS)

## Product Requirements Document

**Status:** Draft for Sprint 27 planning. No code has been written against this document.
**As of:** 2026-08-06, analyzed against `main` @ `1fbe12b` (post Sprint 26/26A).
**Companion documents:** [`EWOS_PRODUCT_ROADMAP.md`](./EWOS_PRODUCT_ROADMAP.md) (module inventory
this PRD extends), [`PROJECT_STATUS.md`](./PROJECT_STATUS.md) (engineering history),
[`RELEASE_NOTES_SPRINT_26.md`](./RELEASE_NOTES_SPRINT_26.md) (most recent prior sprint).

---

## 1. Current State Analysis

This section is a factual audit of `main` as it exists today, not a proposal. Every claim below
was verified directly against source (file/line references given where useful).

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
| Notification | `GET /notifications/mine`, `GET /notifications/mine/unread-count`, `POST /notifications/{id}/read` | Own in-app notification inbox — **already generic, module-agnostic** |

**Gap:** the `employee` module itself has **no self-service controller at all**. An employee cannot
view or request an update to their own core profile (personal details, emergency contact, address)
anywhere in the current API surface — every other module's self-service depends on `Employee`
existing, but `Employee` has no self-service front door of its own.

### 1.2 Existing MSS features

Manager Self Service is **not** a first-class concept anywhere in the codebase. There is no
`com.ewos.mss` module, no `manager-self-service` base path, and no reusable "list my direct
reports" query. Two modules have organically grown manager-facing actions bolted onto their *ESS*
controller, scoped server-side via `Employee.manager`:

- **Leave** — `LeaveSelfServiceController.pendingForMyReports` (`GET
  /leave/self-service/reports/pending`): paginated SUBMITTED requests from the caller's own direct
  reports.
- **Performance** — `PerformanceSelfServiceController.pendingMyManagerReview` (`GET
  .../pending-manager-review`) and `submitMyManagerAssessment` (`POST
  /appraisals/{id}/manager`): appraisals awaiting the caller's manager assessment, and submission of
  that assessment, for a direct report.

No equivalent exists in Attendance, Exit, Onboarding, Goals, Competency, or Development Plan.

Separately, **admin-tier approval endpoints already exist** in each module's regular (non-self-
service) controller — `LeaveRequestController.approve/reject`, `TimesheetController.approve/reject`,
`AppraisalController.submitManager/.approve/.reject`, `ProbationController.recordManagerReview/
.approveConfirmation/.rejectConfirmation`, `JobRequisitionController.approve/reject` — but these are
gated by a **static permission** (`LEAVE_APPROVE`, `ATT_APPROVE`, `PERF_APPROVE`,
`PROBATION_APPROVE`, `RECRUITMENT_APPROVE`), not a "you are this employee's manager" check. Anyone
holding the permission — typically an HR admin role — can act on any employee's record. Whether the
actual line manager is the one taking the action is enforced only indirectly, at *assignment* time,
by the workflow engine's `ApproverResolver` (see §1.3) — not at *action* time.

### 1.3 APIs already available (reuse-relevant)

- **Workflow engine — `ApproverResolver`** already resolves a `"MANAGER"` approver strategy
  (`Employee.manager` of the subject employee) and a `"CEO"` strategy (walks the manager chain to
  the top, bounded at 20 hops), alongside role-based strategies (`HOD`, `HR`, `FINANCE`,
  `CUSTOM:<name>`). Leave, Timesheet, Exit, Probation, and Recruitment approvals already run
  through this engine with a configurable `WorkflowDefinition`/`WorkflowState`. This is a
  ready-made, production-proven "who is this employee's manager" resolution strategy.
- **`WorkflowTaskController.myTasks`** (`GET /api/v1/workflow/tasks/mine?actorId=`) already
  aggregates open approval tasks assigned to a given actor **across every workflow-backed module**
  at the task level. It is not currently surfaced through any self-service or manager-facing
  screen.
- **`NotificationController`** (`/api/v1/notifications/mine`, `.../unread-count`,
  `.../{id}/read`) is already a generic, module-agnostic in-app inbox — every module (Payroll,
  Performance, Goals, Competency, Recruitment, ATS, Interview, Offer, Onboarding, Identity,
  Tenancy) already funnels notifications into it via a consistent
  `@TransactionalEventListener(phase = AFTER_COMMIT)` pattern calling `NotificationService.send`.
- **`Employee.manager`** (self-referential `@ManyToOne`, column `manager_employee_id`) is a solid,
  already-indexed relationship — `V10__employee_engine.sql` created a partial index
  `ON employees (manager_employee_id) WHERE deleted_at IS NULL AND manager_employee_id IS NOT
  NULL`. `EmployeeRepository.countDirectReports(UUID)` exists, but there is **no method that
  returns the list** of direct reports, and **no indirect/skip-level traversal** anywhere in the
  codebase (the only manager-chain traversal today is `ApproverResolver`'s upward CEO walk).
- **Dashboard aggregations already exist per-module** (`PayrollDashboardResponse`,
  `GoalDashboardResponse`, `PerformanceDashboardResponse`, `OrgUnitProgressResponse`,
  `OnboardingDashboardController`) but none is self-scoped to "my team" except `GoalDashboardResponse`'s
  team/department dimensions, and none is unified across modules.
- **Document generation** exists twice, independently: `PayslipPdfGenerationService` (Payroll,
  Standard-14 Helvetica — throws on non-Latin-1 characters) and `ExitDocumentPdfGenerationService`
  (Exit, Sprint 26A — embedded `PDType0Font`/FreeSans, full Unicode). There is no shared
  `com.ewos.shared` PDF utility; the Unicode fix was applied to Exit only, not backported to
  Payroll.

### 1.4 UI / backend gaps

- **No unified ESS or MSS entry point.** An employee today must know which of 8 different
  `self-service` base paths to call for which fact; there is no aggregating "home" API.
  There is also **no frontend of any kind in this repository** — no `frontend/`, `ui/`, `web/`, or
  `.tsx`/`.jsx` files anywhere. Screen requirements in this PRD are forward-looking design, not an
  audit of an existing UI.
- **No "my team" concept at all** outside Leave's and Performance's narrow, module-specific bolt-ons.
  A manager cannot see a simple roster of their direct reports anywhere in the API.
- **No cross-module "my approvals" inbox.** A manager must check Leave, Timesheet, Performance,
  Probation, and Requisition approvals separately, even though the underlying workflow engine
  already tracks all of them uniformly via `WorkflowTaskController`.
- **No employee profile self-service.** Personal details, address, emergency contact — nothing.
- **No document center.** Payslips and exit letters are each fetched from their own module; there
  is no single "my documents" view.
- **No mobile readiness.** No push-notification channel, no device-token storage, no
  mobile-specific API versioning. `pom.xml` carries no FCM/APNs/mobile SDK dependency. Everything
  is a flat `/api/v1/...` REST surface assumed to be consumed by a single web frontend that does
  not yet exist.
- **Manager authorization is permission-based, not relationship-based**, on every *admin-tier*
  approval endpoint. This is an intentional, existing design (it lets HR admins act broadly) but
  means there is currently no way to *restrict* an approval to "only the actual reporting manager"
  if a tenant wants that — worth flagging as a design decision this PRD must make explicitly (see
  §4 and §6).

---

## 2. Module Objectives

1. Give every employee **one place** to see and act on everything that already exists across
   Leave, Attendance, Payroll, Performance, Goals, Exit, Onboarding, Competency, and Development
   Plan self-service — without rebuilding any of that underlying business logic.
2. Give every manager **one place** to see their team and act on everything currently awaiting
   their approval — by exposing the workflow engine's existing `MANAGER` approver strategy and
   task-assignment machinery through a manager-facing surface, not by inventing new approval logic.
3. Close the two genuine backend gaps this exposes: an employee profile self-service surface, and
   a reusable "list my direct reports" capability.
4. Do this as a thin aggregation/UX layer over existing, already-tested module services wherever
   possible, adding new domain logic only where a real gap exists (profile self-service, team
   roster, cross-module approval/document/notification aggregation).

---

## 3. User Roles

| Role | Definition | Scope |
|---|---|---|
| **Employee** | Any authenticated user with an `Employee` record. Base role — everyone is at least this. | Own records only, via `EmployeeContext.currentEmployeeId()`. |
| **Manager** | Not a separate assignable role — derived at query time from `Employee.manager` pointing at the caller. Any employee with ≥1 direct report is automatically a "manager" for MSS purposes. | Own records (as Employee) + direct reports' records exposed by MSS endpoints. |
| **HR Admin** | Existing permission-holding roles (`LEAVE_APPROVE`, `PERF_APPROVE`, etc.) — unchanged by this PRD. | Whatever their existing permissions already grant, company-scoped via `ClientAccessGuard`. |
| **System** | Scheduled jobs / event listeners (e.g. notification dispatch, reminder jobs). | Internal only, no ESS/MSS-facing surface. |

**Explicit design decision:** MSS in Sprint 27 is defined as **direct reports only**
(`Employee.manager == currentEmployeeId`), matching the one pattern that already exists in Leave
and Performance. Indirect/skip-level reports and a full org-chart traversal are out of scope for
Sprint 27 (see §14) because no such traversal exists anywhere in the codebase today and building it
(a recursive CTE, unbounded depth) is materially riskier than the direct-reports case, which is a
single indexed-column filter.

---

## 4. Functional Requirements

Grouped by capability area. "Reuses" cites the existing service/endpoint being wrapped; "New"
marks genuinely new logic.

### 4.1 Unified ESS/MSS Home Dashboard
- FR-1: An employee sees, in one call, their pending leave/timesheet/appraisal actions, latest
  payslip summary, onboarding/probation status if applicable, unread notification count, and (if a
  manager) a count of pending team approvals. **New** aggregation endpoint; **reuses**
  `PayrollSelfServiceService.dashboard`, `GoalDashboardResponse`, `PerformanceDashboardResponse`,
  `NotificationService.unreadCount`, `WorkflowTaskController.myTasks` count.

### 4.2 My Profile (ESS)
- FR-2: An employee can view their own core profile (name, contact details, address, emergency
  contact, employment summary). **New** — `employee` module has no self-service today.
- FR-3: Non-sensitive fields (phone, personal email, address, emergency contact) are editable
  directly. **New.**
- FR-4: Sensitive fields (legal name, date of birth, bank details — bank details already live in
  Payroll's `EmployeeBankAccountController`) require HR approval before taking effect, routed
  through the existing workflow engine using the same `WORKFLOW_SUBJECT_TYPE` pattern Exit
  established in Sprint 26 (`ExitService.WORKFLOW_SUBJECT_TYPE`), not a new approval mechanism.
  **New request-and-approve flow, reusing the workflow engine.**

### 4.3 My Team (MSS)
- FR-5: A manager can list their direct reports (name, title, org unit, employment status).
  **New** — requires a new `EmployeeRepository.findAllByManagerId` method; the existing
  partial index on `manager_employee_id` already supports this query efficiently.
- FR-6: A manager can drill into one direct report's summary (leave balance, latest attendance
  status, current goals/appraisal status) — **reuses** each module's existing admin-read APIs,
  scoped to the selected employee, behind a manager-relationship check rather than a broad
  admin permission.

### 4.4 Unified Approvals Inbox (MSS)
- FR-7: A manager sees every pending approval assigned to them — leave, timesheet, appraisal,
  probation, requisition — in one list, each enriched with subject-employee name and module/type.
  **New** aggregation, **reuses** `WorkflowTaskController.myTasks` (`GET /workflow/tasks/mine`)
  as the underlying data source; no new authorization or assignment logic.
- FR-8: A manager can act (approve/reject/claim) on a task directly from the unified inbox.
  **Reuses** `WorkflowTaskService.claim`/`.complete` exactly as today — the inbox is a view, not a
  new authority.

### 4.5 MSS parity extensions
- FR-9: Extend Attendance, Exit, Onboarding, Goals, Competency, and Development Plan self-service
  controllers with a "my team" scoped read, mirroring the Leave/Performance precedent (e.g.
  `GET /attendance/self-service/reports/team` for team attendance summary). **New**, but each
  instance is a small, module-local addition following an established pattern — not new
  architecture.

### 4.6 Document Center (ESS)
- FR-10: An employee sees all their generated documents (payslips, exit letters if applicable) in
  one list with download links. **New** aggregation; **reuses** `PayslipSelfServiceController`'s
  PDF endpoint and Exit's document-generation endpoint.
- FR-11: Standardize PDF generation on the Unicode-capable `PDType0Font`/FreeSans approach Exit
  already uses, backporting the fix to `PayslipPdfGenerationService` (currently Standard-14,
  throws on non-Latin-1 names). This closes a known, documented gap (`RELEASE_NOTES_SPRINT_26.md`
  "Known limitations").

### 4.7 Notifications Center (ESS/MSS)
- FR-12: ESS/MSS home surfaces the existing `/api/v1/notifications/mine` feed directly — **no new
  endpoint needed**, this is a pure reuse.
- FR-13: Add `MSS_APPROVAL_PENDING`/`MSS_APPROVAL_REMINDER`-style `NotificationType` values and a
  corresponding `EssMssNotificationEventListener`, following the exact
  `@TransactionalEventListener(phase = AFTER_COMMIT)` pattern every other module already uses.
  **New**, but zero new infrastructure.

---

## 5. Business Rules

1. MSS scope is **direct reports only** (`Employee.manager == currentEmployeeId`) in Sprint 27.
   Skip-level/indirect reports are out of scope (see §3, §14).
2. MSS is a **view and action layer over the existing workflow engine**, never a new source of
   authority. A manager can only act on a task that the engine already assigned to them via
   `ApproverResolver`'s `MANAGER` strategy (or an equivalent existing mechanism) — the unified
   inbox does not grant any permission it wouldn't already have granted per-module.
3. Existing admin-tier, permission-based approval endpoints (`LEAVE_APPROVE`, etc.) are
   **unchanged**. This PRD does not narrow HR admins' existing ability to act on any employee's
   record — MSS is additive, not a replacement authorization model.
4. Sensitive profile fields (legal name, date of birth, bank account) are **never** directly
   editable by the employee — they always route through an approval workflow, mirroring how the
   rest of the platform treats sensitive changes (e.g., Exit's clearance/document flows).
5. All ESS reads/writes resolve identity via `EmployeeContext.currentEmployeeId()` — never from a
   header, path parameter, or request body — matching the established, audited security pattern
   (see §9).
6. All MSS reads/writes must additionally verify the target employee is a direct report of the
   caller (`Employee.manager == currentEmployeeId`) **server-side**, on every request — a manager
   ID is never accepted from the client.
7. Every ESS/MSS aggregation endpoint must avoid N+1 queries when fanning out across modules —
   bulk-fetch per module (mirroring Payroll's established `PfEcrFileExporter` bulk-fetch pattern),
   not one query per underlying module per employee.
8. No new business rule in this PRD alters any existing module's own business rules (leave
   accrual, payroll calculation, appraisal lifecycle, etc.) — ESS/MSS surfaces existing rules, it
   does not reinterpret them.

---

## 6. Approval Workflows

No new approval/workflow engine is introduced. Every approval action reachable from MSS in Sprint
27 already runs on `com.ewos.workflow`'s existing generic engine:

- **Leave approval** — `WorkflowDefinition` with `MANAGER` approver strategy, already live.
- **Timesheet approval** — permission-gated today (`ATT_APPROVE`); MSS surfaces it in the unified
  inbox only if/when it is migrated onto the workflow engine (currently a direct
  `TimesheetController.approve` call) — flagged as a **dependency**, not a Sprint 27 blocker: the
  unified inbox can still deep-link to the existing endpoint even before that migration (see
  Increment 4 note in §13).
- **Appraisal manager assessment** — already workflow/state-machine driven
  (`AppraisalLifecyclePolicy`), already has a manager-facing bolt-on (§1.2) that MSS generalizes.
- **Probation manager review** — permission-gated (`PROBATION_RECOMMEND`/`PROBATION_APPROVE`);
  same note as Timesheet.
- **Job requisition approval** — permission-gated (`RECRUITMENT_APPROVE`); same note as Timesheet.
- **My Profile sensitive-field changes (new, FR-4)** — a new `WorkflowDefinition` subject type
  (e.g. `EMPLOYEE_PROFILE_CHANGE`), reusing the exact pattern Exit's `ExitService` established for
  optional multi-level approval: attach a workflow instance if a tenant has one configured for
  this subject type, otherwise fall back to direct HR-admin approval via a simple request/approve
  endpoint pair.

**Consolidated recommendation:** where an approval is still purely permission-gated
(Timesheet, Probation, Requisition) rather than workflow-engine-driven, the unified MSS inbox in
Sprint 27 surfaces it as a **read-only summary card with a deep link** to the existing
module-specific approval screen, not a fully unified one-click action — fully unifying the *action*
requires migrating those three onto the workflow engine, which is out of scope for Sprint 27 (see
§14) and should be scoped as a Sprint 28 candidate once MSS usage data shows it's worth it.

---

## 7. Screen-by-Screen Requirements

Forward-looking design — there is no existing UI in this repository to audit against.

1. **ESS/MSS Home Dashboard** — pending actions summary, latest payslip card, unread notifications,
   (if manager) team approvals count and team headcount. Single aggregation call (§4.1).
2. **My Profile** — view/edit non-sensitive fields; "request change" flow for sensitive fields with
   pending-request status shown inline.
3. **My Leave** — existing Leave self-service surfaced as-is (balances, request form, history).
4. **My Attendance & Timesheets** — existing Attendance self-service surfaced as-is.
5. **My Payslips & Tax Center** — existing Payroll self-service surfaced as-is (payslips, dashboard,
   tax declaration, tax projection).
6. **My Performance** — existing Performance self-service surfaced as-is (self-assessment,
   appraisal status).
7. **My Goals** — existing Goals self-service surfaced as-is.
8. **My Learning & Development** — existing Competency + Development Plan self-service surfaced as-
   is.
9. **My Documents** — new aggregated list: payslip PDFs, exit letters (if applicable), with
   download action.
10. **My Team (MSS)** — roster of direct reports with drill-down to each report's summary card
    (leave balance, attendance status, goal/appraisal status).
11. **Approvals Inbox (MSS)** — unified list across Leave/Performance (one-click act) and
    Timesheet/Probation/Requisition (deep-link, per §6), grouped by module with a pending-count
    badge per module.
12. **Team Leave Calendar (MSS)** — visual calendar of direct reports' approved/pending leave.
    Flagged as a Sprint 28 candidate in §13 (depends on Increment 5's team-scoped Leave read).
13. **Notifications Center** — the existing `/notifications/mine` feed, surfaced as its own screen
    in addition to the home dashboard's summary.

---

## 8. API Reuse Opportunities vs. New APIs

### Reused as-is (no changes required)

| Existing endpoint | Reused for |
|---|---|
| `GET /api/v1/notifications/mine`, `.../unread-count`, `POST .../{id}/read` | Notifications Center, dashboard badge |
| `GET/POST /api/v1/leave/self-service/*` | My Leave screen |
| `GET /api/v1/attendance/self-service/*` | My Attendance screen |
| `GET/POST /api/v1/payroll/self-service/*` | My Payslips & Tax Center |
| `GET/POST /api/v1/performance/self-service/*` | My Performance screen |
| `GET/POST /api/v1/goals/self-service/*` | My Goals screen |
| `GET /api/v1/competencies/self-service/*`, `/development-plans/self-service/*` | My Learning & Development |
| `GET /api/v1/exit/self-service/*` | My Documents (exit letters), profile exit status |
| `GET /api/v1/onboarding/self-service/*` | Home dashboard onboarding status card |
| `GET /api/v1/workflow/tasks/mine?actorId=` | Approvals Inbox data source |
| `LeaveSelfServiceController.pendingForMyReports`, `PerformanceSelfServiceController.pendingMyManagerReview` | Approvals Inbox (already-unified modules) |

### New APIs required

| New endpoint (indicative) | Purpose | Backing change |
|---|---|---|
| `GET/PUT /api/v1/employees/self-service/profile` | My Profile view/edit | New controller; new "profile change request" flow for sensitive fields |
| `GET /api/v1/employees/self-service/profile/change-requests` | Track pending sensitive-field requests | Reuses workflow-instance pattern |
| `GET /api/v1/manager-self-service/team` | My Team roster | New `EmployeeRepository.findAllByManagerId` |
| `GET /api/v1/manager-self-service/team/{employeeId}/summary` | Direct report drill-down | Aggregates existing per-module admin reads, manager-relationship-checked |
| `GET /api/v1/manager-self-service/approvals` | Unified Approvals Inbox | Wraps `WorkflowTaskController.myTasks` + enrichment |
| `GET /api/v1/self-service/dashboard` | Unified home dashboard | Aggregates existing per-module dashboards |
| `GET /api/v1/self-service/documents` | Document Center | Aggregates Payslip + Exit document listings |
| `GET /attendance/self-service/reports/team`, `GET /exit/self-service/reports/team`, etc. | MSS parity extensions (§4.5) | Small additions to each existing self-service controller |

---

## 9. Database Impact

**Deliberately minimal.** No breaking changes; every new capability targets an additive migration.

- **No change** to `employees` table — `manager_employee_id` and its partial index already exist
  (`V10__employee_engine.sql`). `findAllByManagerId` is a new repository method against existing
  schema, not a new column.
- **New, additive:** an `employee_profile_change_requests`-equivalent, OR (preferred, for
  consistency with Exit's Sprint 26 pattern) reuse the existing generic `workflow_instances` table
  by registering `EMPLOYEE_PROFILE_CHANGE` as a new `subjectType`, storing the proposed field
  diff as workflow instance metadata/JSON, exactly as `ExitService.WORKFLOW_SUBJECT_TYPE` does for
  resignation approval. **Recommendation: reuse the generic workflow-instance table; do not add a
  new domain table for this if the generic mechanism's metadata column is sufficient** — this
  avoids a parallel approval-tracking data model.
- **No change** to Payroll, Leave, Attendance, Performance, Goals, Exit, Onboarding, Competency
  schemas — all MSS/ESS reads/writes in Sprint 27 go through those modules' existing services and
  tables unchanged.
- **New `NotificationType` enum values only** (`MSS_APPROVAL_PENDING`, etc.) — no schema change,
  `notification_type` is stored as a string/enum column already sized for growth.
- **Verify before Increment 1:** confirm query-plan cost of `findAllByManagerId` at expected
  scale (a few hundred direct reports max per manager in practice) — the existing partial index
  should make this a non-issue, but this should be confirmed with `EXPLAIN ANALYZE` against a
  representative dataset before considering it done, not assumed.

---

## 10. Security Requirements

1. **Identity resolution:** every new ESS endpoint must resolve the caller via
   `EmployeeContext.currentEmployeeId()` exactly as every existing self-service controller does —
   never from a header, path variable, or request body field. This is the established, audited
   convention (see Sprint 26A P0-3's `AlumniController` fix for the anti-pattern this avoids).
2. **Manager-relationship enforcement:** every new MSS endpoint must verify
   `targetEmployee.manager.id == callerEmployeeId` **server-side**, on every request, never
   trusting a client-supplied manager or team-membership claim.
3. **No new authority surface:** the Approvals Inbox must not grant any manager the ability to act
   on a task the workflow engine did not already assign to them — it is strictly a filtered view
   over `WorkflowTaskService`'s existing assignment/authorization logic.
4. **Tenant isolation:** all new endpoints continue to enforce `ClientAccessGuard`/`TenantContext`
   company-scoping exactly as every existing module does; no exception for aggregation endpoints.
5. **Sensitive-field protection:** legal name, date of birth, and bank details are never
   direct-write fields on the new profile endpoint — always routed through the approval flow
   (§4.2, §6), closing off a class of "employee edits their own bank account to redirect salary"
   risk.
6. **Aggregation endpoints must not leak cross-tenant or cross-manager data** — every per-module
   call an aggregator makes (dashboard, documents, team summary) must pass through that module's
   own existing tenant/company/ownership checks unmodified; the aggregator is a caller, not a
   bypass.
7. **Rate/volume consideration:** the unified dashboard and approvals inbox fan out to multiple
   modules per request — must use bulk queries (§5 rule 7) and should be evaluated for basic
   rate-limiting or caching if usage patterns show hot-path repeated polling (e.g. a mobile client
   polling for badge counts).

---

## 11. Audit Requirements

1. Every MSS write action (approve/reject/claim via the unified inbox) is already captured by
   `WorkflowTaskService`'s existing audit trail (actor, timestamp, decision) — no new audit
   logging is required for actions that flow through the existing engine.
2. New profile-change-request flow (§4.2) must be fully auditable: who requested the change, what
   fields changed (old value → new value), who approved/rejected it, and when — satisfied by
   reusing the generic `workflow_instances`/`AuditableEntity` conventions (`created_by`,
   `updated_by`, timestamps) rather than inventing a parallel audit mechanism.
3. Direct (non-approval) profile edits to non-sensitive fields (§4.2 FR-3) must still be captured
   via the standard `AuditableEntity` `updated_by`/`updated_at` columns already present on every
   entity in the codebase.
4. Aggregation endpoints (dashboard, documents, team summary) are **read-only** and do not
   themselves need new audit records beyond standard request logging (`CorrelationIdFilter`,
   already platform-wide) — audit obligations live with the underlying module whose data is being
   read, not with the aggregator.
5. Any new `NotificationType` values added for MSS/ESS reminders must not log or notify with
   sensitive payload content (per the established `AuditorProvider`/logging pitfalls documented in
   `PROJECT_STATUS.md` §8.5) — notification bodies reference the record, not its sensitive fields.

---

## 12. Mobile Readiness

Per §1.4, the platform today has **zero** mobile-specific infrastructure: no push-notification
channel, no device-token storage, no mobile API versioning, no FCM/APNs dependency in `pom.xml`.

**Sprint 27 position:** design every new ESS/MSS API to be mobile-consumable in principle —
stateless JWT auth, plain JSON REST, no server-side session state, no assumption of a specific
client — but do **not** build push notifications, device-token registration, or a mobile-tuned
response shape in Sprint 27 itself. This is a scoped deferral, not an oversight:

- The unified dashboard, approvals inbox, and notifications endpoints are exactly the surfaces a
  future mobile app would need first — building them now, API-first, is the right investment even
  without a mobile client yet.
- Push notification delivery (the one channel genuinely missing end-to-end) is called out as an
  explicit **Future Roadmap** item in `EWOS_PRODUCT_ROADMAP.md`'s v1.1 scope and should be
  proposed for a dedicated future sprint once a mobile client is actually being planned, not
  spent now against a hypothetical.

---

## 13. Implementation Increments

Each increment is scoped to be a small, independently shippable, fully-tested unit, matching this
codebase's established increment style (see Sprint 26's 7-increment delivery).

**Increment 1 — Foundation**
`EmployeeRepository.findAllByManagerId`; extract a shared `com.ewos.shared` PDF text-layout
utility from `ExitDocumentPdfGenerationService` and backport the Unicode `PDType0Font`/FreeSans fix
into `PayslipPdfGenerationService` (closing the documented Sprint 26A known limitation); register
`EMPLOYEE_PROFILE_CHANGE` as a new workflow subject type (no UI yet — just the domain plumbing).

**Increment 2 — Unified Approvals Inbox (read + act for workflow-engine-backed modules)**
`GET /api/v1/manager-self-service/approvals` wrapping `WorkflowTaskController.myTasks` with
subject-employee enrichment; act-through for Leave and Performance (already workflow/manager-
scoped); read-only summary cards with deep links for Timesheet/Probation/Requisition (§6).

**Increment 3 — My Team (MSS roster + drill-down)**
`GET /api/v1/manager-self-service/team` and `.../team/{employeeId}/summary`, using Increment 1's
`findAllByManagerId` plus manager-relationship-checked reads into Leave/Attendance/
Goals/Performance for the drill-down summary.

**Increment 4 — MSS parity extensions**
Add team-scoped read endpoints to Attendance, Exit, Onboarding, Goals, Competency, and Development
Plan self-service controllers, matching the Leave/Performance precedent (§4.5).

**Increment 5 — My Profile (ESS)**
`GET/PUT /api/v1/employees/self-service/profile`, direct-write for non-sensitive fields,
request-and-approve flow (built on Increment 1's workflow subject type) for sensitive fields.

**Increment 6 — Unified ESS/MSS Home Dashboard**
`GET /api/v1/self-service/dashboard`, aggregating Increments 2/3/5 plus existing per-module
dashboards and the notification unread count.

**Increment 7 — Document Center**
`GET /api/v1/self-service/documents`, aggregating Payslip and Exit document listings, benefiting
from Increment 1's standardized PDF font handling.

**Increment 8 (stretch / Sprint 28 candidate) — Team Leave Calendar & Team Attendance Overview**
Visual/calendar-style MSS aggregations building on Increment 4's team-scoped Leave/Attendance
reads. Deferred if Sprint 27 velocity doesn't allow it — none of Increments 1–7 depend on it.

**Increment 9 (out of Sprint 27, flagged for future roadmap) — Mobile push scaffolding**
Device-token registration + a push channel plugged into `NotificationService`. Explicitly not
started in Sprint 27 (§12, §14).

---

## 14. Out of Scope

- **Native mobile application** — this PRD scopes API readiness only, not a mobile client build.
- **Push notifications / device-token infrastructure** — flagged as future roadmap (§12,
  Increment 9), not attempted in Sprint 27.
- **Skip-level / indirect-report org-chart traversal** — MSS in Sprint 27 is direct-reports-only
  (§3). A recursive manager-chain-descent query is a materially larger and riskier piece of work
  than the direct-reports case and has no precedent anywhere in the codebase today.
- **Migrating Timesheet/Probation/Requisition approvals onto the workflow engine** — these remain
  permission-gated, deep-linked from the unified inbox rather than fully unified (§6). A full
  migration is a candidate for a future sprint once MSS usage data justifies the investment.
- **Any change to underlying module business logic** — leave accrual rules, payroll calculation,
  appraisal lifecycle rules, etc. are explicitly untouched; ESS/MSS is an aggregation/UX layer,
  not a rules rewrite.
- **Analytics/BI-style reporting on top of ESS/MSS usage** — `com.ewos.analytics` remains a
  reserved, unbuilt namespace per `EWOS_PRODUCT_ROADMAP.md`; no dependency on it here.
- **Multi-language/localization** of any new screen or notification template.
- **Company Configuration backlog items** (profile version history, statutory registrations,
  company bank accounts, policy assignments, shared-service team assignments) — unrelated scope,
  tracked separately in `COMPANY_CONFIGURATION_BACKLOG.md`.
- **Real-time (WebSocket/chat-style) collaboration features** — the notification model remains
  poll/fetch-based (`GET /notifications/mine`), not push-streamed, in Sprint 27.

---

## 15. Reusable Components — Cross-Module Inventory

| Source module | Reusable component | How ESS/MSS uses it |
|---|---|---|
| **Payroll** | `PayrollSelfServiceService.dashboard`, `PayslipSelfServiceController`, `EmployeeTaxDeclarationController` | Surfaced as-is in My Payslips & Tax Center; dashboard feeds the unified home screen |
| **Leave** | `LeaveSelfServiceController` (full CRUD lifecycle), `pendingForMyReports` (existing MSS precedent) | Surfaced as-is; the precedent pattern for Increment 4's parity extensions |
| **Attendance** | `AttendanceSelfServiceController` (read pattern) | Surfaced as-is; extended with a team-scoped read in Increment 4 |
| **Exit** | `ExitSelfServiceController`, `ExitDocumentPdfGenerationService` (Unicode `PDType0Font` pattern), `ExitService.WORKFLOW_SUBJECT_TYPE` (optional-workflow-attachment pattern) | Document generation pattern backported to Payroll (Increment 1); workflow-subject-type pattern reused for profile-change approval (§4.2) |
| **Recruitment** | `JobRequisitionController.approve/reject` (permission-gated approval pattern) | Reference pattern for the "deep-link, not full unification" treatment in §6 |
| **Workflow** | `ApproverResolver` (`MANAGER`/`CEO`/role strategies), `WorkflowTaskController.myTasks`, `WorkflowTaskService.claim/complete` | The entire backbone of the Approvals Inbox (Increment 2) — no new approval logic written |
| **Documents** (Exit + Payroll PDF generation) | PDFBox content-stream approach, to be consolidated into one `com.ewos.shared` utility | Increment 1 and Increment 7 |
| **Notifications** | `NotificationService.send`, `NotificationController` (`/mine`, `/unread-count`, `/{id}/read`), the `@TransactionalEventListener(AFTER_COMMIT)` per-module listener pattern | Notifications Center is pure reuse; only new `NotificationType` values + one new listener are added |

---

## 16. Recommended Implementation Order

1. **Increment 1 (Foundation)** — unblocks everything else; lowest risk (a repository method, an
   extraction/backport, a workflow subject-type registration — no new user-facing behavior yet).
2. **Increment 2 (Approvals Inbox)** — highest business value for the lowest risk: it is pure
   reuse of a proven, already-in-production engine (`WorkflowTaskController`/`ApproverResolver`),
   ships a manager-visible feature fast, and needs nothing from Increments 3–7.
3. **Increment 3 (My Team)** — a natural next step once approvals exist; gives the inbox richer
   context (who reports to whom) and stands alone as its own screen.
4. **Increment 4 (MSS parity extensions)** — mechanical, low-risk repetition of an established
   pattern (Leave/Performance) across five more modules; best done once Increment 3's roster
   endpoint exists to reuse for "who is on my team" context.
5. **Increment 5 (My Profile)** — the one genuinely new domain surface (employee module had zero
   self-service before this); sequenced after the lower-risk reuse work so the team has Sprint 27
   velocity data before taking on the riskiest new-build item.
6. **Increment 6 (Unified Dashboard)** — deliberately last among the core increments because it
   aggregates the outputs of 2, 3, and 5; building it earlier would mean reworking it as those
   land.
7. **Increment 7 (Document Center)** — lowest urgency (payslip/exit-letter access already exists
   per-module today; this is convenience aggregation), scheduled last within Sprint 27's core
   scope.
8. **Increment 8 (Team calendar/attendance visuals)** and **Increment 9 (mobile push)** — explicitly
   sequenced into Sprint 28+ per §13, not committed to Sprint 27.

**Rationale summary:** front-load pure-reuse, low-risk, high-visibility work (Approvals Inbox) to
prove the aggregation pattern early; do genuinely new domain work (My Profile) once that pattern is
validated; finish with the increment that depends on everything else (Dashboard) and the
lowest-urgency convenience feature (Documents).

---

## 17. Open Questions for Sprint 27 Kickoff

1. Should Timesheet/Probation/Requisition approvals be migrated onto the workflow engine in
   Sprint 27 or deferred (§6, §14)? This PRD recommends deferring, but the call belongs to
   whoever owns those three modules' roadmaps.
2. What is the exact list of "sensitive" profile fields requiring approval (§4.2 FR-4)? This PRD
   proposes legal name, date of birth, and bank details as a starting set — final list needs HR/
   compliance sign-off before Increment 5.
3. Should `findAllByManagerId` (Increment 1) return only active employees, or also recently
   offboarded ones for a transition period? Needs a decision before Increment 3 ships.
4. Does any tenant need MSS scoped to something other than the direct `Employee.manager` pointer
   (e.g. a matrix/dotted-line manager)? No such concept exists in the schema today — confirming
   this is out of scope before committing to §3's "direct reports only" definition would avoid
   rework later.
