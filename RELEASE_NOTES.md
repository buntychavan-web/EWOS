# EWOS backend — release notes

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
