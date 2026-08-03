# EWOS — Project Status

_Last updated: 2026-08-02 (Sprint 24J)._

Snapshot of the backend after Sprints 1, 2, 4, 5 (hardening), the Sprint
1.1–1.4/2.x/4 program (multi-tenancy, RBAC hardening, employee identity
linking, workflow/approval engine), the 2026-07-27 CTO Production
Readiness Audit remediation, Sprint 15 (Enterprise Quality & Reliability
Sprint, 2026-07-28), and now Sprints 16 through 24J (Payroll V1
finalization + repository housekeeping, 2026-08-02). This is the single
source of truth for what is delivered, what quality gates are enforced,
and what technical debt still exists. §§ 1–10 below are the original
Sprint 1–5 snapshot from 2026-07-09 and are historical; §11 covers the
2026-07-27 audit; §13 covers Sprint 15; §14 backfills every sprint from
16 through 24H-2 — the T1–T12 Talent/Recruitment/Exit suite, the
WP-001–009 foundation/payroll build-out, and the Payroll Statutory
Calculation Engine — none of which had ever been recorded here before
that update; §15 covers Sprint 24I; **§16 (new) covers Sprint 24J** —
Payroll V1's Employee Self-Service and Administration finalization, plus
the Statutory Return and Lifecycle reviews.

---

## 13. Sprint 15 — Enterprise Quality & Reliability Sprint (2026-07-28)

Quality-only sprint: no new features, focused entirely on strengthening
automated testing, correctness, and reliability of existing functionality
across payroll, statutory compliance, employee lifecycle, organization
structure, security, and regression coverage. See `TESTING.md` for the
test-writing conventions this sprint established and how to run the suite.

**Scope covered** (16 new test files, 149 new test methods, all against
real business logic — no placeholder/tautological tests):

- **Payroll**: `EmployeeCompensationService`, `PayComponentService`,
  `PayrollArrearService`, `FinalSettlementService` (full DRAFT→APPROVED→
  SETTLED/CANCELLED lifecycle and its arrear-queuing side effect),
  supplementary-run path and finalize/freeze transitions on
  `PayrollRunService`, `PayrollJournalGenerator` (double-entry GL balancing,
  missing-mapping failures, cost-centre proportional splitting),
  `PayrollValidator`/`PayrollValidationService` (pre-flight blockers vs.
  warnings), `EmployeeCostAllocationService`.
- **Statutory compliance**: `StatutoryDeductionService` (payslip extraction,
  idempotency, jurisdiction/period-month derivation — the effective PF/ESI/
  PT/TDS/Social Security/Medicare/FIT engine per `StatutoryClassifier`),
  `StatutoryChallanService` (roll-up aggregation, unique-employee counting,
  DRAFT→FILED→PAID lifecycle), `StatutorySettingService` (the effective-
  dated rate/slab lookup used for statutory limits).
- **Employee lifecycle**: `ProbationService` (confirmation lifecycle
  orchestration — open/duplicate/cross-company guards, default period-end,
  extend, submit→approve/reject workflow gate, confirm, terminate, cancel),
  `LeaveBalanceService` (allocation upsert + balance mirroring, adjustment
  accumulation).
- **Organization**: `GlConfigService` (cost-centre/business-unit code
  uniqueness per company, GL mapping creation) — `OrganizationUnit` CRUD
  (Company/Department/Designation/Location/Grade as configurable unit
  types) already had solid coverage from earlier sprints.
- **Security**: reviewed — already comprehensive (JWT filter/service/secret
  guard, auth rate limiting + account lockout, refresh-token rotation +
  revocation + expiry, logout idempotency, `ClientAccessGuard` tenant
  isolation, CORS). No gaps found requiring new tests beyond the regression
  suite below.
- **Regression suite**: `SoftDeleteRegressionTest` (Role/Permission
  soft-delete against real Postgres — User already had this), and
  `ConstructorAmbiguityRegressionTest` (permanent reflection-based CI check
  across all 336+ `@Component`/`@Service`/`@Controller` classes for the
  ambiguous-constructor bug class found in the P9 audit — supersedes the ad
  hoc one-off scan mentioned in §0).
- **Code quality**: reviewed for dead code, commented-out code, debug
  prints, and TODO/FIXME comments — none found. One duplicate-logic finding
  documented, not executed this sprint (see below).
- **Bug found and fixed while writing tests**: `StatutoryDeductionService
  .extractForRun`'s in-memory duplicate-code check only saw deductions
  already persisted from *prior* calls, not ones inserted earlier in the
  *same* call — two differently-coded components resolving to the same
  statutory code on one payslip (e.g. `PF` and `PROVIDENT_FUND`) would
  double-insert and rely on the database's unique constraint to catch it at
  runtime instead of skipping gracefully. Fixed by updating the in-memory
  set as each row is inserted.

**Not done this sprint, and why:**
- Bonus, Leave Encashment, and Loans are not implemented as distinct
  domain concepts in this codebase (no `BonusService`/`LoanService` etc.
  exist) — Full & Final Settlement handles encashment/gratuity/notice-pay
  as generic amount fields. No tests were written for features that don't
  exist.
- Attendance (`TimesheetService`/`TimeEntryService`/`AttendancePolicyService`)
  and Onboarding (`OnboardingPlanService`/`CandidateConversionService`/etc.)
  application-layer services remain untested at the service-orchestration
  level, though their domain policy layers (`TimesheetCalculator`,
  `OnboardingPolicy`) already had coverage. Time-boxed out of this sprint;
  recommended as Sprint 16's next target — see the coverage roadmap in §11.
- A code-quality finding: 18 services each define an identical private
  `currentActor()` helper (parse `SecurityContextHolder`'s authentication
  name as a UUID). Several modules (`probation`, `exit`, `onboarding`)
  already extract this into a package-local `XxxSecurity` helper class —
  that's the codebase's established de-duplication convention, applied
  inconsistently. Consolidating all 18 into a single shared utility would
  touch many files for marginal benefit and wasn't attempted this sprint
  given its testing focus; flagged here for a future low-risk cleanup pass,
  done with full regression coverage rather than a rushed mechanical edit.

**Result**: 986 backend tests (837 baseline + 149 new), 0 failures, all
quality gates green, JaCoCo coverage floor (`0.35`) still comfortably
cleared — real coverage rose meaningfully with this sprint's additions, but
the floor itself was deliberately left unmoved pending a precise
next-sprint measurement (§8.7's rule: never move the gate ahead of the
tests that justify it).

---

## 14. Sprints 16 through 24H-2 — backfilled 2026-08-02

**This section did not exist before Sprint 24I.** A live repository review on
2026-08-02 found that this document's §1 "Delivered by sprint" was never
updated past Sprint 5, and no later section captured the large amount of
work that shipped afterward — including modules that predate even Sprint 15
(§13) but were still never recorded here. This section closes that gap in
summary form; it does not replace or restate the sprint-by-sprint detail
that already lives in commit messages, ADRs, and business-rules docs.

### Modules delivered (headline commits, oldest first)

- **Platform foundation** — WP-003 (package restructure to master
  architecture v1.0), WP-004 Organization Engine, WP-005 Employee module,
  WP-006 Workflow engine (metadata-driven state machine), WP-007 Attendance,
  WP-008 Leave, WP-009 Payroll (periods, components, compensation, runs,
  payslips).
- **Payroll** — M1 (configuration + employee payroll assignment) through M7
  (accounting, ERP export, reports, dashboards), a v1.0 freeze declaration,
  then continued anyway: N+1/JDBC-batching performance fix, a critical
  no-BASIC-component run-failure fix, and Sprint 24H-1/24H-2's Statutory
  Calculation Engine (PF/ESI/Professional Tax/LWF/TDS/Gratuity — see §15 for
  24H-2 detail).
- **Talent/Recruitment/Exit suite (T1–T12)** — Recruitment (job positions +
  requisitions with workflow approval), ATS (candidates/applications/
  pipeline), Interview Management, Offer Management & Pre-Boarding, Employee
  Onboarding (plans/tasks/surveys), Probation & Confirmation, Performance
  Management, Goals/OKR/KPI, Learning & Development, Competency Management,
  Career & Succession Planning, Employee Exit & Alumni.
- **Multi-tenancy / client-provider platform** — Sprint 14.1–14.4: tenant/
  client/company/service-catalogue/provider foundation, payroll
  collaboration + client-scoped authorization, data-exchange framework,
  generic integration adapter framework, provider operations dashboard.
- **Tenant resolution and access control** — Sprint 1.1 (`user_tenant_
  memberships` + `tenant_access_grants`), Sprint 1.2 (1/4–4/4) `ClientAccessGuard`
  rollout across every module, Sprint 1.3 Employee↔User identity link,
  Sprint 1.4 Role & Permission Management + Role Usage Impact Analysis.
- **Sprint 13** — CI static-analysis gate fix (PMD `UnusedPrivateMethod`
  false positives).
- **Sprint 16** — zero-coverage service tests across Payroll, Attendance,
  and Onboarding; payroll certification evidence report; Final Audit
  Readiness Report.
- **Sprint 17** — Release Candidate Report; Backup & Disaster Recovery
  Runbook (RC1).
- **Sprint 18–20** — payroll run performance fix (N+1 elimination + JDBC
  batching), CSP/Referrer-Policy security headers + gitleaks secret
  scanning + Dependabot, JaCoCo floor raised to `0.45`, graceful shutdown
  (SIGTERM drain) for v1.0 readiness.
- **Sprint 21 UAT** — seeded Leave/Timesheet approval workflows for new
  tenants; fixed `ClientAccessGuard` incorrectly blocking Leave/Attendance
  for ordinary (non-client-managed) tenant users.
- **Sprint 22A** — Recruitment backend stabilization.
- **Sprint 23A** — closed Onboarding backend gaps (self-service + task
  reassignment).
- **Sprint 24A/24B/24D/24E** — Performance Management P0 gap closure, bulk
  launch + cycle state machine + reports + notifications, Goals/Competency/
  Development Plan self-service gap closure, PMS notification framework +
  bulk import/export. (Sprint 24C does not appear in the commit history
  under any code or docs; either skipped or never separately labeled —
  flagged here rather than silently omitted.)
- **Sprint 24F** — Platform Stabilization: tenant isolation, authorization,
  notifications.
- **Sprint 24G** — Authentication hardening: fixed failed-login
  persistence, closed refresh-token reuse and password-change session gaps.

### Current module inventory

25 active modules under `com.ewos.*` (identity, tenancy, employee,
organization, recruitment, ats, interview, offer, onboarding, probation,
performance, goals, competency, learning, succession, exit, talent,
attendance, leave, payroll, workflow, notification, dataexchange,
integration, importexport), plus `ai`/`governance`/`analytics` explicitly
reserved (`package-info.java` only, `Status: RESERVED`) per the master
architecture's later milestones. 53 Flyway migrations (V1–V53) as of Sprint
24H-2.

---

## 15. Sprint 24I — Payroll V1 completion + repository housekeeping (2026-08-02)

Two-track sprint per CTO direction: continue Payroll toward V1 completion
(primary) without spending the whole sprint on documentation, and bring this
document and the branch history current (secondary), in parallel.

### Payroll: PF ECR statutory return file generation

**Gap found:** `StatutoryChallanService.file()` only ever recorded a
manually-typed filing reference string — the system tracked that a filing
happened but never produced the file a company actually needs to upload to
the EPFO unified portal. Every other "government compliance output" bullet
either already existed (bank advice generation, payroll reports, variance/
cost-centre dashboards) or reduces to this same gap.

**What shipped:**

- `PfEcrFileExporter` (`com.ewos.payroll.domain`) — writes the EPFO
  Electronic Challan-cum-Return (ECR) v2.0 text file, `#~#`-delimited, one
  line per member: UAN, member name, gross/EPF/EPS/EDLI wages, EPF
  contribution remitted, EPS contribution remitted, employee's own EPF
  contribution remitted, NCP days, refund of advances.
- `StatutoryChallanService.generateReturnFile(tenantId, id)` — resolves the
  effective `PfConfiguration` for the challan's period (reusing the existing
  `StatutoryConfigResolver`, no new resolution logic), bulk-fetches active
  `EmployeePayrollProfile` rows for UAN lookup (no N+1), and only accepts PF
  challans (400 for any other code — no invented filing format for schemes
  this engine doesn't yet model precisely enough).
- `GET /api/v1/payroll/challans/{id}/return-file` on
  `StatutoryComplianceController` — mirrors the existing `BankAdviceController`/
  `PayrollJournalController` CSV-export pattern exactly (same
  `Content-Disposition: attachment` header shape, same access guard). No new
  architectural pattern introduced.
- Available regardless of challan status: a filer needs the file's content
  *before* calling `file()` with the resulting reference, so the corrected
  flow is roll up → download return file → file it with EPFO → record the
  reference → pay.

**Known, documented limitation:** NCP (non-contributory period) days and
refund-of-advances are emitted as `0` rather than guessed, because neither
is tracked anywhere in the statutory engine yet — see §7 item 17.

**Tests:** `PfEcrFileExporterTest` (3, pure formatting), plus 3 new
`StatutoryChallanServiceTest` cases (non-PF rejection, capped-wage
formatting, missing-profile UAN blanking). Full payroll suite (322 tests,
excluding the handful of Testcontainers-backed integration tests that can't
run without a Docker daemon in this sandbox) is green; checkstyle/PMD/
SpotBugs clean; CI (which has Docker) confirmed green on the pushed commit.

### Documentation: this document

- Backfilled §14 (sprints 16 through 24H-2 never previously recorded here).
- Corrected §7 item 16, which claimed Payroll/Employee/Leave/Attendance/
  Organization were "not in scope for this repo" — all five have shipped.
- Added §7 item 17 for the ECR NCP-days/refund-of-advances gap.

### Branch reconciliation

See the branch reconciliation report delivered alongside this sprint (not
duplicated here to avoid this document going stale again the moment a
branch is deleted). Summary: `claude/environment-selection-e607ds` →
`claude/sprint-24h2-recovery-6q6u16` is the one canonical lineage (contains
every sprint in §14/§15); `main`/`ewos-main` are stale ancestors stuck at
Sprint 20; `claude/repository-selection-575dn9` is a divergent, abandoned
10-commit fork from ~Sprint 5 with no relationship to current work.

---

## 16. Sprint 24J — Payroll Version 1 finalization (2026-08-02)

Explicit CTO direction for this sprint: finish Payroll V1 so it can be frozen, without starting
Exit Management and without introducing V2 architecture. A full read-only audit of the Payroll
module against a ~40-item checklist (ESS, Administration, Statutory Returns, Lifecycle) was run
first so nothing already built got duplicated — full findings are in the sprint's audit notes;
summary below.

### Employee Self-Service — shipped this sprint

- **Payslip detail** — `GET /self-service/payslips/{id}` (ownership-checked, not a company-access
  guard — an employee reading their own payslip needs no payroll authority at all).
- **Salary Components Explanation / personalized explanations** — every `PayslipLineResponse` now
  carries an `explanation` field (`PayslipLineExplainer`, pure function) describing what each line
  is and, for percentage-based lines, the exact percentage applied — no admin data-entry required.
- **Payroll Dashboard** — `GET /self-service/dashboard`: current compensation + latest payslip +
  current fiscal year's tax declaration in one view. Pure aggregation of three already-existing
  response types, no new data model.
- **Income Tax Projection** — `GET /self-service/tax-projection`: runs the caller's own numbers
  through the exact same `IncomeTaxCalculationService` the real payroll run uses, so the preview
  can never diverge from what a run would actually withhold.
- **Investment Declaration self-service** — `GET`/`PUT /self-service/tax-declaration`: employees
  can now view/create/update their own declaration (previously admin-only via `PAYROLL_CONFIG`).
  `tenantId`/`companyId`/`employeeId` are always resolved server-side from the authenticated
  caller, never taken from the request body.
- **Investment Proof Upload — Year-End Tax Compliance foundation** — new `tax_declaration_proofs`
  table (V54) + `TaxDeclarationProof` entity, mirroring the existing `CandidateDocument`/
  `ExitDocument` convention exactly: metadata + a `storageUri`, never raw file bytes. This is
  explicitly the "necessary foundation for future Year-End Tax Compliance" the sprint asked for;
  **Form 16 generation itself was explicitly out of scope for this sprint and was not attempted.**
- **Payroll History / Earnings vs Deductions Breakdown / YTD Summary** — audited and found to
  already exist (`myPayslips()` already returns full line-item detail with gross/deductions/net;
  `EmployeeTaxDeclaration` already carries YTD fields) — not rebuilt.
- **Payroll Notifications** — audited and found to already exist (`PayrollNotificationEventListener`
  fires on payslip finalization) — not rebuilt.
- **Payslip PDF Download — deferred, not built.** The codebase's established document convention
  (candidate/exit documents) never generates files server-side, only stores a `storageUri` to
  externally-hosted content. A payslip PDF is different: it would need this backend to render the
  bytes itself, which means either adding a real PDF library (a dependency/licensing decision:
  Apache PDFBox vs. commercial iText vs. OpenPDF) or hand-rolling raw PDF syntax that can't be
  verified renders correctly without a PDF viewer in this environment. Rather than guess at a
  dependency choice or ship an unverified hand-rolled renderer, this is left for a decision with
  the frontend/product team on which library and in Sprint 24J the payslip detail JSON (which
  carries every field a PDF would need) was shipped as the foundation for it.

### Payroll Administration — shipped this sprint

- **Payroll Exception Log — was dead code, now wired.** `PayrollRunService.recordValidationReport()`
  existed since Sprint 24H-1/earlier but was **never called from anywhere** (confirmed via
  repo-wide search) — every run's `validationReportJson` column was always null in practice, even
  though it's already exposed in `PayrollRunResponse`. `doStart()` now runs the existing
  `PayrollValidator` against the run's employee scope and records the report before processing
  begins. No behavior change to run outcomes (blockers still don't hard-stop `start()` — that
  contract was deliberately left alone) — this only makes the exception log actually populate.
- **Payroll Run History** — `GET /payroll/runs?companyId=&status=` lists every run for a company
  across periods (previously only listable per-period or via internal-only repository queries used
  by other services).
- **Payroll Activity Timeline / partial Approval History** — `GET /payroll/runs/{id}/timeline`
  reconstructs a run's CREATED → STARTED → COMPLETED/FAILED → FINALIZED → FROZEN sequence with
  actor and timestamp for each stage reached. Built entirely from fields already on `PayrollRun` —
  no new audit-log table.
- **Search / generic filtering / Payroll Comparison / Reprocessing** — reviewed, not built this
  sprint. Search and multi-field filtering beyond status would need a proper query-spec change
  across several controllers — a real feature, not a quick addition, and risks exactly the kind of
  broad architectural change this sprint was told to avoid. General period-to-period comparison is
  achievable today by combining the existing variance reports (`PayrollReportsController`) with the
  new run history — no gap in capability, just no single combined endpoint yet. **Reprocessing an
  already-finalized run was deliberately not built**: it has real implications (reversing GL
  postings, statutory challans already filed, bank advices already sent) that need a product
  decision, not a quick service method — flagged, not guessed at.

### Statutory Return Foundation — reviewed, not implemented further

PF already has a real, working return-file exporter (`PfEcrFileExporter`, Sprint 24I). ESIC,
Professional Tax, and Labour Welfare Fund challans exist (config, calculation, and the DRAFT→
FILED→PAID lifecycle with a manually-entered reference), but **no government-format file exporter
was built for any of them this sprint** — per instruction, only where "official government
specifications are available and verifiable." Unlike the EPFO ECR (a single, stable, well-known
text format), ESIC/PT/LWF filing formats vary by state and portal and were not independently
verified against a primary source in this sprint, so nothing was implemented rather than risk
shipping an invented format. This is a real, open gap — flagged for a future sprint once the
specific target state(s)/portal format(s) are confirmed with an authoritative source.

### Payroll Lifecycle — full review

| Item | Status |
|---|---|
| Previous Payroll Closure | **Exists** — `PayrollPeriodController.lock()`/`.close()`. |
| Payroll Calendar | **Exists** — `PayrollPeriod` + company/status listing. |
| Salary Revision | **Exists** — `EmployeeCompensationService` supersedes with effective-dated cutover + history. |
| Arrears / Retro Pay | **Exists** — `PayrollArrearService`; "retro pay" is the same concept, not a separate feature (its own javadoc calls it "retro salary adjustments (arrears)"). |
| Validation | **Exists**, narrow scope — checks active compensation, primary bank account, active payroll profile only. |
| Payroll Processing | **Exists** — `PayrollRunService` is the core engine. |
| Payroll Approval | **Exists, opt-in per tenant** — `PAYROLL_CLIENT_APPROVAL` workflow gates finalize only for tenants with that workflow definition seeded; other tenants finalize directly via `PAYROLL_RUN` authority with no maker-checker step. Not changed this sprint — making it mandatory platform-wide is a product/policy decision, not a bug fix. |
| Loan Recovery / Reimbursements | **Achievable today, not a dedicated feature.** `PayComponent` already supports arbitrary `FIXED` `EARNING`/`DEDUCTION` components with a `taxable` flag and free-text `description` — a company can already configure "Loan EMI" (DEDUCTION, non-taxable) or "Travel Reimbursement" (EARNING, non-taxable) today with zero code changes. What's genuinely missing is loan-balance/amortization tracking and a reimbursement-claim workflow (submit → approve → pay) — both are real, separate features, not gaps in this sprint's "can payroll pay/deduct these at all" sense. Not built this sprint to avoid inventing a new domain model under time pressure. |
| Attendance Import | **Missing, by design so far.** LOP is derived from approved unpaid `LeaveRequest` rows (`LopCalculator`), not from raw attendance/timesheet data — the `com.ewos.attendance` module isn't consulted by payroll at all today. |
| Variable Input Import | **Missing.** No bulk-import path for variable pay components exists; `PayrollArrearService.create()` is single-record only. |
| Payroll Simulation | **Missing as a feature.** `PayrollRunService` computes an internal throwaway preview payslip mid-calculation, but there is no admin/employee-facing dry-run endpoint. |

None of the "Missing" items above were built this sprint — each is a genuine, separate feature
(attendance-based LOP, bulk variable-pay import, a dry-run API) that would expand scope well beyond
"finalize V1," not a quick gap-fill.

### Verification

340 payroll-package tests run (up from 322 before this sprint), 0 failures, 338 passing — the
remaining 2 are the same pre-existing Testcontainers/Docker-sandbox errors present before any
Sprint 24J change (this sandbox has no Docker daemon; CI, which does, is green). Checkstyle/PMD/
SpotBugs clean.

---

## 17. Sprint 24K — Payroll Version 1 Freeze Sprint (2026-08-03)

### 1. Executive summary

Sprint 24K was scoped as the final Payroll V1 sprint before freeze. All ten numbered items in the
sprint brief were addressed: the three mandatory domain enhancements (§8.1 LTA blocks, §8.2
prorated tax recovery, §8.3 tax on variable payments), Payroll Simulation, Bulk Variable Input,
Payslip PDF generation, a Payroll Administration review (two new capabilities shipped, several
verified as already existing), a Statutory Return review (no new formats implemented — same honest
conclusion as Sprint 24J, for the same reason), an AI-ready foundation, and a Knowledge Centre
foundation. Eight commits, 97 files changed, ~6,700 lines added. 379 payroll-package tests (up
from 340), 377 passing — the same 2 pre-existing Docker-sandbox failures as every prior sprint, not
a regression. Checkstyle/PMD/SpotBugs all clean.

**Recommendation: Payroll V1 is ready to freeze.** See §15 (Freeze Report) below.

### 2. Payroll domain enhancements implemented (§8, mandatory)

- **§8.1 LTA Block Management** — `LtaBlockConfiguration` (government-fixed 4-calendar-year block,
  fully configurable anchor year/duration/max-claims/carry-forward rules per tenant or company) +
  `EmployeeLtaBlockClaim` (append-only ledger: annual credit, journey claim, block carry-forward).
  Block balances, remaining claims, and closing balance are all derived by summing the ledger, so a
  financial-year close never erases block history. The exemption gate is the well-established
  journey-count rule (2 per block, 1 carry-forward into the next block's first calendar year), not
  an invented monetary cap. **The current block's exact boundary years are seeded as a common
  default and explicitly flagged for statutory confirmation** — see
  `docs/business-rules/payroll-domain-enhancements.md`.
- **§8.2 Prorated Monthly Tax Recovery** — `IncomeTaxCalculationService` now prorates the even-share
  monthly TDS recovery against what's actually payable this period (new joiner, LOP, salary hold),
  instead of deducting the full normal amount against a shrunken payslip. The shortfall
  self-corrects on the next run (since `ytdTdsDeducted` only ever reflects what was actually
  recovered) and is logged to `TdsAdjustmentLog` for audit.
- **§8.3 Tax on Variable Payments** — one-time payments (bonus, incentive, arrears, ex-gratia) are
  never annualised; only the incremental tax they cause is computed and recovered in full the same
  period, tracked separately (`EmployeeTaxDeclaration.ytdVariablePaymentTdsRecovered`) so it never
  distorts the recurring monthly trajectory. `PayComponent.recurring` (default `true`) plus the
  `ARREAR_` code-prefix convention drive the recurring/one-time split, now centralized in
  `PayrollCalculator.oneTimeGross()` so both the real run and the simulation share one
  implementation.
- Both §8.2 and §8.3 were implemented directly (stable arithmetic/law, no statutory figure to
  verify); §8.1's block-boundary seed is the one figure flagged for confirmation. Per the
  mid-sprint clarification, nothing was hardcoded that depends on a recent/unverifiable
  notification — see the business-rules doc for the full assumption log and confirmation checklist.

### 3. Payroll features completed this sprint

| Feature | What shipped |
|---|---|
| Payroll Simulation (dry run) | `PayrollSimulationService` — runs the full calculation pipeline against live data and discards every result; creates no run, payslip, ESI enrollment, or tax-declaration update. Compares against the employee's most recent prior payslip, flags abnormal gross changes (>25%, a heuristic), and surfaces the same `PayrollValidator` report a real run would block on. `GET /api/v1/payroll/runs/simulate`. |
| Bulk Variable Input | `BulkVariablePaymentService` — bulk upload of Bonus/Incentives/Variable Pay/Arrears/Adjustments, each row becoming a `PayrollArrear` via the existing single-row creation path (no duplicated validation logic). Strictly all-or-nothing: `preview` validates without persisting, `commit` writes nothing at all unless every row passes, with a `REJECTED` batch header still recorded for audit either way. `POST /preview`, `POST /commit`, `GET /{batchId}` under `/api/v1/payroll/bulk-variable-payments`. |
| Payslip PDF generation | `PayslipPdfGenerationService` (Apache PDFBox 3.0.3, evaluated against OpenPDF/iText 7) renders employer-branded, optionally AES-256 password-protected payslip PDFs. `PayslipPdfService` wires up individual admin download, bulk ZIP download (one PDF per employee per run), and ESS self-service download on top of the existing `PayslipService` access checks. `PayslipSignatureService` interface + no-op default is the requested "digital signature architecture" (real PKI signing needs a company-provisioned certificate this sprint cannot invent). |
| Payroll Comparison | `PayrollComparisonService` — employee-by-employee new-joiner/leaver/changed/unchanged classification between two already-executed runs, plus total-level deltas. `GET /api/v1/payroll/runs/compare`. |
| Exception Reports | `PayrollExceptionReportService` — flags payslips worth manual review (no lines at all, zero/negative gross, net pay consumed to zero, high deduction ratio) before finalizing a run. `GET /api/v1/payroll/runs/{id}/exceptions`. |

### 4. Domain enhancements implemented

Covered in full in §2 above (§8.1/§8.2/§8.3).

### 5. ESS improvements

- `GET /api/v1/payroll/self-service/payslips/{id}/pdf` — download own payslip as PDF.
- `GET /api/v1/payroll/self-service/payslips/{id}/insights` — rule-based "explain my payslip"
  (payslip-line explanations + tax-adjustment reasons), the AI-ready foundation's first real
  consumer.

### 6. Payroll Administration improvements

Newly shipped this sprint: **Payroll Comparison** and **Exception Reports** (§3 above).

Verified as already existing from earlier sprints, not rebuilt: **Approval History** (the generic
Workflow engine, tied to Payroll via `PayrollApprovalWorkflowListener` since Sprint 14.3 —
`WorkflowInstanceService`/`WorkflowTaskService` already provide a full instance/task history for
the `PAYROLL_CLIENT_APPROVAL` workflow); **Reprocessing** (`PayrollRunService.startSupplementary` —
off-cycle correction runs for selected employees); **Search/Filtering** (`forCompany(status)` on
runs; per-employee/per-run payslip listing). **Payroll Audit** in the sense of "who did what and
when" is covered by `AuditableEntity`'s `createdBy`/`updatedBy`/timestamps on every payroll entity
plus the append-only `TdsAdjustmentLog`/`EmployeeLtaBlockClaim`/`BulkVariablePaymentBatch` tables;
there is no separate consolidated "audit log" UI/endpoint spanning every payroll table, which would
be a genuinely new cross-cutting feature, not a gap in any specific capability.

### 7. Statutory Return improvements

**None implemented this sprint — same conclusion as Sprint 24J, re-verified rather than assumed
stale.** PF's EPFO ECR exporter (Sprint 24I) remains the only government-format file exporter that
exists. ESIC, Professional Tax, and Labour Welfare Fund filing formats vary by state/portal and
still could not be independently verified against a primary source in this sandboxed environment,
so — per the sprint's explicit instruction to never invent a format — nothing new was built for
them. This is an open, flagged gap for a future sprint once specific target state(s)/portal
format(s) are confirmed with an authoritative source, not something Sprint 24K silently deferred
without re-checking.

### 8. AI Foundation

`PayrollInsightProvider` interface + `RuleBasedPayrollInsightProvider` (the only, default
implementation) — 100% deterministic, reuses existing calculation/audit data
(`PayslipLineExplainer`, `TdsAdjustmentLog`, `PayrollExceptionReportService`), zero LLM calls
anywhere in the codebase. Full detail and the documented future extension point in
`docs/architecture/ai-ready-payroll-foundation.md`. Explicitly NOT built: personalized tax-saving
suggestions, learned anomaly detection, an admin AI assistant — all genuinely new features outside
this sprint's "architecture only" instruction.

### 9. Knowledge Centre Foundation

`KnowledgeDocument` (+ `KnowledgeDocumentService`/`KnowledgeDocumentController`) — versioned,
effective-dated metadata records for statutory sources (Income Tax Act, CBDT/EPFO/ESIC/PT/LWF
circulars) and company policies, with plain-text search. Full detail, and an explicit list of what
is NOT built (ingestion pipeline, AI/semantic retrieval, full-text indexing), in
`docs/architecture/knowledge-centre-foundation.md`.

### 10. Files modified

97 files changed across 8 commits (`64b1658` through `ab9b1db`); see each commit's message for the
detailed breakdown by item. Headline new files: `IncomeTaxCalculationService` (rewritten),
`PayrollRunService` (statutory-amounts resolution updated), `LtaBlockConfiguration`/
`EmployeeLtaBlockClaim`/`LtaBlockService`, `PayrollSimulationService`,
`BulkVariablePaymentService`/`BulkVariablePaymentBatch`, `PayslipPdfGenerationService`/
`PayslipPdfService`/`PayslipBrandingConfiguration`, `PayrollComparisonService`/
`PayrollExceptionReportService`, `PayrollInsightProvider`/`RuleBasedPayrollInsightProvider`,
`KnowledgeDocument`/`KnowledgeDocumentService`.

### 11. Database changes

Four new migrations: `V55__tax_domain_enhancements.sql` (§8 tables +
`pay_components.recurring`), `V56__bulk_variable_payment_batches.sql`,
`V57__payslip_branding_configuration.sql`, `V58__knowledge_centre_foundation.sql`. All additive —
no destructive changes, no data migrations required.

### 12. APIs added/updated

- `GET /api/v1/payroll/runs/simulate`, `/compare`, `/{id}/exceptions`
- `POST/GET /api/v1/payroll/lta/*` (configurations, annual-credit, claims, carry-forward, summary,
  history)
- `POST /api/v1/payroll/bulk-variable-payments/{preview,commit}`, `GET /{batchId}`
- `GET /api/v1/payroll/payslips/{id}/pdf`, `/run/{runId}/pdf`
- `GET/POST /api/v1/payroll/payslip-branding`
- `GET /api/v1/payroll/self-service/payslips/{id}/{pdf,insights}`
- `POST/GET /api/v1/payroll/knowledge-documents/*`

### 13. Test summary

379 payroll-package tests (up from 340 before this sprint), 377 passing, 0 failures — the remaining
2 are the same pre-existing Testcontainers/Docker-sandbox errors present in every prior sprint's
report (this sandbox has no Docker daemon; CI, which does, is green). Full application-wide suite:
1,527 tests, 0 failures, 40 errors — all 40 are the same Docker-unavailable
`AbstractIntegrationTest` initialization failure across identity/tenancy/payroll integration test
classes, not payroll-specific and not new this sprint. Checkstyle, PMD, and SpotBugs all clean
(5 PMD findings and 1 SpotBugs finding surfaced during this sprint's own new code were fixed before
this report, not left for CI to catch).

### 14. Live verification

No live PostgreSQL/Redis instance is available in this sandbox (no Docker daemon), consistent with
every prior sprint's reporting in this document. Verification performed instead: (a) every new
Flyway migration was written following the exact column/constraint conventions of prior migrations
in this repo and will run through the same Flyway pipeline validated by
`FlywayMigrationValidationTest` in CI; (b) `mvn clean test-compile` was run repeatedly through this
sprint specifically to catch the kind of incremental-compiler false-positive documented in Sprint
24H-2/24J session notes; (c) all new services were unit-tested against mocked repositories with the
exact same access-control patterns (`ClientAccessGuard`) as existing, already-verified services.
PostgreSQL/Redis verification against a real instance remains a CI-only capability in this
environment, as in every prior sprint.

### 15. Remaining P0/P1/P2 issues

**P0 (blocking production for the specific rule):** none new this sprint. The one open item from
Sprint 24J (Payroll Approval is opt-in per tenant, not platform-wide-mandatory) is unchanged — a
product/policy decision, not a bug.

**P1 (should resolve before broad rollout of the specific feature):**
- LTA block boundary years need statutory confirmation before any tenant relies on the seeded
  default (`docs/business-rules/payroll-domain-enhancements.md`).
- ESIC/PT/LWF government-format return-file exporters remain unimplemented (§7 above) — same gap
  carried from Sprint 24J, still open.
- Payslip PDF employer branding is text-only; no logo image is rendered (documented, not a defect).
- Payslip digital signature is architecture-only (no-op); real signing needs a provisioned
  certificate.

**P2 (nice-to-have, not blocking):**
- Knowledge Centre has no ingestion UI/pipeline yet — documents must be created one at a time via
  API.
- No consolidated cross-table "payroll audit log" UI (per-table audit trails exist; see §6).
- AI-ready foundation covers payslip/tax-adjustment/exception explanation only; personalized
  tax-saving suggestions and an admin assistant are still future work.

### 16. Final Payroll production readiness

**Estimated 90%** for the scope defined across Sprints 24H-2 through 24K (core payroll run,
statutory PF/ESI/PT/LWF/TDS calculation, the three mandatory §8 domain enhancements, simulation,
bulk variable input, PDF generation, comparison/exception reporting, ESS, and both foundations).
The 10% gap is concentrated entirely in the two items explicitly deferred for good reason across
two consecutive sprints, not new to this one: ESIC/PT/LWF government-format exporters (no verifiable
primary source available in this environment) and the LTA block boundary's statutory confirmation
(a fact this environment cannot independently verify). Neither blocks running payroll correctly
today — both are flagged, documented, and configurable rather than silently assumed.

### 17. Recommendation: freeze Payroll V1

**Yes — recommend freezing Payroll Version 1 now.** Every mandatory item in the Sprint 24K brief
was delivered, tested, and documented; the two open gaps are pre-existing, explicitly flagged,
non-blocking for day-to-day payroll operation, and cannot be closed by more engineering effort in
this environment (they need an authoritative external source, not more code). Freezing now starts
the clock on Exit Management per the stated program objective, while the flagged gaps remain
visible in `docs/business-rules/payroll-domain-enhancements.md` for whoever picks them up.

### 18. Payroll Freeze Report

- **Scope frozen:** Payroll Version 1 as of commit `ab9b1db` on
  `claude/sprint-24h2-recovery-6q6u16`.
- **Statutory engine:** PF, ESI, Professional Tax, LWF, TDS (old + new regime, Section 87A marginal
  relief, HRA/LTA exemptions), §8.1/§8.2/§8.3 domain enhancements.
- **Operational capabilities:** run/finalize/freeze lifecycle, supplementary (off-cycle)
  reprocessing, simulation, bulk variable input, comparison, exception reporting, PDF generation,
  bank advice export, ECR export.
- **Self-service:** payslip view/download/insights, tax declaration, investment-proof upload, tax
  projection, dashboard.
- **Known, documented gaps (do not block freeze):** ESIC/PT/LWF file exporters; LTA block boundary
  confirmation; payslip logo rendering; digital signature is architecture-only.
- **Explicitly out of scope, not started (per sprint instruction):** Exit Management, any V2
  architecture, any LLM integration.

### 19. Git commit / push confirmation

All Sprint 24K work is committed and pushed to `claude/sprint-24h2-recovery-6q6u16` at
`origin`. Final commit: `ab9b1db` ("Fix static analysis findings in PayslipPdfGenerationService").
Eight sprint commits total: `64b1658`, `e05b3fb`, `1a382f7`, `c04d64a`, `19e8c8b`, `e7c629b`,
`43b3b87`, `ab9b1db`.

---

## 0. CTO Production Readiness Audit — 2026-07-27

A full pass across both repos to close production-readiness gaps found in
a CTO-level audit. Commits on `ewos-main` (this repo) and `main`
(`enterprise-core`, the frontend):

- **CI**: the `ci.yml` branch trigger never matched `ewos-main` — the branch
  every sprint since Sprint 1.1 actually developed on — so CI had not run on
  real work for the entire Sprint 1–4 program. Fixed. Also reformatted 139
  pre-existing files that had drifted out of Spotless compliance (cosmetic
  only — verified against the full unit suite before/after).
- **Security**: added `AdminPasswordGuard` (mirrors the existing
  `JwtSecretGuard`) — refuses to boot outside dev/test if `ADMIN_PASSWORD`
  is still the `ChangeMe!Admin123` placeholder. Flipped
  `server.error.include-message` / `include-binding-errors` to
  secure-by-default (`never`) in the base `application.yml`, with dev/test
  opting back into verbose messages explicitly (prod already had this right,
  but nothing stopped a profile-less or new deployment from leaking).
- **Deployment**: added `k8s/`, `helm/ewos/`, `.env.example`, and
  `docs/operations/deployment.md` — see that guide for the full picture.
  None of this existed before.
- **Frontend** (`enterprise-core`): added a CI workflow (it had none),
  Vitest/RTL component tests (it had none) and a Playwright e2e smoke suite,
  a React error boundary, a feature-flag framework, a `beforeLoad` route
  guard replacing a `useEffect`-based one, global 401/session-expiry
  handling, and a Dockerfile + deployment guide. See that repo's
  `PROJECT_STATUS.md`/README for details.

**Update — P9 validation pass.** Fixing the CI branch trigger above (bullet 1)
was necessary but not sufficient: it let CI *run* on `ewos-main` for the first
time, but the run kept failing at the `test` phase on six successive,
previously-invisible bugs, one at a time, each masking the next:

1. Three pre-existing PMD false positives (`UnusedPrivateMethod` on
   `this::method` references; `ConfusingArgumentToVarargsMethod` on a
   single-arg `List.of(...)` call) — suppressed narrowly with rationale
   comments.
2. `CorsConfig` was `final`, breaking Spring's CGLIB `@Configuration` proxy —
   fixed by moving its fail-fast validation into `afterPropertiesSet()`
   (`InitializingBean`), matching the `JwtSecretGuard`/`AdminPasswordGuard`
   pattern, instead of just dropping `final` (which would have re-triggered
   SpotBugs `CT_CONSTRUCTOR_THROW`).
3. `CandidateNumberGenerator` had two constructors, neither `@Autowired` nor
   no-arg — Spring can't pick one. The second was dead code; deleted.
4. `ExitInterviewRepository.findAllByTenantIdAndCompanyId` was a derived-query
   method referencing a field `ExitInterview` doesn't have — Spring validates
   every repository method at boot regardless of whether it's ever called.
   Dead/unused; deleted.
5. `LeaveRequestService` had the same ambiguous-constructor problem as #3, but
   here the second constructor **is** used by a real test — added
   `@Autowired` to the production constructor instead of deleting anything.
6. **`User`, `Role`, and `Permission` soft-delete never actually worked.**
   All three used `@SQLDelete(sql = "... version = version + 1 WHERE id = ?")`
   — one JDBC placeholder. Hibernate always binds a versioned entity's
   current `@Version` as a *second* parameter for any custom `@SQLDelete`,
   whether the SQL references it or not, so every delete on these three
   entities threw `PSQLException: column index is out of range: 2, number of
   columns: 1` (surfaced to callers as a generic 409). Four other
   soft-deletable entities already used the correct two-placeholder form
   (`... WHERE id = ? AND version = ?`); applied the same fix here. This was
   invisible in every prior sprint because the one integration test that
   exercises it never ran against real Postgres in CI until bugs 1–5 above
   were cleared.

A reflection-based scanner (`ConstructorScan`, ad hoc, not committed) was
written to re-verify #3/#5's whole bug class doesn't recur elsewhere: 336
Spring-stereotype classes checked, 0 flagged.

Clearing bugs 1–6 let all 780 tests pass for the first time and let Maven's
`verify` phase run `jacoco-check` for the first time too — which is how the
~33%-vs-80% coverage gap documented in §4/§11 was discovered. None of this
was reachable by local `mvn test`/`mvn verify` runs in this audit's sandbox,
which has no usable Docker registry access; real GitHub Actions CI (which
does have Docker) was the only way to find any of it.

Known limitations carried forward, not fixed by this pass (see §11.4):
GitHub's repository **default branch** could not be changed via any
available tool/API in this environment — `main` was fast-forwarded to match
`ewos-main`'s tip via a merged PR, but an operator with repo admin access
still needs to flip the default-branch setting in GitHub's UI/API if
`ewos-main` should stop being the branch developers push to.

---

## 1. Delivered by sprint

### Sprint 1 — foundation
- Spring Boot 3.3 on Java 21, Maven build.
- PostgreSQL 16 + Flyway (V1 baseline extensions).
- Spring Data Redis (config only).
- Spring Security stateless config (CSRF off, session `STATELESS`).
- JWT primitives (`JwtService`, `JwtAuthenticationFilter`, `JwtProperties`).
- Actuator liveness / readiness / health.
- springdoc-openapi + Swagger UI.
- Profiles `dev` / `test` / `prod`; `logback-spring.xml` with prod rolling file appender.
- Global `ApiError` + `GlobalExceptionHandler`.
- Multi-stage Dockerfile, `docker-compose.yml` for app + Postgres + Redis (healthchecked).
- `AbstractIntegrationTest` on Testcontainers Postgres; `contextLoads` smoke test.

### Sprint 2 — identity module
- Flyway V2 (schema) + V3 (seed permissions + `SYSTEM_ADMIN` role).
- Entities: `Permission`, `Role`, `User`, `RefreshToken` under `com.ewos.identity.domain`.
- Spring Data repositories with `existsByUsername` / `existsByEmail` / `findByTokenHash`.
- `POST /api/v1/auth/login` — BCrypt verify → JWT access + opaque refresh.
- `POST /api/v1/auth/refresh` — rotates refresh tokens (old is revoked before the new pair is minted).
- Access tokens carry `authorities` (`ROLE_<name>` + permission codes); `JwtAuthenticationFilter` hydrates `GrantedAuthority` from the claim.
- `IdentityBootstrap` (`ApplicationRunner`, idempotent) creates the default admin from `app.security.bootstrap.admin.*`.
- Refresh tokens are 48-byte `SecureRandom` Base64Url values stored SHA-256-hashed.

### Sprint 4 — user management
- Flyway V4 — `password_history`, `login_history`, `created_by` / `updated_by` on identity tables, `password_changed_at` on users.
- `AuditableEntity` — `@CreatedDate` / `@LastModifiedDate` / `@CreatedBy` / `@LastModifiedBy` via Spring Data auditing; `AuditorProvider` reads the JWT subject.
- Configurable password policy (`PasswordPolicyProperties` + `PasswordPolicyValidator`) — length min/max, upper/lower/digit/special toggles, history size.
- `PasswordHistoryService` blocks reuse of the last N BCrypt hashes.
- `LoginHistoryRecorder` writes every login attempt (including unknown-username) in a `REQUIRES_NEW` transaction so failures aren't lost to outer rollback.
- `UserService` — create, update, setEnabled, resetPassword, changePassword, getById, `Specification`-driven search with `Pageable`.
- `UserController` — full user-management REST surface with springdoc annotations and method-level `@PreAuthorize`.

### Sprint 5 — hardening
- **CI**: `.github/workflows/ci.yml` runs Spotless, Checkstyle, PMD, SpotBugs, and full `mvn verify` on every push and PR.
- **Static analysis**: Spotless (Google Java Format AOSP), Checkstyle 10.18, PMD 7.6, SpotBugs — all bound to the `verify` phase and currently clean.
- **JaCoCo**: 80 % instruction-coverage floor on the BUNDLE (excludes bootstrap / config / DTO / entity / repository interface classes so the floor targets business logic).
- **Soft delete**: users / roles / permissions carry `deleted_at` + `version`; entities use `@SQLDelete` + `@SQLRestriction("deleted_at IS NULL")`; full-column UNIQUE constraints replaced with partial unique indexes so a soft-deleted row keeps its identifiers without blocking reuse.
- **Optimistic locking**: `@Version` on all three entities; concurrent-modification conflicts surface as `409 Conflict` via a `OptimisticLockingFailureException` handler.
- **Logout**: `POST /api/v1/auth/logout` — idempotent, always `204`, revokes the presented refresh token, records a `LOGOUT` audit event.
- **Login audit**: new `LoginEventType` enum (`LOGIN_SUCCESS`, `LOGIN_FAILURE`, `LOGOUT`, `REFRESH_SUCCESS`, `REFRESH_FAILURE`); refresh attempts audited with distinct failure reasons (`revoked` / `expired` / `unknown`); IP + User-Agent captured on refresh + logout too.
- **API consistency**: `CorrelationIdFilter` (HIGHEST_PRECEDENCE) reads/mints `X-Request-ID`, publishes it on the response, and places it in the SLF4J MDC. `ApiError` carries a `correlationId` field. `GlobalExceptionHandler` gained handlers for `HttpMessageNotReadable`, `MissingServletRequestParameter`, `MethodArgumentTypeMismatch`, `HttpRequestMethodNotSupported`, `NoResourceFound`, and `OptimisticLockingFailure` — every 4xx/5xx now returns the same `ApiError` shape.
- **README**: badges + `Quality gates & CI` section + endpoint updates + soft-delete / correlation-ID / login-audit docs.

---

## 2. Checklist status (Sprint 5 mandate)

| # | Item                                          | Status  | Evidence                                                                                       |
| - | --------------------------------------------- | ------- | ---------------------------------------------------------------------------------------------- |
| 1 | GitHub Actions CI/CD                          | ✅ Done | `.github/workflows/ci.yml`                                                                     |
| 2 | Spotless                                      | ✅ Done | `pom.xml`, config in `com.diffplug.spotless:spotless-maven-plugin` block                       |
| 3 | Checkstyle                                    | ✅ Done | `config/checkstyle/checkstyle.xml`                                                             |
| 4 | PMD                                           | ✅ Done | `config/pmd/pmd-ruleset.xml`                                                                   |
| 5 | SpotBugs                                      | ✅ Done | `config/spotbugs/spotbugs-exclude.xml`                                                         |
| 6 | JaCoCo ≥ 80 % coverage                        | ✅ Done | `jacoco-maven-plugin` `check` execution; BUNDLE / INSTRUCTION / COVEREDRATIO / 0.80            |
| 7 | Configurable password policy                  | ✅ Done | `PasswordPolicyProperties`, `PasswordPolicyValidator`, `app.security.password-policy.*` config |
| 8 | Soft delete for users / roles / permissions   | ✅ Done | V5 migration, `@SQLDelete` + `@SQLRestriction`, `DELETE /api/v1/users/{id}`                     |
| 9 | Complete audit fields                         | ✅ Done | `created_at` / `updated_at` / `created_by` / `updated_by` / `version` / `deleted_at`           |
| 10 | Refresh-token revocation on logout           | ✅ Done | `POST /api/v1/auth/logout`, `AuthenticationService.logout(...)`                                |
| 11 | Login audit improvements                     | ✅ Done | `LoginEventType`, `login_history.event_type`, refresh / logout events audited                  |
| 12 | Consistent API response model                | ✅ Done | `ApiError` (+ correlationId), broadened `GlobalExceptionHandler`, `CorrelationIdFilter`        |
| 13 | README improvements                          | ✅ Done | Badges, Quality-gates section, endpoint tables, soft-delete/correlation/audit docs             |

---

## 3. Technology inventory

| Concern       | Choice                                                    |
| ------------- | --------------------------------------------------------- |
| Language      | Java 21                                                   |
| Framework     | Spring Boot 3.3.5                                         |
| Build         | Maven 3.9                                                 |
| Database      | PostgreSQL 16                                             |
| Migrations    | Flyway (core + `flyway-database-postgresql`)              |
| Cache         | Redis 7 (config only, no runtime usage yet)               |
| Auth          | Spring Security + JJWT 0.12 (HS256)                       |
| ORM           | JPA / Hibernate 6                                         |
| API docs      | springdoc-openapi 2.6 + Swagger UI                        |
| Observability | Spring Boot Actuator + SLF4J MDC + `X-Request-ID`         |
| Tests         | JUnit 5 + AssertJ + Mockito + Testcontainers 1.20         |
| Container     | Docker + Docker Compose                                   |
| Formatter     | Spotless with Google Java Format (AOSP style)             |
| Linters       | Checkstyle 10.18, PMD 7.6, SpotBugs 4.8                   |
| Coverage      | JaCoCo 0.8 (≥ 80 % INSTRUCTION on the bundle)             |

---

## 4. Test coverage

| Suite                                | Kind         | Count | Runs locally without Docker? |
| ------------------------------------ | ------------ | ----- | ---------------------------- |
| `JwtServiceTest`                     | unit         | 2     | ✅                           |
| `PasswordPolicyValidatorTest`        | unit         | 7     | ✅                           |
| `UserServiceTest`                    | unit (mocks) | 12    | ✅                           |
| `AuthenticationServiceTest`          | unit (mocks) | 12    | ✅                           |
| `AuthControllerIntegrationTest`      | integration  | 5     | needs Docker (Testcontainers Postgres) |
| `UserControllerIntegrationTest`      | integration  | 12    | needs Docker                 |
| `EwosApplicationTests` (`contextLoads`) | smoke     | 1     | needs Docker                 |

**Unit total: 33 / 33 passing locally.** Integration suite (18 tests) runs in CI, where Docker is preinstalled on `ubuntu-latest`.

**Update, 2026-07-27 audit.** The line above ("the 80% floor is enforced ... during `mvn verify`") was aspirational, not verified: from Sprint 5 onward, every CI run on `ewos-main` failed at the `test` phase itself (a mix of unrelated boot-crashing bugs — see §0 below) and Maven's reactor never got as far as the `verify` phase's `jacoco-check` goal. The 80% number had never actually been checked against a real run. Once this audit's fixes let the full 780-test suite pass for the first time, `jacoco-check` ran for real and reported **~33%** aggregate instruction coverage — the first honest measurement this project has ever had.

Rather than discount the gate down to that number, `ExitServiceTest` and `SuccessionServiceTest` were added (covering the two largest previously-**zero**-coverage service classes, `ExitService` and `SuccessionService` — 206 of the project's 332 non-excluded classes had no test at all touching them) to push real coverage genuinely above a new floor. `jacoco.line.coverage.min` is now `0.35` — backed by added tests, not a discount — as the first step of the staged roadmap in §11: **35% now → 50% before Beta → 65% before RC → 80% before GA**. Raise the number only as fast as real tests land; never move it ahead of the tests that justify it.

---

## 5. Endpoint catalog

### Authentication (`com.ewos.identity.api.AuthController`)

| Method | Path                    | Auth              | Notes                                                          |
| ------ | ----------------------- | ----------------- | -------------------------------------------------------------- |
| POST   | `/api/v1/auth/login`    | public            | Issues JWT access + opaque refresh                             |
| POST   | `/api/v1/auth/refresh`  | public            | Rotates refresh token (old one revoked)                        |
| POST   | `/api/v1/auth/logout`   | public            | Idempotent; always 204; revokes token; audits `LOGOUT`         |

### User management (`com.ewos.identity.api.UserController`)

| Method | Path                                    | Required authority        | Notes                                              |
| ------ | --------------------------------------- | ------------------------- | -------------------------------------------------- |
| POST   | `/api/v1/users`                         | `SYSTEM_ADMIN`            | 409 on duplicate username/email                    |
| GET    | `/api/v1/users`                         | `USER_READ`               | Paged/sorted/filtered                              |
| GET    | `/api/v1/users/{id}`                    | `USER_READ`               | 404 if unknown or soft-deleted                     |
| PUT    | `/api/v1/users/{id}`                    | `USER_WRITE`              | Updates email + roles                              |
| PATCH  | `/api/v1/users/{id}/status`             | `USER_WRITE`              | Enable / disable                                   |
| POST   | `/api/v1/users/{id}/reset-password`     | `USER_WRITE`              | Admin reset (policy + reuse checks)                |
| POST   | `/api/v1/users/me/change-password`      | any authenticated         | Verifies current password first                    |
| DELETE | `/api/v1/users/{id}`                    | `USER_DELETE`             | **Soft** delete — sets `deleted_at`                |

---

## 6. Configuration surface (env vars)

| Variable                        | Purpose                                    | Default (dev)              |
| ------------------------------- | ------------------------------------------ | -------------------------- |
| `SPRING_PROFILES_ACTIVE`        | Active Spring profile                      | `dev`                      |
| `SPRING_DATASOURCE_URL`         | JDBC URL                                   | `jdbc:postgresql://...`    |
| `SPRING_DATASOURCE_USERNAME`    | DB user                                    | `ewos`                     |
| `SPRING_DATASOURCE_PASSWORD`    | DB password                                | `ewos`                     |
| `SPRING_DATA_REDIS_HOST/PORT`   | Redis host / port                          | `localhost:6379`           |
| `JWT_SECRET`                    | HMAC secret (≥ 256 bits in prod)           | placeholder                |
| `JWT_ISSUER`                    | JWT `iss` claim                            | `ewos`                     |
| `JWT_ACCESS_TTL`                | Access-token lifetime                      | `15m`                      |
| `JWT_REFRESH_TTL`               | Refresh-token lifetime                     | `7d`                       |
| `ADMIN_USERNAME/EMAIL/PASSWORD` | Default admin identity                     | `admin` / `admin@ewos.local` / `ChangeMe!Admin123` |
| `PASSWORD_MIN_LENGTH`           | Minimum password length                    | `8`                        |
| `PASSWORD_MAX_LENGTH`           | Maximum password length                    | `128`                      |
| `PASSWORD_REQUIRE_UPPERCASE`    | Require ≥ 1 uppercase                      | `true`                     |
| `PASSWORD_REQUIRE_LOWERCASE`    | Require ≥ 1 lowercase                      | `true`                     |
| `PASSWORD_REQUIRE_DIGIT`        | Require ≥ 1 digit                          | `true`                     |
| `PASSWORD_REQUIRE_SPECIAL`      | Require ≥ 1 special character              | `true`                     |
| `PASSWORD_HISTORY_SIZE`         | # of past passwords blocked from reuse     | `5`                        |

---

## 7. Remaining technical debt

Prioritized. None of these blocks moving into the next sprint, but each should be tackled before the platform reaches production.

### High priority
1. ~~**`AbstractIntegrationTest` container reuse across classes**~~ ✅ **Resolved by PR #4.** `AbstractIntegrationTest` now uses the singleton-container pattern: one Postgres per JVM, started in a static initializer, terminated by Ryuk at JVM exit. See `CONTRIBUTING.md` § 6.4 and "Common pitfalls" below for the failure mode this closes.
2. ~~**No CORS bean is registered**~~ ✅ **Resolved.** `CorsConfig` registers a config-driven `CorsConfigurationSource` (`app.cors.*`), with a prod-profile fail-fast guard against wildcard origins — see ADR-0004.
3. ~~**Actuator scrape endpoints are unauthenticated beyond health/info**~~ ✅ **Resolved.** `application-prod.yml` restricts `management.endpoints.web.exposure.include` to `health,info` only; nothing else is exposed in prod.
4. ~~**`JWT_SECRET` default in `application.yml`**~~ ✅ **Resolved.** `JwtSecretGuard` refuses to boot outside dev/test with a placeholder or under-length secret. As of the 2026-07-27 audit, `AdminPasswordGuard` closes the equivalent gap for `ADMIN_PASSWORD`.

### Medium priority
5. **Role / Permission admin API is missing** — entities support soft delete + versioning but there's no controller to CRUD them. Blocked by product scope decision (assign / mint permissions at runtime vs. seed-only).
6. **Restore-from-soft-delete** — no `POST /api/v1/users/{id}/restore` yet. Simple to add (`UPDATE users SET deleted_at = NULL WHERE id = ?`), but partial-unique-index collision has to be handled first (a live row with the same username may exist).
7. **Refresh tokens aren't bound to a device / session** — reuse detection would be stronger if refresh tokens carried a family id and rotation-chain detection revoked the whole family on reuse. Currently only the presented token is revoked on `logout` / `refresh`.
8. **No account lockout / brute-force throttling** — Sprint 4 excluded this. `login_history` gives us the raw data; a simple threshold-based lock (N failed attempts within M minutes → set `accountNonLocked = false`) would close the gap.
9. **No `spring.jpa.hibernate.ddl-auto=validate`** — we run with `none`. Enabling `validate` in CI would catch entity/schema drift early. Requires cleaning up small mismatches (nullable / length) that today are harmless.
10. **No `@ControllerAdvice`-level request logging** — CorrelationIdFilter puts the id in MDC, but there is no access-log filter. `logbook`, `spring-boot-starter-actuator` request-metrics, or a simple `HandlerInterceptor` would give us the "one line per request" that ops teams expect.

### Low priority
11. **Testcontainers reuse via `.withReuse(true)` + `~/.testcontainers.properties` opt-in** — meaningful only for local iterative runs.
12. **Optimistic-lock retry policy** — currently 409s propagate to the client. A single retry on conflict for `changePassword` / `resetPassword` would improve UX under contention.
13. **OpenAPI examples on request/response bodies** — schemas are described, but only a handful of DTO fields carry `@Schema(example = ...)`.
14. **Refresh-token cleanup** — `RefreshTokenRepository` has a `deleteAllExpired(Instant)` query but no scheduled job runs it. Add `@Scheduled` daily sweep.
15. **Coverage exclusions could shrink** — `common/persistence/AuditorProvider` and `common/web/CorrelationIdFilter` deserve tests; currently the coverage exemption on `common/**` masks them.

### Not in scope for this repo
16. ~~Employee, Payroll, Leave, Attendance, and Organization modules — deferred to their respective sprints.~~ ✅ **Resolved.** All five shipped (WP-005/007/008/009, plus Sprint 6/7 Company/Organization work referenced elsewhere) well before this correction — this line was simply never updated. See §14.
17. **PF ECR return file doesn't track NCP days or refund of advances** (Sprint 24I) — both statutory-return columns are emitted as `0` because neither is threaded through `StatutoryDeductionService` yet. A filer must correct these two columns by hand until LOP/non-contributory days are tracked at the statutory-deduction level. Also: return-file generation only supports the PF scheme today — ESI/PT/LWF challans get a 400 from `generateReturnFile` rather than an invented file format.

---

## 8. Common pitfalls

Real issues we've hit in this repo, the failure modes, and how the codebase now prevents them. Read this before making changes in the neighborhood.

### 8.1 Testcontainers + multiple `@SpringBootTest` classes in one JVM

**Symptom.** Every request in one integration-test class returns `500` with a JDBC connection-refused root cause, while a *different* integration-test class in the same CI run passed. Local unit tests are all green.

**Failure mode.** A static `@Container` field on an `@Testcontainers`-annotated base class scopes the container's lifecycle to *one* test class. The JUnit extension calls `container.start()` before the class and `container.stop()` after. The next `@SpringBootTest` class calls `start()` again on the same reference — but Testcontainers `GenericContainer.start()` is a **silent no-op on a container that has already been started and removed**. No exception, no restart. Spring's `@DynamicPropertySource` hands the *dead* port to Hikari, which times out at `connection-timeout` (default 30 s) per test attempt.

We hit this in CI [run 28996473363](https://github.com/buntychavan-web/EWOS/actions/runs/28996473363): 14 `UserControllerIntegrationTest` cases each burned 30 s of "connection refused" before failing.

**Prevention (already in place).** `AbstractIntegrationTest` uses the singleton-container pattern — start once in a `static { }` block, never call `stop()`, let Ryuk clean up at JVM exit. All `@SpringBootTest` classes in the JVM share one container. `CONTRIBUTING.md` § 6.4 documents this and forbids `@Testcontainers` / `@Container` in the codebase.

### 8.2 Full-column `UNIQUE` on soft-deletable tables

**Symptom.** Soft-deleting a user, then creating a new user with the same username, fails with a constraint violation instead of succeeding.

**Failure mode.** A full-column `UNIQUE (username)` constraint counts the soft-deleted row. `SELECT` queries filter it out via `@SQLRestriction`; `INSERT` doesn't.

**Prevention.** V5 migration replaced full-column `UNIQUE` on `users.username`, `users.email`, `roles.name`, `permissions.code` with **partial unique indexes** (`WHERE deleted_at IS NULL`). Any future soft-deletable table must follow the same pattern — `CONTRIBUTING.md` § 4 states the rule.

### 8.3 Editing a merged Flyway migration

**Symptom.** Production deploy aborts with `Validate failed: Migration checksum mismatch for migration V<n>`.

**Failure mode.** Flyway records the checksum of each migration when it first runs. Editing the file — even a comment or whitespace — changes the checksum; Flyway refuses to continue.

**Prevention.** Migrations are append-only. `CONTRIBUTING.md` § 4 forbids editing merged migrations; ship a new `V<next>__...sql` that alters/undoes.

### 8.4 Auditor is null on public / bootstrap flows

**Symptom.** Rows created by `IdentityBootstrap` or during login/refresh/logout have `created_by = NULL`.

**Failure mode.** `AuditorProvider` returns `Optional.empty()` when the `SecurityContext` has no authenticated principal — which is exactly the case for the initial admin bootstrap and for anything hit through `/api/v1/auth/*` (those endpoints are `permitAll()`).

**Prevention.** This is intentional and documented in `AuditorProvider`. Do **not** invent a "system" auditor to paper over it — that would obscure genuine anonymous writes. If a real use case emerges (e.g. attributing scheduled-job writes), add a distinct sentinel UUID with an explicit contract, don't reuse a real user id.

### 8.5 Logging secrets or tokens

**Symptom.** Access tokens, refresh tokens, or BCrypt hashes appear in stdout / log files.

**Failure mode.** A well-meaning `log.info("...{}", tokenResponse)` prints the full `TokenResponse#toString()`, including the access + refresh tokens.

**Prevention.** SLF4J is available; use it *only* on non-sensitive payloads. Refresh tokens are SHA-256 hashed before storage, so the plaintext exists in memory only during a single request. Do not log `TokenResponse`, `LoginRequest`, `ChangePasswordRequest`, `ResetPasswordRequest`, or their fields. Correlation IDs are safe to log — that's the point of the MDC.

### 8.6 `Checkstyle` `ConstantName` vs SLF4J `log`

**Symptom.** Checkstyle fails on every `private static final Logger log = ...` line because the default `ConstantName` rule expects `UPPER_SNAKE`.

**Failure mode.** Checkstyle's out-of-the-box `ConstantName` treats `static final` fields as constants and enforces the case convention.

**Prevention.** `config/checkstyle/checkstyle.xml` deliberately omits `ConstantName`. Lowercase `log` is idiomatic SLF4J and this repo prefers it. If you add a *real* constant (`public static final int MAX_FOO = 42`), do use `UPPER_SNAKE`.

### 8.7 Loosening a quality gate to make CI green

**Symptom.** Someone raises a JaCoCo threshold from 80 % to 60 %, or adds a broad `<exclude>com/ewos/**</exclude>` to shut up SpotBugs.

**Failure mode.** The gate stops enforcing what it was there for. Regressions land quietly.

**Prevention.** `CONTRIBUTING.md` § 2 lists this as prohibited without explicit reviewer + team-lead consent. The PR template's "Scope guardrails" checklist asks the author to confirm no gate was loosened. Reviewers should reject PRs that touch `pom.xml` gate configuration without a linked justification.

---

## 9. How to run

Full instructions live in [`README.md`](./README.md). Quick reference:

```bash
# Format + full CI-equivalent pipeline (needs Docker)
mvn spotless:apply
mvn -q verify

# Unit tests only (no Docker required)
mvn -q test

# Boot the app with Postgres + Redis via compose
docker compose up --build
# Swagger UI: http://localhost:8080/swagger-ui.html
```

---

## 11. Remaining known risks (2026-07-27 audit)

- **Test coverage was ~33% aggregate instructions when first measured, not
  the 80% this document previously claimed** (see §4 for how this was
  discovered — the 80% gate had silently never run to completion in CI since
  Sprint 5). 206 of 332 non-excluded classes — mostly application-layer
  `*Service` classes across the Sprint 1–14 feature modules — had **zero**
  test coverage. `ExitServiceTest` and `SuccessionServiceTest` were added in
  this same pass to raise real coverage past a genuine `0.35` floor rather
  than discounting the gate to match the as-found number; see `pom.xml`'s
  `jacoco.line.coverage.min` comment. This is still a real production risk
  and should be the **next sprint's headline priority**, via a staged
  roadmap rather than one big backfill:
  - **35%** — done, this pass (`ExitService`, `SuccessionService`).
  - **50%** — before Beta. Pull the `jacoco-report` artifact from a green CI
    run, sort the remaining ~204 zero-coverage classes by instruction count
    descending, and work down the list (`OfferService` 1090,
    `OnboardingPlanService` 929, `AppraisalService` 911,
    `PreboardingService` 881, `JobRequisitionService` 809, `ProbationService`
    783, `GoalService` 735, `PayrollReportsService` 723, ... — see the full
    ranked list this audit produced from `target/site/jacoco/jacoco.csv`).
  - **65%** — before RC. Backfill the remaining mid-size services and start
    closing branch/edge-case gaps in already-tested classes, not just
    adding one happy-path test per class.
  - **80%** — before GA, matching the original Sprint 5 target.

  Raise `jacoco.line.coverage.min` only as fast as real tests land at each
  stage — never move the number ahead of the tests that justify it (§8.7).

- **GitHub default branch** — still `main` at the GitHub settings level in
  a way this environment's tools couldn't change (no repo-settings API
  available). `main` has been fast-forwarded to `ewos-main`'s tip so there
  is no code divergence, but a repo admin should still flip the default
  branch in GitHub's UI if the intent is for `ewos-main` to stop being a
  separate line developers push to.
- **Backend↔frontend API routing in production** — the frontend's
  `/api/v1/*` calls are same-origin relative paths with no production
  reverse-proxy/rewrite configured (see `enterprise-core`'s
  `docs/DEPLOYMENT.md`). An operator must wire this up per their actual
  Cloudflare/ingress topology before both sides can talk to each other in a
  real deployment.
- **Kafka messaging is off by default** (`APP_MESSAGING_KAFKA_ENABLED=false`)
  and untested against a real broker outside `docker-compose.yml`'s local
  dev setup.
- **`helm/ewos` charts were not run through `helm lint`/`helm template`** —
  no Helm CLI was reachable in the sandbox this audit ran in (network
  policy blocked `get.helm.sh`). Reviewed by hand against the raw `k8s/`
  manifests they mirror; run `helm lint` before a first real install.
- Items 5–16 in §7 above (Role/Permission admin API gaps notwithstanding —
  much of that has since shipped in Sprint 1.4 — refresh-token device
  binding, restore-from-soft-delete, JPA `ddl-auto=validate` in CI, request
  access logging, Testcontainers reuse, optimistic-lock retry, OpenAPI
  examples, refresh-token cleanup job, coverage exclusions) were not
  re-verified in this audit pass and should be treated as still open unless
  a later section of this document says otherwise.

---

## 12. Change log for this document

- **2026-07-09** — Initial version. Reflects the tip of the `claude/quality-hardening` branch after the Sprint 5 hardening PR.
- **2026-07-09** — Added § 8 "Common pitfalls" with the Testcontainers singleton-container writeup, soft-delete/UNIQUE, Flyway checksum, null auditor, log-hygiene, Checkstyle-vs-SLF4J-log, and gate-loosening. Marked tech-debt item #1 as resolved by PR #4. Renumbered § 8 → § 9 and § 9 → § 10.
- **2026-07-27** — CTO Production Readiness Audit: added §0 summarizing the audit's changes, marked tech-debt items #2–#4 in §7 as resolved (CORS bean, actuator exposure, JWT secret guard — plus the new `AdminPasswordGuard`), added §11 "Remaining known risks" reflecting this pass's findings, and noted that this document's §§1–10 predate the Sprint 1.1–4/2.x program and were not rewritten wholesale in this pass — treat sprint-by-sprint detail past Sprint 5 as living in each sprint's own completion report rather than here.
- **2026-07-27 (P9 validation)** — Fixing the CI trigger only got CI *running*; getting it to actually complete uncovered six previously-invisible bugs (three PMD false positives, a non-proxyable `final @Configuration` class, two ambiguous-Spring-constructor bugs, one dead derived-query method, and a Hibernate `@SQLDelete`/`@Version` bug that meant `User`/`Role`/`Permission` soft-delete had never worked) — see §0's new subsection for detail. Clearing all six let `mvn verify` reach `jacoco-check` for the first time ever, which is how the real ~33%-vs-80%-claimed coverage gap in §4 was found. Rather than discount the gate, added `ExitServiceTest`/`SuccessionServiceTest` (the two largest of 206 zero-coverage service classes) to genuinely clear a new `0.35` floor, and documented a staged `35% → 50% → 65% → 80%` roadmap tied to Beta/RC/GA in §11.
- **2026-07-28 (Sprint 15 — Enterprise Quality & Reliability)** — Quality-only sprint, no new features: 16 new test files / 149 new test methods across payroll, statutory compliance, employee lifecycle, organization, and a permanent regression suite for two of the P9 findings. Found and fixed one new bug while writing tests (`StatutoryDeductionService`'s in-run duplicate-code check). Backend now at 986 tests, 0 failures, CI still green. Added §13 with full detail and a new `TESTING.md` guide.
- **2026-08-02 (Sprint 24I — Payroll V1 completion + housekeeping)** — A live repository review found this document hadn't been updated since Sprint 15, despite the entire T1–T12 Talent/Recruitment/Exit suite, the WP-001–009 foundation/payroll build-out, and Sprints 16 through 24H-2 having shipped in the meantime (some of it, per git history, *before* Sprint 15). Added §14 to backfill all of it in summary form, and §15 documenting Sprint 24I itself: the PF ECR statutory return-file feature (closing the "government compliance output" gap identified in the CTO review that opened this sprint) and this document update. Corrected §7 item 16 (falsely claimed Payroll/Employee/Leave/Attendance/Organization were out of scope) and added item 17 for the ECR feature's known NCP-days/refund-of-advances limitation.
- **2026-08-02 (Sprint 24J — Payroll Version 1 finalization)** — A full audit of the Payroll module against a ~40-item ESS/Administration/Statutory/Lifecycle checklist (to avoid duplicating anything already built) preceded any code change. Shipped: payslip detail + line-by-line explanations, a self-service dashboard, on-demand income-tax projection, self-service investment declaration, and an investment-proof-upload foundation for future Year-End Tax Compliance (Form 16 itself explicitly out of scope this sprint) on the ESS side; on the admin side, wired `PayrollRunService.recordValidationReport()` — present since an earlier sprint but never actually called, meaning the Payroll Exception Log had silently never populated — plus cross-period Payroll Run History and an Activity Timeline built from data already on `PayrollRun`. Statutory returns beyond PF (ESIC/PT/LWF) and several lifecycle items (attendance-based LOP, bulk variable-pay import, a simulation/dry-run API, dedicated loan/reimbursement tracking) were reviewed and deliberately not built — each would need either a verified government spec or scope beyond "finalize V1," so each is documented in §16 rather than guessed at. Added §16 with full detail.
