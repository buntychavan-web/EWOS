# EWOS Product Roadmap

**Status:** Single source of truth for the EWOS product — module inventory, delivery status, and
forward roadmap.
**As of:** 2026-08-06, through Sprint 26A (merge commit `091994cc19d3f61e9d40a99bede2d769d55fb397`
on `main`).
**Companion documents:** [`PROJECT_STATUS.md`](./PROJECT_STATUS.md) (engineering detail,
sprint-by-sprint changelog, technical operating rules), [`RELEASE_NOTES_SPRINT_26.md`](./RELEASE_NOTES_SPRINT_26.md)
(Sprint 26/26A release detail), [`COMPANY_CONFIGURATION_BACKLOG.md`](./COMPANY_CONFIGURATION_BACKLOG.md)
(source for the Company Configuration backlog entry below).

This document answers "what is EWOS today and where is it going" at the product level. For "how
was it built" and "what exactly shipped in sprint N," see `PROJECT_STATUS.md`.

---

## How to read this document

Every module gets: **Current Status**, **Sprint(s) completed**, **Major features implemented**,
**Remaining gaps**, **Dependencies**, **Priority**. Status is one of:

- **Completed** — shipped, in production use, no sprint currently scoped to extend it.
- **In Progress** — shipped a working baseline, actively receiving sprints.
- **Planned** — not built. Either a reserved namespace with no code beyond `package-info.java`, or
  a documented backlog item with no code at all.

Modules are grouped into ten categories per the product's requested structure: Core HR,
Recruitment, Payroll, Employee Experience, Performance, Learning, Analytics, Administration,
Platform, Future Roadmap.

A note on accuracy: this document was produced by directly inspecting the `main` branch (package
contents, `pom.xml`, migrations) rather than by copying prior status claims verbatim. One
correction versus `PROJECT_STATUS.md` §14 surfaced during that check — see the `talent` module
entry under Future Roadmap.

---

## Core HR

### Employee (`com.ewos.employee`)

- **Current Status:** Completed
- **Sprint(s) completed:** WP-005 (foundation), Sprint 1.3 (Employee↔User identity link)
- **Major features implemented:** Employee master data, employment status lifecycle, soft delete
  (`@SQLRestriction`), the identity link every other module's `employee_id` FK relies on.
- **Remaining gaps:** No Grade / Designation / Employee Category master data model — several
  downstream modules (Exit checklist/document template scoping, Payroll) have flagged this as a
  blocker for finer-grained scoping.
- **Dependencies:** `tenancy` (tenant/company scoping), `identity` (user link).
- **Priority:** High (foundational; the missing Grade/Designation model blocks other modules).

### Organization (`com.ewos.organization`)

- **Current Status:** Completed
- **Sprint(s) completed:** WP-004 (Organization Engine)
- **Major features implemented:** Organization units (business unit / department hierarchy),
  the `OrganizationUnit` scoping used throughout Payroll and Exit for most-specific-wins
  configuration resolution.
- **Remaining gaps:** None documented at the module level.
- **Dependencies:** `tenancy`.
- **Priority:** Medium.

### Leave (`com.ewos.leave`)

- **Current Status:** Completed
- **Sprint(s) completed:** WP-008 (foundation), Sprint 21 UAT (bug fix)
- **Major features implemented:** Leave types, leave application/approval, balance tracking.
  Sprint 21 fixed `ClientAccessGuard` incorrectly blocking Leave for ordinary (non-client-managed)
  tenants.
- **Remaining gaps:** Not separately audited since Sprint 21; no open findings on record.
- **Dependencies:** `employee`, `workflow` (approval), `attendance` (LOP interplay via Payroll).
- **Priority:** Medium.

### Attendance (`com.ewos.attendance`)

- **Current Status:** Completed
- **Sprint(s) completed:** WP-007 (foundation), Sprint 16 (test coverage), Sprint 24L (LOP
  integration into Payroll)
- **Major features implemented:** Attendance capture, regularization, and — as of Sprint 24L —
  direct Loss-of-Pay integration into the payroll run (previously payroll's LOP input was manual).
- **Remaining gaps:** None documented at the module level.
- **Dependencies:** `employee`, `payroll` (LOP consumption).
- **Priority:** Medium.

### Exit Management (`com.ewos.exit`) — **Exit Management Version 1**

- **Current Status:** Completed (Version 1)
- **Sprint(s) completed:** T-suite baseline (resignation lifecycle, clearance, knowledge transfer,
  exit interview, exit documents, alumni), **Sprint 26** (7 increments — self-service resignation +
  resignation types, notice-period actions, multi-level approval via the workflow engine,
  configurable exit checklist templates, Full & Final settlement linkage, configurable PDF exit
  document generation, KT successor/handover refinement), **Sprint 26A** (audit remediation — see
  below)
- **Major features implemented:** Full resignation lifecycle (submission, approval, notice
  management, clearance, KT, F&F linkage, document generation, alumni conversion); employee
  self-service resignation submission/withdrawal; optional multi-level approval via the generic
  workflow engine (falls back to direct approval when no workflow is configured for a tenant);
  company/business-unit-scoped checklist and document templates; on-demand PDF letter generation
  (Acceptance, Relieving, Experience, Service Certificate, F&F Statement) via an embedded
  Unicode-capable font (GNU FreeSans), fixed in Sprint 26A for Indian-name/multilingual rendering.
- **Remaining gaps:** No Grade/Designation/Employee Category scoping for templates (blocked on the
  `employee` module gap above); KT tracking has no due dates or handover-completeness metric beyond
  a per-item completed flag; exit completion is not gated on F&F settlement status; exit-interview
  analytics not built.
- **Dependencies:** `employee`, `workflow` (approval), `payroll` (F&F settlement linkage,
  read-only), `organization` (checklist/document template scoping).
- **Priority:** Low (V1 scope is closed; remaining gaps are documented, deliberate deferrals, not
  defects).

### Career & Succession Planning (`com.ewos.succession`)

- **Current Status:** Completed
- **Sprint(s) completed:** T-suite (T12)
- **Major features implemented:** Succession planning baseline (career/succession tracking).
- **Remaining gaps:** Not separately re-audited since the T-suite baseline; no open findings on
  record.
- **Dependencies:** `employee`, `performance`/`competency` (readiness signals).
- **Priority:** Low.

---

## Recruitment

### Recruitment (`com.ewos.recruitment`)

- **Current Status:** Completed
- **Sprint(s) completed:** T-suite baseline (T1), Sprint 22A (backend stabilization)
- **Major features implemented:** Job positions and requisitions with workflow approval.
- **Remaining gaps:** None documented beyond the Sprint 22A stabilization pass.
- **Dependencies:** `workflow`, `organization`.
- **Priority:** Medium.

### Applicant Tracking (`com.ewos.ats`)

- **Current Status:** Completed
- **Sprint(s) completed:** T-suite baseline
- **Major features implemented:** Candidates, applications, pipeline stages.
- **Remaining gaps:** None documented at the module level.
- **Dependencies:** `recruitment`.
- **Priority:** Medium.

### Interview Management (`com.ewos.interview`)

- **Current Status:** Completed
- **Sprint(s) completed:** T-suite baseline
- **Major features implemented:** Interview scheduling and evaluation workflow.
- **Remaining gaps:** None documented at the module level.
- **Dependencies:** `ats`, `recruitment`.
- **Priority:** Medium.

### Offer Management & Pre-Boarding (`com.ewos.offer`)

- **Current Status:** Completed
- **Sprint(s) completed:** T-suite baseline
- **Major features implemented:** Offer generation and pre-boarding handoff into `onboarding`.
- **Remaining gaps:** None documented at the module level.
- **Dependencies:** `ats`, `interview`, `onboarding`.
- **Priority:** Medium.

---

## Payroll

### Payroll (`com.ewos.payroll`) — **Payroll Version 1**

- **Current Status:** Completed (Version 1, frozen)
- **Sprint(s) completed:** WP-009 (foundation: periods, components, compensation, runs, payslips),
  M1–M7 (configuration through accounting/ERP export/reports/dashboards), Sprint 18–20 (N+1/JDBC
  batching performance fix), Sprint 24H-1/24H-2 (Statutory Calculation Engine: PF/ESI/Professional
  Tax/LWF/TDS/Gratuity), Sprint 24I (PF EPFO ECR statutory return-file generation), Sprint 24J
  (Employee Self-Service: payslip detail, salary component explanations, payroll dashboard, income
  tax projection, investment declaration self-service, investment proof upload foundation), Sprint
  24K (**Payroll V1 freeze sprint** — LTA block management, prorated monthly tax recovery, tax on
  variable payments, payroll simulation/dry-run, bulk variable-input upload, payslip PDF
  generation, statutory return review, AI-ready + Knowledge Centre foundations), Sprint 24L
  (Maker-Checker separation of duties, Reopen/Correction framework, Attendance-driven LOP
  integration, Loan & Recovery engine, bank-advice/payslip security fixes)
- **Major features implemented:** Full payroll run lifecycle (configuration, employee payroll
  assignment, runs, payslips, accounting/ERP export, reports/dashboards); statutory calculation
  engine (PF/ESI/PT/LWF/TDS/Gratuity); PF EPFO ECR return-file export; full employee
  self-service suite; maker-checker approval on runs; reopen/correction framework with audit
  trail; attendance-driven LOP; loan & recovery deductions; Sprint 26's nullable Full & Final
  settlement ↔ Resignation linkage (Payroll's math untouched, Exit only reads/links).
- **Remaining gaps:** PF ECR return file emits NCP days and refund-of-advances as `0` (neither is
  tracked at the statutory-deduction level yet — a filer must correct by hand); return-file
  generation only supports the PF scheme (ESI/PT/LWF challans get a 400, no invented format);
  `PayslipPdfGenerationService` still uses Standard-14 Helvetica fonts and shares Exit's
  pre-Sprint-26A Unicode limitation (out of scope for Sprint 26A, which only covered the exit
  document generator).
- **Dependencies:** `employee`, `attendance` (LOP), `leave`, `organization`, `workflow`
  (maker-checker/approval), `exit` (F&F linkage, read direction only).
- **Priority:** Low (V1 is frozen; remaining gaps are documented, scoped deferrals).

---

## Employee Experience

### Employee Onboarding (`com.ewos.onboarding`)

- **Current Status:** Completed
- **Sprint(s) completed:** T-suite baseline, Sprint 23A (self-service + task reassignment gap
  closure)
- **Major features implemented:** Onboarding plans, tasks, surveys; employee self-service
  onboarding portal; task reassignment.
- **Remaining gaps:** None documented beyond the Sprint 23A closure.
- **Dependencies:** `offer` (pre-boarding handoff), `employee`.
- **Priority:** Medium.

### Probation & Confirmation (`com.ewos.probation`)

- **Current Status:** Completed
- **Sprint(s) completed:** T-suite baseline
- **Major features implemented:** Probation period tracking and confirmation workflow.
- **Remaining gaps:** None documented at the module level.
- **Dependencies:** `employee`, `workflow`.
- **Priority:** Low.

---

## Performance

### Performance Management (`com.ewos.performance`)

- **Current Status:** Completed
- **Sprint(s) completed:** T-suite baseline, Sprint 24A/24B (P0 gap closure, bulk launch + cycle
  state machine + reports + notifications)
- **Major features implemented:** Performance review cycles, bulk launch, state machine-driven
  cycle progression, reporting, notifications.
- **Remaining gaps:** None documented beyond the Sprint 24A/24B closure.
- **Dependencies:** `employee`, `goals`, `competency`, `notification`.
- **Priority:** Medium.

### Goals / OKR / KPI (`com.ewos.goals`)

- **Current Status:** Completed
- **Sprint(s) completed:** T-suite baseline, Sprint 24D/24E (self-service gap closure)
- **Major features implemented:** Goal/OKR/KPI setting and tracking, employee self-service.
- **Remaining gaps:** None documented beyond the Sprint 24D/24E closure.
- **Dependencies:** `employee`, `performance`.
- **Priority:** Medium.

### Competency Management (`com.ewos.competency`)

- **Current Status:** Completed
- **Sprint(s) completed:** T-suite baseline, Sprint 24D (self-service gap closure)
- **Major features implemented:** Competency framework and assessment, employee self-service.
- **Remaining gaps:** None documented beyond the Sprint 24D closure.
- **Dependencies:** `employee`, `performance`, `succession` (readiness signals).
- **Priority:** Medium.

---

## Learning

### Learning & Development (`com.ewos.learning`)

- **Current Status:** Completed
- **Sprint(s) completed:** T-suite baseline
- **Major features implemented:** Learning/development plan tracking.
- **Remaining gaps:** Not separately re-audited since the T-suite baseline; no open findings on
  record.
- **Dependencies:** `employee`, `competency`.
- **Priority:** Low.

---

## Analytics

### Analytics (`com.ewos.analytics`)

- **Current Status:** Planned
- **Sprint(s) completed:** None. Reserved namespace only (`package-info.java`, `Status: RESERVED`
  per `EWOS_MASTER_ARCHITECTURE_v1.0`) — no `.api`/`.application`/`.domain`/`.infrastructure` code
  exists.
- **Major features implemented:** None.
- **Remaining gaps:** Entire module is unbuilt. Cross-module reporting today lives inside
  individual modules (e.g. Payroll's dashboards/reports, Performance's reports) rather than a
  unified analytics layer.
- **Dependencies:** Would depend on most other modules as data sources; likely depends on
  `dataexchange`/`integration` for any external BI export path.
- **Priority:** Medium (no committed sprint; product-level cross-module reporting is a recurring
  ask across Payroll/Performance/Exit but nothing consolidates it yet).

---

## Administration

### Notification (`com.ewos.notification`)

- **Current Status:** Completed
- **Sprint(s) completed:** Sprint 24E (PMS notification framework), Sprint 24F (platform
  stabilization)
- **Major features implemented:** Cross-module notification framework; consumed by Payroll
  (`PayrollNotificationEventListener`), Performance, and others.
- **Remaining gaps:** None documented at the module level.
- **Dependencies:** Consumed by most business modules; no significant upstream dependency itself.
- **Priority:** Medium.

### Company Configuration (`com.ewos.company` — proposed, not merged)

- **Current Status:** Planned (documented backlog, zero code)
- **Sprint(s) completed:** None. PR #6 ("Sprint 6: Company Configuration") was closed unmerged on
  2026-08-05, superseded by the `com.ewos.tenancy` `Tenant → Client → Company` model that
  independently covers the foundational need (giving `company_id` a real backing row). Five
  specific capabilities from that closed PR were never rebuilt and are recorded as backlog in
  [`COMPANY_CONFIGURATION_BACKLOG.md`](./COMPANY_CONFIGURATION_BACKLOG.md).
- **Major features implemented:** None. (`tenancy` already provides the `Tenant`/`Company` anchor
  rows themselves — see the Platform section below — but none of the five items below.)
- **Remaining gaps (the backlog, in full):**
  1. **Company profile version history** — effective-dated snapshots of company profile fields, so
     a profile edit opens a new version rather than overwriting history.
  2. **Company statutory registrations** — tracking each company's own PAN/TAN/GST/PF/ESIC/PT/LWF
     registration numbers (distinct from Payroll's statutory *calculation* configuration).
  3. **Company bank accounts** — accounts belonging to the company itself (salary funding, F&F
     funding, reimbursement, statutory remittance, vendor payments), distinct from
     `EmployeeBankAccount`.
  4. **Policy assignments** — associating a company with policy configuration (leave policy,
     attendance policy, workflow definitions) with an effective-dated, non-overlapping window.
  5. **Shared-service team assignments** — recording which internal team (HR/Payroll/Finance/IT)
     services a given company, relevant to the payroll-service-provider model.
- **Dependencies:** `tenancy` (`Client`/`ClientAssignment` model is the intended foundation for
  item 5); `payroll` (statutory calculation config is the adjacent-but-distinct concept for item
  2); `workflow`/`leave`/`attendance` (concrete policy entities for item 4).
- **Priority:** Medium (no committed sprint or owner per the backlog document itself — recorded so
  the scope decision is visible, not to imply urgency).

---

## Platform

### Identity & Access (`com.ewos.identity`)

- **Current Status:** Completed
- **Sprint(s) completed:** Sprint 1–5 (foundation, identity module, user management, hardening),
  Sprint 1.4 (Role & Permission Management + Role Usage Impact Analysis), Sprint 24G (authentication
  hardening — failed-login persistence, refresh-token reuse detection, password-change session
  gaps)
- **Major features implemented:** Authentication, JWT issuance/refresh, RBAC (roles/permissions),
  refresh-token rotation with reuse detection, login history, admin bootstrap guards
  (`JwtSecretGuard`, `AdminPasswordGuard`).
- **Remaining gaps:** No Role/Permission admin CRUD API (entities support it; blocked on a product
  scope decision); no restore-from-soft-delete endpoint for users; refresh tokens aren't bound to a
  device/session family (reuse revokes only the presented token, not a whole chain); no account
  lockout / brute-force throttling.
- **Dependencies:** None (foundational — every other module depends on it).
- **Priority:** Medium (gaps are hardening items, not blockers; see Technical Debt below).

### Multi-Tenancy (`com.ewos.tenancy`)

- **Current Status:** Completed
- **Sprint(s) completed:** Sprint 1.1–1.3 (`user_tenant_memberships`/`tenant_access_grants`,
  `ClientAccessGuard` rollout, Employee↔User identity link), Sprint 14.1–14.4 (tenant/client/
  company/service-catalogue/provider foundation, payroll collaboration + client-scoped
  authorization), Sprint 25B (production-readiness audit — confirmed foundational architecture
  coverage, closed PR #6 as superseded)
- **Major features implemented:** `Tenant → Client → Company` hierarchy; `ClientAccessGuard`
  enforced across every module; payroll-service-provider multi-client collaboration model
  (`TenantAccessGrant`, `PayrollServiceProvider`).
- **Remaining gaps:** See Company Configuration backlog above — the `Company` entity itself has no
  version history, statutory registration tracking, bank accounts, policy assignment, or
  shared-service assignment beyond the anchor row.
- **Dependencies:** `identity` (tenant membership is user-scoped).
- **Priority:** High (every other module's tenant isolation depends on this working correctly).

### Workflow Engine (`com.ewos.workflow`)

- **Current Status:** Completed
- **Sprint(s) completed:** WP-006 (foundation — metadata-driven state machine)
- **Major features implemented:** Generic, reusable approval-workflow engine consumed by Payroll
  (`PayrollApprovalWorkflowListener`, since Sprint 14.3), Recruitment (requisition approval), and
  Sprint 26's optional multi-level Exit resignation approval.
- **Remaining gaps:** None documented at the module level.
- **Dependencies:** `tenancy` (tenant-scoped workflow definitions).
- **Priority:** Medium.

### Integration Framework (`com.ewos.integration`)

- **Current Status:** Completed
- **Sprint(s) completed:** Sprint 14.1–14.4 (generic integration adapter framework)
- **Major features implemented:** Generic adapter framework for external system integration.
- **Remaining gaps:** Not separately re-audited since Sprint 14; no open findings on record.
- **Dependencies:** `tenancy`.
- **Priority:** Low.

### Data Exchange (`com.ewos.dataexchange`)

- **Current Status:** Completed
- **Sprint(s) completed:** Sprint 14.1–14.4 (data-exchange framework)
- **Major features implemented:** Data-exchange framework underlying bulk import/export across
  modules.
- **Remaining gaps:** Not separately re-audited since Sprint 14; no open findings on record.
- **Dependencies:** `tenancy`, `importexport`.
- **Priority:** Low.

### Import/Export (`com.ewos.importexport`)

- **Current Status:** Completed
- **Sprint(s) completed:** Sprint 24E (bulk import/export, alongside the PMS notification
  framework work)
- **Major features implemented:** Bulk import/export used by Payroll (bulk variable-input upload)
  and Performance/Goals/Competency.
- **Remaining gaps:** Not separately re-audited beyond Sprint 24E; no open findings on record.
- **Dependencies:** `dataexchange`.
- **Priority:** Low.

### Shared Kernel (`com.ewos.shared`)

- **Current Status:** Completed
- **Sprint(s) completed:** Ongoing since Sprint 1 (cross-cutting; not a feature module with its own
  sprint history).
- **Major features implemented:** Cross-cutting infrastructure — `ApiException`/error handling,
  `AuditableEntity`/`AuditorProvider` conventions, `CorrelationIdFilter`, common web/persistence
  utilities used by every module.
- **Remaining gaps:** `AuditorProvider`/`CorrelationIdFilter` are excluded from the JaCoCo coverage
  gate as a blanket `common/**` exemption and deserve direct tests (see Technical Debt).
- **Dependencies:** None (foundational).
- **Priority:** Medium.

---

## Future Roadmap

These are reserved namespaces — `package-info.java` only, `Status: RESERVED` per
`EWOS_MASTER_ARCHITECTURE_v1.0` — with zero `.api`/`.application`/`.domain`/`.infrastructure` code.
They are placeholders reserving a package name for a future milestone, not modules with any
delivered functionality.

### AI Platform (`com.ewos.ai`)

- **Current Status:** Planned
- **Sprint(s) completed:** None. Sprint 24K noted an "AI-ready foundation" for Payroll (data shapes
  that would support future AI features), but that work lives inside `com.ewos.payroll`, not this
  reserved package.
- **Major features implemented:** None.
- **Remaining gaps:** Entire module unbuilt.
- **Dependencies:** Likely broad (would consume data across most modules).
- **Priority:** Low.

### Governance (`com.ewos.governance`)

- **Current Status:** Planned
- **Sprint(s) completed:** None.
- **Major features implemented:** None.
- **Remaining gaps:** Entire module unbuilt.
- **Dependencies:** Unknown — not yet scoped.
- **Priority:** Low.

### Talent (`com.ewos.talent`)

- **Current Status:** Planned
- **Sprint(s) completed:** None.
- **Major features implemented:** None — the package contains only
  `package-info.java` (`Status: RESERVED`, documented purpose: "Talent (learning + performance)
  module," i.e. a possible future consolidation point for `learning`/`performance`/`competency`/
  `succession`, none of which has been merged into it).
- **Remaining gaps:** Entire module unbuilt.
- **Dependencies:** Would likely absorb or front `learning`, `performance`, `competency`,
  `succession` if built as its package comment describes.
- **Priority:** Low.

  > **Correction note:** `PROJECT_STATUS.md` §14's "Current module inventory" (backfilled
  > 2026-08-02) lists `talent` among "25 active modules." Direct inspection of `main` for this
  > document found `com.ewos.talent` contains only `package-info.java` with `Status: RESERVED` —
  > the same state as `ai`/`governance`/`analytics`. That PROJECT_STATUS.md line appears to be a
  > transcription error (likely conflating the reserved `talent` package with the very much
  > active `learning`/`performance`/`competency`/`succession` modules it was proposed to
  > consolidate). This document reflects the verified live state: `talent` is Planned, not
  > Completed.

---

## Version Roadmap

### EWOS v1.0

Scope: everything **Completed** in this document as of Sprint 26A. This is the release the
platform is at today.

- Core HR (Employee, Organization, Leave, Attendance, Exit Management V1, Succession)
- Full Recruitment suite (Recruitment, ATS, Interview, Offer/Pre-boarding)
- Payroll Version 1 (frozen at Sprint 24K/24L, statutory engine, self-service, maker-checker)
- Employee Experience (Onboarding, Probation & Confirmation)
- Performance suite (Performance, Goals/OKR/KPI, Competency)
- Learning & Development
- Platform (Identity, Multi-Tenancy, Workflow, Integration, Data Exchange, Import/Export, Shared
  Kernel)
- Sprint 26A security/correctness remediation (soft-delete association safety, fail-loud audit
  actor resolution, tenant-header consistency, Unicode-capable PDF generation)

### EWOS v1.1

Scope: near-term hardening and gap closure on already-Completed modules — no new module namespace.

- Employee/Grade/Designation/Employee Category master data (currently the single most-cited
  blocker across Exit template scoping and other modules)
- PF ECR return-file NCP-days/refund-of-advances tracking; ESI/PT/LWF return-file formats
- `PayslipPdfGenerationService` Unicode font fix (apply Sprint 26A's Exit-module fix to Payroll)
- Identity hardening: Role/Permission admin CRUD API, refresh-token device/session binding,
  account lockout/brute-force throttling, user restore-from-soft-delete
- Company Configuration backlog items 2–3 (statutory registrations, company bank accounts) — the
  two items with the clearest immediate compliance/operational value
- JaCoCo coverage floor raised toward the 65%-before-RC / 80%-before-GA staged targets (see
  Technical Debt)

### EWOS v2.0

Scope: new module namespaces — the reserved packages, built out.

- **Analytics** — unified cross-module reporting/BI layer, superseding the per-module
  dashboards/reports that exist today in Payroll and Performance
- **AI Platform** — building on Payroll's Sprint 24K "AI-ready foundation"
- **Governance** — scope not yet defined
- **Talent** — evaluate whether to build as a `learning`/`performance`/`competency`/`succession`
  consolidation layer, or retire the reserved package if the four modules remain independently
  sufficient
- Company Configuration backlog items 1, 4, 5 (profile version history, policy assignments,
  shared-service team assignments) — the three items with more open design questions
  (see `COMPANY_CONFIGURATION_BACKLOG.md`)

---

## Technical Debt

Consolidated from `PROJECT_STATUS.md` §7 ("Remaining technical debt") and §16/§17 (Payroll
sprint-close notes). Items already marked resolved in `PROJECT_STATUS.md` are omitted here as
noise; this list is what is still open as of Sprint 26A.

### Medium priority

1. **Role / Permission admin API is missing** (`identity`) — entities support soft delete +
   versioning but there is no controller to CRUD them. Blocked on a product scope decision
   (assign/mint permissions at runtime vs. seed-only).
2. **Restore-from-soft-delete** (`identity`) — no `POST /api/v1/users/{id}/restore` yet. The
   partial-unique-index collision case (a live row with the same username) needs handling first.
3. **Refresh tokens aren't bound to a device/session** (`identity`) — reuse detection would be
   stronger with a family id and rotation-chain revocation; currently only the presented token is
   revoked on logout/refresh.
4. **No account lockout / brute-force throttling** (`identity`) — `login_history` has the raw
   data; a threshold-based lock is not yet built.
5. **No `spring.jpa.hibernate.ddl-auto=validate` in CI** (platform-wide) — would catch
   entity/schema drift early; requires cleaning up small existing mismatches first.
6. **No `@ControllerAdvice`-level request/access logging** (platform-wide) — `CorrelationIdFilter`
   puts the correlation id in MDC, but there's no "one line per request" access log.
7. **PF ECR return file** (`payroll`) — NCP days and refund-of-advances emitted as `0` (not tracked
   at the statutory-deduction level); return-file generation only supports the PF scheme.
8. **`PayslipPdfGenerationService`** (`payroll`) — still on Standard-14 Helvetica fonts, sharing
   the same Unicode/Indian-name limitation Sprint 26A fixed for Exit's document generator; out of
   scope for that fix.
9. **Grade / Designation / Employee Category master data** (`employee`) — does not exist anywhere
   in EWOS; blocks finer-grained scoping in Exit (checklist/document templates) and is a likely
   future blocker elsewhere.
10. **Company Configuration backlog** (`tenancy`/proposed `company`) — see the five-item list
    under Administration above.

### Low priority

11. **Testcontainers reuse via `.withReuse(true)`** — meaningful only for local iterative runs.
12. **Optimistic-lock retry policy** — 409s currently propagate to the client rather than a single
    automatic retry on conflict.
13. **OpenAPI examples on request/response bodies** — schemas are described, but only a handful of
    fields carry `@Schema(example = ...)`.
14. **Refresh-token cleanup job** — `RefreshTokenRepository.deleteAllExpired(Instant)` exists but no
    scheduled job calls it.
15. **Coverage exclusions could shrink** — `common/persistence/AuditorProvider` and
    `common/web/CorrelationIdFilter` are covered by a blanket `common/**` exemption and deserve
    direct tests.

### Coverage gate trajectory

JaCoCo instruction-coverage floor is currently `0.45` (raised from an initial, never-actually-met
`0.80` claim; corrected to a real, test-backed `0.35` by the 2026-07-27 audit, then raised to
`0.45` in Sprint 18–20). Staged roadmap per `PROJECT_STATUS.md` §11: **50% before Beta → 65% before
RC → 80% before GA.** No sprint has yet moved the floor past `0.45`; each future raise must be
backed by real new tests per the "never move the gate ahead of the tests that justify it" rule
(`PROJECT_STATUS.md` §8.7).

---

## Compliance Roadmap

Tracks statutory/regulatory areas EWOS currently supports at a fixed point in time, and where
future compliance changes are anticipated. This is a placeholder for future statutory-change
tracking, not a legal compliance certification.

### Currently supported (Payroll statutory engine, Sprint 24H-1/24H-2 onward)

- **Provident Fund (PF)** — calculation engine + EPFO Electronic Challan-cum-Return (ECR) v2.0
  file export (Sprint 24I). The only scheme with a real government-format return file today.
- **Employee State Insurance (ESI)** — calculation engine. No return-file export yet.
- **Professional Tax (PT)** — calculation engine, jurisdiction/slab configuration. No return-file
  export yet.
- **Labour Welfare Fund (LWF)** — calculation engine. No return-file export yet.
- **Income Tax / TDS** — calculation engine, investment declaration self-service, income tax
  projection, prorated monthly tax recovery, tax on variable payments (Sprint 24K).
- **Gratuity** — calculation engine.

### Known gaps (statutory)

- ESI/PT/LWF challans have no generated return file — `generateReturnFile` returns 400 for any
  scheme other than PF rather than inventing an unverified format. Building these requires
  confirming each format with an authoritative source first (explicitly not attempted so far, to
  avoid shipping an invented/incorrect government file format).
- PF ECR's NCP-days and refund-of-advances columns are placeholder zeros pending LOP/non-
  contributory-day tracking at the statutory-deduction level.
- Company-level statutory registration numbers (PAN/TAN/GST/PF/ESIC/PT/LWF establishment codes)
  are not tracked anywhere — see Company Configuration backlog item 2. Today's statutory engine
  configures *calculation* (jurisdictions, slabs, rates), not the company's own registered
  identifiers.
- Form 16 generation was explicitly out of scope for Sprint 24J and has not been attempted; only
  the investment-proof-upload foundation for future Year-End Tax Compliance was built.

### Anticipated future work (not yet scoped to any sprint)

- ESI/PT/LWF statutory return-file exporters, once each format is confirmed with an authoritative
  source.
- Form 16 generation (Year-End Tax Compliance), building on the Sprint 24J investment-proof-upload
  foundation.
- Statutory rate/slab updates as PF/ESI/PT/LWF/TDS thresholds change year over year — the existing
  `StatutoryConfigResolver`/effective-dated configuration pattern is designed to absorb these
  without new code, but each year's actual rate change still needs to be entered and verified.
- Company statutory registration tracking (Company Configuration backlog item 2).

---

## Document provenance

- Module inventory verified directly against `main` (`ls src/main/java/com/ewos/*/`, 29 top-level
  packages) rather than transcribed from prior documents.
- Sprint history sourced from `PROJECT_STATUS.md` §13–§17 and its backfilled §14 module inventory,
  cross-checked against this session's first-hand knowledge of Sprint 26/26A (not yet reflected in
  `PROJECT_STATUS.md` at the time of writing).
- Company Configuration backlog sourced verbatim from `COMPANY_CONFIGURATION_BACKLOG.md`.
- JaCoCo floor value (`0.45`) verified directly from `pom.xml` (`jacoco.line.coverage.min`), not
  assumed from prior text.
