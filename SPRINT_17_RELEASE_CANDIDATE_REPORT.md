# Sprint 17 — Release Candidate (RC) Sprint — Completion Report

**Date:** 2026-07-28
**Scope:** Backup/DR runbook, production-blocker remediation, and RC verification only — no new
HRMS business functionality, no new business modules, per the sprint's explicit constraint.
**Repos:** `buntychavan-web/EWOS` (backend, branch `ewos-main`) and
`buntychavan-web/enterprise-core` (frontend, branch `main`).

---

## 1. GitHub Verification

| Repo | Branch | Working tree | Local vs. remote |
|---|---|---|---|
| `buntychavan-web/EWOS` | `ewos-main` | Clean (`git status --porcelain` empty) | Identical (`a4711b8` both) |
| `buntychavan-web/enterprise-core` | `main` | Clean (`git status --porcelain` empty) | Identical (`b6748c8` both) |

No uncommitted, untracked, or unpushed changes in either repository.

## 2. Latest Commit SHA(s)

- **Backend (`EWOS`):** `a4711b8006f5c98a64edd87cce67ff227db80d26` — "Sprint 17 RC1: Author and
  validate the Backup & Disaster Recovery Runbook"
- **Frontend (`enterprise-core`):** `b6748c8d620298a545d90cb273936c4fab2e72d0` — "Add
  package-lock.json and patch safe transitive dependency vulnerabilities"

## 3. CI Status

| Repo | Commit | Run | Conclusion |
|---|---|---|---|
| EWOS | `a4711b8` | 30377726525 | **success** |
| EWOS | (prior, unchanged this sprint's earlier check) `a1e4b2b` | 30375744039 | success |
| enterprise-core | `b6748c8` | 30377147884 | **success** |

Both pipelines green on the current tip.

## 4. Production Readiness Assessment

Builds on the Sprint 16 ops/config review (already GOOD across build, backend config, DB
migrations, Docker, Kubernetes, logging, monitoring, secrets) with this sprint's additions:

- **Backend build:** `mvn clean verify` — **BUILD SUCCESS**. Checkstyle, PMD, and SpotBugs
  (0 bugs, 0 errors) all clean; JaCoCo coverage gate passes; 1,115 unit tests, 0 failures (the 32
  Testcontainers-based integration-test errors are the standard no-Docker-registry-access artifact
  of this sandbox, not a code defect — the same suite runs green in GitHub Actions CI, which has
  registry access).
- **Frontend build:** `npm run build`, `npm run typecheck`, `npm run lint` (0 errors, pre-existing
  style-only warnings), and all 16 unit tests — all pass.
- **Flyway migrations:** all 41 migrations confirmed sequential with no version gaps, and verified
  to apply cleanly end-to-end this sprint as part of the DR drill (see §6) — a live re-confirmation,
  not just a static check.
- **Docker:** multi-stage `Dockerfile` unchanged and still correct (non-root user, `HEALTHCHECK`,
  no baked-in secrets) — re-confirmed from Sprint 16, no regressions.
- **Kubernetes / Helm:** all seven `k8s/*.yaml` manifests parse as valid YAML; all six
  `helm/ewos/templates/*.yaml` files have balanced Go-template delimiters (a full `helm template`
  render was not possible — the Helm CLI is not installed in this sandbox and could not be added
  without a heavier, riskier environment change; the templates were independently reviewed and
  passed in Sprint 16's production-readiness agent, and this sprint's brace-balance check found no
  regression since).
- **Logging / monitoring:** `logback-spring.xml` and the prod actuator-exposure configuration
  (`health,info` only) confirmed still in place, unchanged since Sprint 16.
- **Backup & DR:** now fully documented and validated — see §6. This closes the one open gap
  carried from Sprint 16.
- **Code-level production-blocker review** (new this sprint, distinct from Sprint 16's ops/config
  review): an independent pass over global error handling, exception-swallowing, resource
  management, transactional boundaries, dependency-vulnerability tooling, N+1/unbounded-query
  patterns, and static-analysis suppressions. **Verdict: no CRITICAL or HIGH severity blocker
  found.** Two MEDIUM items were noted for the backlog (not fixed, per the sprint's
  critical/high-only mandate): an N+1 query pattern in `PayrollRunService.processPayslips` when
  computing per-employee arrears/leave during a payroll run, and the absence of an OWASP
  Dependency-Check (or equivalent SCA) plugin in the Maven build.
- **Frontend dependency hygiene:** no `package-lock.json` existed prior to this sprint (a
  reproducible-build risk); one was generated and committed. `npm audit fix` (non-force) reduced
  15 known vulnerabilities to 12; all 12 remaining are in dev-only tooling (vite/vitest/esbuild
  dev-server CVEs — not present in the production browser bundle) and require a breaking
  major-version bump, deliberately deferred rather than risking a regression under this sprint's
  safe-only mandate.

## 5. Remaining Risks

1. **Production-scale RTO unmeasured.** The DR drill (§6) proves the restore *procedure* is
   correct; it does not measure recovery time at a realistic production data volume. See the
   runbook's own §7 for detail.
2. **PITR/continuous WAL archiving not independently exercised.** Standard, well-documented per
   managed-Postgres provider, but not validated in this sandbox (depends on provider choice).
3. **No automated backup-verification job yet** (a scheduled restore-and-smoke-test). This
   sprint's drill was performed manually.
4. **Two MEDIUM code-level findings**, deliberately not fixed this sprint per the critical/high-only
   mandate: the `PayrollRunService` N+1 pattern (§4) and missing SCA/dependency-scanning tooling.
5. **Frontend: 12 residual dev-tooling CVEs** requiring a breaking major-version bump to fully
   clear (vite/vitest) — deferred, not shipped-code risk (§4).
6. **Ancillary HR-module unit-test coverage** (carried from Sprint 16, unchanged this sprint):
   ~35 application-layer services in Learning, Interview, Competency, Performance, and
   Recruitment/ATS sub-services remain at 0% unit coverage. Non-payroll, non-compliance, lower
   risk, but still an honest gap.
7. **Helm chart not rendered end-to-end** in this sandbox (Helm CLI unavailable) — structurally
   validated (balanced templating, matches Sprint 16's independent review) but not executed through
   `helm template`/`helm install --dry-run`.

**No CRITICAL issue was found or remains open.**

## 6. Backup & DR Runbook — Validated

`docs/operations/backup-disaster-recovery.md` (new this sprint) documents the backup strategy,
RPO/RTO targets, and disaster-recovery plan, and embeds the actual evidence from a real drill run
this sprint against a genuine local PostgreSQL 16.13 instance (not a mock):

1. The real EWOS application was booted against it, applying all 41 Flyway migrations and the
   identity bootstrap.
2. A full `pg_dump` logical backup was taken (488 KB, 129 tables).
3. **The database was completely dropped** — simulating total data loss.
4. `pg_restore` recovered it — **exit code 0**.
5. Verification: every row count matched the pre-disaster control exactly, the admin user's UUID
   survived unchanged (proving identity/PK preservation, not just counts), all 129 tables and every
   foreign-key constraint (including the workflow engine's five interlocking tables) were
   recreated.

Full transcript, commands, and honest caveats (toy-scale timing, not a production RTO measurement)
are in the runbook itself.

## 7. Recommendation for Independent Audit

**YES — ready**, with the risks in §5 disclosed as open items rather than defects. Both the
Sprint 16 Audit Readiness Report and this report should be handed to the auditor together: Sprint
16 certified payroll calculations and ops/config; Sprint 17 adds a validated DR/backup runbook and
a code-level (not just config-level) production-blocker sweep that found nothing CRITICAL or HIGH.
All work this sprint is either a new test file, a documentation artifact, or a dependency-hygiene
fix (lockfile + non-breaking patches) — **zero business logic was modified**, satisfying the
sprint's "no new functionality" constraint end to end.

## 8. Recommendation for Pilot Deployment

**YES — ready for pilot deployment.** Every objective in the Sprint 17 brief is met except one,
disclosed here rather than silently worked around: **the `v1.0.0-rc1` git tag could not be pushed
to `origin`** — `git push origin refs/tags/v1.0.0-rc1` was rejected with `HTTP 403` (confirmed on
retry, not transient), and no GitHub API tool available in this session can create a tag or release
as an alternative path. The tag was created **locally** in this session's clone
(`a4711b8` → `v1.0.0-rc1`) but does not yet exist on `github.com/buntychavan-web/EWOS`. Every other
completion criterion is met: all changes are committed and pushed, CI is green on both repos, the
Backup & DR Runbook is complete and validated, and the repository is otherwise ready for an
independent audit. **This tag push needs either an elevated-permission retry or the repository
owner pushing `git tag v1.0.0-rc1 a4711b8 && git push origin v1.0.0-rc1` directly** — it is a
one-line action once permission is available, not a blocked engineering task.
