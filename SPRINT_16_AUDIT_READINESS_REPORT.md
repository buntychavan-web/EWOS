# Sprint 16 — Final Quality, Audit & Pilot Readiness — Completion Report

**Date:** 2026-07-28
**Scope:** Quality, audit, and pilot-readiness only — no new HRMS business functionality, no new
business modules, per the sprint's explicit constraint.
**Repo:** `buntychavan-web/EWOS`, branch `ewos-main`.
**Final commit:** `cf9ea50c43d22ffd83e6d1b7fd3196d3886a535d`
**CI:** green (run `30375337037`, https://github.com/buntychavan-web/EWOS/actions/runs/30375337037)

---

## 1. Executive Summary

Sprint 16 closed the two named business-critical test gaps (Attendance, Onboarding), extended that
closure to the highest-risk *remaining* gap surfaced by a fresh coverage run (five untested
payroll-application services), certified all 17 named payroll scenarios against the automated test
suite, and completed an independent production-readiness review that found the deployment
configuration already correct with no unsafe fixes needed. Three commits were pushed this sprint,
each individually verified green in GitHub Actions CI before the next was started. No production
(`src/main/java`) code was modified anywhere in this sprint — every change is either a new test file
or a documentation artifact.

## 2. Sprint Objectives Completed

| # | Task | Status |
|---|------|--------|
| 1 | Complete remaining business-critical service-layer tests (Attendance, Onboarding + review) | Done — see §3, §4 |
| 2 | Fresh JaCoCo coverage analysis + realistic quality-gate recommendation | Done — see §5 |
| 3 | Payroll certification scenarios (17 named scenarios) | Done — see §6 |
| 4 | Production readiness review (build/backend/frontend/DB/Docker/K8s/logging/monitoring/config/secrets/backup-DR) | Done — see §7 |
| 5 | Code quality cleanup (dead code, duplicates, unused imports, TODOs) | Reviewed — see §8 |
| 6 | GitHub verification (commit/push everything, CI green, no stray files) | Done — see §10 |
| 7 | Audit Readiness Report | This document |

## 3. Attendance Testing Status

Three previously-untested Attendance service classes now have full unit-test coverage:

- **`AttendancePolicyServiceTest`** (8 tests) — per-tenant code uniqueness, company-guard skip for
  tenant-wide policies, partial-update semantics, and the company-scoped-wins-over-tenant-wide
  effective-policy resolution.
- **`TimeEntryServiceTest`** (10 tests) — employee/company validation, source defaulting, and the
  correction chain forcing `TimeEntrySource.CORRECTION`.
- **`TimesheetServiceTest`** (17 tests) — the full open → recompute → submit → approve/reject →
  cancel lifecycle, including workflow-engine integration via `WorkflowInstanceService`.

35 tests, all passing. Committed as `b7d4077`, CI-confirmed green.

## 4. Onboarding Testing Status

Three previously-untested Onboarding service classes now have full unit-test coverage:

- **`OnboardingPlanServiceTest`** (17 tests) — idempotent plan creation, task materialization from
  templates, task-status transitions, completion-percentage rollup math (`BigDecimal`/HALF_UP), and
  mandatory-task-gated plan completion.
- **`CandidateConversionServiceTest`** (10 tests) — the New Joiner handover: offer-status gate,
  employee-number generation/collision, work-email derivation/override, joining-date resolution, and
  idempotent hand-off into onboarding.
- **`OnboardingTaskTemplateServiceTest`** (6 tests) — per-company code uniqueness and default
  mandatory/owner/active values.

33 tests, all passing. Committed as `a0df3ff`, CI-confirmed green.

## 5. Coverage Report

Measured locally via `mvn test jacoco:report` (unit tests only — this sandbox has no Docker, so the
~32 Testcontainers-based integration tests cannot execute here; they do execute in GitHub Actions CI,
which has Docker and is green on every commit this sprint).

| Metric | Before Sprint 16 | After Sprint 16 |
|---|---|---|
| Overall line coverage | 46.96% (6,452 / 13,740) | **50.91%** (6,995 / 13,740) |
| Overall branch coverage | 39.69% | **43.48%** |
| Unit tests passing | 1,020 | **1,115** |
| `com.ewos.payroll.application` package coverage | — | **78.93%** (up from having 5 classes at 0%) |
| Zero-coverage classes | 159 | 154 |

**Module-wise (packages below 50% line coverage, selected):** `ats.application` 2.78%,
`competency.application` 3.16%, all `*.infrastructure.messaging` packages ~4% (thin
event-publishing wrappers), `shared.web` 20.45%, `ats.api` 31.11%, `offer.api` 34.16%,
`employee.api` 36.26%, `identity.api` 36.89%, `probation.application` 41.07%, `payroll.api` 42.93%.

**Zero-coverage classes still remaining (application-layer, excluding controllers/thin listeners):**
`OfferService` (275 lines), `PreboardingService` (222), `AppraisalService` (198),
`JobRequisitionService` (195), `GoalService` (164), `JobApplicationService` (158),
`InterviewRoundService` (147), `EnrollmentService` (122), `EmployeeCompetencyService` (120),
`DevelopmentPlanService` (118), plus ~35 smaller services across Learning, Interview, Competency,
Performance, ATS-candidate-sub-services, Recruitment, and Probation-policy. These are all ancillary
HR-workflow modules (recruitment pipeline, performance appraisal, learning management), not
payroll/compliance-critical — deliberately deprioritized this sprint in favor of the
higher-risk payroll gap (see §6 rationale). Recommended as the next sprint's test-coverage backlog.

**Realistic quality-gate recommendation:** the current `jacoco.line.coverage.min` gate in `pom.xml`
is `0.35`. With overall coverage now at 50.91%, **raising the gate to `0.45`** is achievable without
further work and would lock in this sprint's gains as a floor. Do not raise it to overall-average
(50.91%) or higher yet — that would leave no margin and risk blocking unrelated future changes
whose packages happen to sit in the untested ancillary-HR-module long tail documented above.
Recommend re-raising incrementally (in ~10-point steps) as each ancillary module gets its
dedicated test sprint.

## 6. Payroll Certification Results

All 17 named scenarios (Monthly Payroll, New Joiner, Exit, Full & Final, Arrears, Supplementary
Payroll, Salary Revision, Loans, Reimbursements, Leave Encashment, Bank File, GL Posting, PF, ESI,
PT, TDS, Gratuity) were certified against the automated test suite — full matrix, expected-vs-actual
detail, and two honestly-flagged minor gaps (ESI/TDS share PF/PT's certified code path but lack a
test named for that specific code) are in `docs/reviews/SPRINT_16_PAYROLL_CERTIFICATION.md`.

Fresh confirming run: `mvn test -Dtest="com.ewos.payroll.**,com.ewos.exit.**,com.ewos.onboarding.**"`
→ **373 tests, 0 failures, 0 errors.**

Five previously-zero-coverage payroll services backing GL Posting, Bank File, reporting, salary
revision, and scheduled reporting were given full test coverage this sprint (57 new tests,
commit `f52fc73`): `PayrollJournalService`, `BankAdviceService`, `PayrollReportsService`,
`EmployeePayrollProfileService`, `ScheduledReportService`.

## 7. Production Readiness Assessment

An independent review covered build, backend config, DB migrations, Docker, Kubernetes, logging,
monitoring/health checks, secrets management, and backup/DR documentation. Result: **8 of 9 areas
GOOD, 0 unsafe fixes required, 1 documented gap.**

- **Build** — GOOD. All plugin versions pinned; no unpinned SNAPSHOT dependencies.
- **Backend config** — GOOD. Prod profile fail-fast guards (`JwtSecretGuard`, `AdminPasswordGuard`,
  CORS wildcard rejection) verified intact from prior sprint work.
- **DB migrations** — GOOD. V1–V41 sequential, additive-only DDL, FK-adjacent indexes present.
- **Docker** — GOOD. Multi-stage build, non-root UID, `tini` init, `HEALTHCHECK` on
  `/actuator/health/liveness`.
- **Kubernetes** — GOOD. Resource requests/limits, liveness/readiness probes, `replicas: 2`,
  secrets via `secretRef`, HPA 2→6 on CPU/memory.
- **Logging** — GOOD. Profile-aware (readable dev, JSON prod), no sensitive fields logged.
- **Monitoring/health** — GOOD. Actuator restricted to `health,info` in prod; `/actuator/metrics`
  requires auth. One judgment-call item (not a defect): Swagger UI has no prod-disable override —
  documented for the platform team to decide, not fixed unilaterally.
- **Secrets management** — GOOD. No real secrets found anywhere in the tree; only
  env-var-backed placeholders and explicit `CHANGE-ME` templates.
- **Backup & DR documentation** — **GAP (pre-existing, already self-acknowledged in
  `docs/reviews/PROJECT_HEALTH_REPORT.md`)**. No backup/restore runbook or DR plan exists under
  `docs/operations/`. This requires the platform/ops team to author (backup schedule, RPO/RTO
  targets, restore-drill procedure) — not something to fabricate in this review.

No files were modified during this review; every config-only fix that could have been made safely
was already in place from prior sprints.

## 8. Documentation Status

- New: `docs/reviews/SPRINT_16_PAYROLL_CERTIFICATION.md` (payroll certification matrix).
- New: this report.
- Reviewed and confirmed current: `docs/operations/deployment.md`, `docker.md`,
  `flyway-migrations.md`, `database-indexing.md`, `performance-benchmarks.md`, `sonarqube.md`,
  `auditor-and-actuator.md`.
- Confirmed gap (not fabricated): no backup/DR runbook exists yet (see §7).

## 9. Remaining Risks

1. **Backup/DR runbook missing** — highest-priority documentation gap; platform-team action
   required before an independent audit can consider DR posture complete.
2. **Ancillary HR-module test coverage** — ~35 application-layer services (Learning, Interview,
   Competency, Performance, Recruitment/ATS sub-services) remain at 0% unit coverage. Lower risk
   than payroll (non-financial, non-compliance), but a gap nonetheless — recommended as next
   sprint's backlog.
3. **ESI/TDS statutory extraction** — functionally certified via the same code path as PF/PT, but
   lacking a test named specifically for those two codes. Low risk, easy to close (see
   `SPRINT_16_PAYROLL_CERTIFICATION.md`).
4. **`requireActor()` duplication** — an identical (or near-identical, two message-text variants)
   actor-resolution helper is duplicated across 11 payroll/attendance/notification/workflow/
   recruitment/leave services, and a related `currentActor()` pattern appears in ~29 files total.
   Several modules already consolidate this into a dedicated `XxxSecurity` helper class
   (`CompetencySecurity`, `ExitSecurity`, `GoalSecurity`, etc.) — a deliberate per-module pattern.
   Consolidating the remaining raw duplicates into a single cross-module utility was considered
   this sprint but **deliberately not done**: it would touch auth-adjacent code across 11+ files
   with two different error-message strings, which risks a real (if minor) behavior change and
   exceeds this sprint's "safe refactoring only" mandate. Documented here as a backlog item for a
   dedicated future pass with its own regression-test plan.
5. **JaCoCo coverage-gate policy** — the enforced minimum (0.35) is well below the historical 0.80
   target noted in `pom.xml`'s own inline comment. See §5 recommendation to raise to 0.45 now and
   step up incrementally.

## 10. GitHub Verification

- **Repository:** `buntychavan-web/EWOS`
- **Branch:** `ewos-main`
- **Latest commit:** `cf9ea50c43d22ffd83e6d1b7fd3196d3886a535d` ("Sprint 16: Add payroll
  certification evidence report")
- **Local vs. remote:** confirmed identical (`git rev-parse HEAD origin/ewos-main` after a forced
  fetch of the remote-tracking ref both resolve to `cf9ea50`)
- **Working tree:** clean — `git status --porcelain` returns no output (no uncommitted, untracked,
  or unpushed files)
- **Commits this sprint:**
  1. `b7d4077` — Attendance service-layer tests
  2. `a0df3ff` — Onboarding service-layer tests
  3. `f52fc73` — Payroll-adjacent zero-coverage service tests
  4. `cf9ea50` — Payroll certification evidence report

## 11. CI Verification

All four commits above were individually confirmed green in GitHub Actions before the next was
started (the sprint's discipline: never stack a new commit on top of an unverified one):

| Commit | CI Run | Conclusion |
|---|---|---|
| `b7d4077` | 30373117610 | success |
| `a0df3ff` | 30373738560 | success |
| `f52fc73` | 30374764130 | success |
| `cf9ea50` | 30375337037 | success |

The CI pipeline (Spotless → Checkstyle → PMD → SpotBugs → `mvn verify` including the JaCoCo
coverage gate and the full Testcontainers-backed integration suite) is green end-to-end on the
current tip.

## 12. Recommendation

**Ready for Pilot Deployment: YES**, with one condition. **Ready for Independent Audit: YES**, with
the same condition and one disclosure.

- **Condition:** author the backup/restore and disaster-recovery runbook (§7, §9.1) before treating
  the platform as fully audit-complete on operational resilience. Everything else reviewed this
  sprint — build, config, migrations, Docker, Kubernetes, logging, monitoring, secrets — is
  production-correct today.
- **Disclosure for the auditor:** unit-test coverage sits at 50.91% overall, concentrated in
  payroll, compliance, tenancy, security, and now Attendance/Onboarding; ~35 ancillary HR-module
  services (recruitment pipeline, performance, learning) remain untested at the unit level. This is
  an honest, documented gap, not a defect — none of it touches money movement or statutory
  compliance, which are the areas this sprint and Sprint 15 concentrated on and certified.
- All code changes this sprint were additive test files and documentation; **zero business logic
  was modified**, satisfying the sprint's explicit "no new functionality" constraint.
