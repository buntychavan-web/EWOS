# EWOS backend — release notes

## 2026-07-28 — Sprint 15: Enterprise Quality & Reliability Sprint

Quality-only sprint: no new features. Strengthened automated testing across
payroll, statutory compliance, employee lifecycle, and organization
modules; reviewed and confirmed security test coverage; added a permanent
regression suite for two of the P9 audit's findings; found and fixed one
new bug while writing tests. See `PROJECT_STATUS.md` §13 for the full
writeup and the new `TESTING.md` for how the suite is organized.

### Added
- 16 new test files / 149 new test methods (986 total backend tests, up
  from 837), covering: `EmployeeCompensationService`, `PayComponentService`,
  `PayrollArrearService`, `FinalSettlementService`, supplementary-run and
  finalize/freeze transitions on `PayrollRunService`,
  `PayrollJournalGenerator`, `PayrollValidator`/`PayrollValidationService`,
  `EmployeeCostAllocationService`, `StatutoryDeductionService`,
  `StatutoryChallanService`, `StatutorySettingService`, `ProbationService`,
  `LeaveBalanceService`, `GlConfigService`.
- `SoftDeleteRegressionTest` — exercises Role/Permission soft-delete against
  real Postgres (User already had equivalent coverage).
- `ConstructorAmbiguityRegressionTest` — permanent reflection-based CI check
  scanning all `@Component`/`@Service`/`@Controller` classes for the
  ambiguous-constructor bug class found in the P9 audit.
- `TESTING.md` — new guide to test organization, conventions, and how to
  run the suite with/without Docker.

### Fixed
- `StatutoryDeductionService.extractForRun`'s in-memory idempotency check
  only saw deductions already persisted from *prior* calls, not ones
  inserted earlier in the *same* call — two differently-coded components
  resolving to the same statutory code on one payslip (e.g. `PF` and
  `PROVIDENT_FUND`) would double-insert and rely on the database's unique
  constraint to catch it at runtime instead of skipping gracefully. Fixed
  by updating the in-memory set as each row is inserted.

### Reviewed, no changes needed
- Security test coverage (JWT, auth rate limiting, account lockout,
  refresh-token rotation/revocation, `ClientAccessGuard` tenant isolation,
  CORS) was already comprehensive from earlier sprints.
- Code quality: no dead code, commented-out code, debug prints, or
  TODO/FIXME comments found. One duplicate-logic finding (18 services share
  an identical `currentActor()` helper) documented as a low-risk future
  cleanup rather than executed this sprint — see `PROJECT_STATUS.md` §13.

### Known gaps carried forward
- Attendance and Onboarding application-service layers remain untested at
  the orchestration level (their domain policy layers already had
  coverage) — recommended as the next sprint's target.
- Bonus, Leave Encashment, and Loans are not implemented as distinct
  domain concepts in this codebase; no tests were written for features
  that don't exist.
- JaCoCo coverage floor left at `0.35` — real coverage rose with this
  sprint's additions, but the gate itself wasn't moved pending a precise
  measurement next sprint (see `PROJECT_STATUS.md` §8.7/§11).

---

## 2026-07-27 — CTO Production Readiness Audit

A cross-cutting remediation pass across CI, security, and deployment
readiness. See `PROJECT_STATUS.md` §0 for the fuller writeup; this is the
condensed version.

### Fixed
- CI never actually ran on `ewos-main` — the branch every sprint since
  Sprint 1.1 developed on — because the workflow's branch trigger only
  listed `main`. `.github/workflows/ci.yml` now triggers on both.
- Reformatted 139 files that had drifted out of Spotless compliance
  (cosmetic; verified against the full unit suite before and after —
  no functional change).
- `main` fast-forwarded to `ewos-main`'s tip (0 divergent commits,
  verified via `git merge-base --is-ancestor` before merging) so the
  default branch reflects the same code as the active development branch.

### Added
- `AdminPasswordGuard` — refuses to boot outside `dev`/`test` if
  `ADMIN_PASSWORD` is still the shipped placeholder, mirroring the existing
  `JwtSecretGuard` for `JWT_SECRET`.
- `k8s/` (plain manifests) and `helm/ewos/` (parametrized chart) for
  Kubernetes deployment; `.env.example`; `docs/operations/deployment.md`.

### Changed
- `server.error.include-message` / `include-binding-errors` now default to
  `never` in the base `application.yml` (secure-by-default), with `dev`/
  `test` profiles explicitly opting back into verbose messages for local
  debugging. `prod` already had this right; the change closes the gap for
  any profile-less or new-profile deployment.

### Fixed (P9 validation pass)
- Six previously-invisible bugs were blocking every CI run on `ewos-main`
  at the `test` phase, one after another: three PMD false positives, a
  non-proxyable `final @Configuration` class (`CorsConfig`), two ambiguous
  Spring-constructor bugs (`CandidateNumberGenerator`, `LeaveRequestService`),
  a dead derived-query method (`ExitInterviewRepository`), and — the
  significant one — a Hibernate `@SQLDelete`/`@Version` bug that meant
  soft-delete had **never actually worked** for `User`, `Role`, or
  `Permission` (every delete threw a JDBC parameter-count error, surfaced to
  callers as a generic 409). All 780 tests now pass against real Postgres in
  CI for the first time. See `PROJECT_STATUS.md` §0 for full detail on each.

### Test coverage roadmap (new)
- Clearing those six bugs let `mvn verify` reach the JaCoCo `jacoco-check`
  goal for the first time ever, which revealed real aggregate coverage is
  ~33% — not the 80% `pom.xml` has required (unenforced) since Sprint 5. 206
  of 332 non-excluded classes had zero test coverage.
- Rather than discount the gate to match, `ExitServiceTest` and
  `SuccessionServiceTest` were added (the two largest zero-coverage classes)
  to genuinely clear a new `0.35` floor. A staged roadmap now ties the
  threshold to release milestones instead of one large backfill:
  **35% now → 50% before Beta → 65% before RC → 80% before GA**. See
  `PROJECT_STATUS.md` §4/§11 for the full ranked list of remaining
  zero-coverage classes to work through for the 50% milestone.

### Known limitations
- GitHub's repository default-branch *setting* could not be changed via
  any tool available in this environment (no repo-settings API access) —
  documented as a manual step for a repo admin in `PROJECT_STATUS.md` §11.
- The frontend's production API routing (`/api/v1/*` → this backend) is not
  automatically wired up by either repo — see `enterprise-core`'s
  `docs/DEPLOYMENT.md`.
- Test coverage is ~35-37% aggregate, well below the eventual 80% GA target
  — see the staged roadmap above and `PROJECT_STATUS.md` §11.
