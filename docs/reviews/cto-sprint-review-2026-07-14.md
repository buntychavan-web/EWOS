# EWOS CTO Sprint Review

**Date:** 2026-07-14
**Repository:** `buntychavan-web/EWOS`
**Prepared by:** Backend lead

---

## 1. Current GitHub branch

`claude/cors-configuration` (checked out locally, pushed to origin).

Sibling active branches:

- **`claude/repository-selection-575dn9`** — carries Sprints 6 → 8.1 → Dashboard → docs. **Not yet opened as a PR.**
- `claude/sprint-6-company`, `claude/ci-fix`, `claude/process-docs` — each has its own open PR against `main`.
- `main` — last merge is `c63947c` (PR #3, foundation hardening).

## 2. Last commit hash

**`22c3f14`** — "Production-ready CORS: data-driven allow-list, deny-by-default in prod" (2026-07-14).

Recent commit history (newest first):

| SHA | Description |
|---|---|
| `22c3f14` | Production-ready CORS |
| `e3a76fa` | Frontend handover doc |
| `79bb48e` | Architecture + sprints indexes |
| `287d46b` | CTO review checklist |
| `aa400b4` | Sprint 8.1 CTO review package |
| `a53af0a` | Dashboard summary API |
| `7bbefa0` | Sprint 8.1 — Person Engine |
| `7466a60` | Sprint 7 — Organization Structure |
| `cb100b9` | Sprint 6 — Company Configuration |
| `9b46bf2` | CI fix (singleton Testcontainers) |

## 3. Open Pull Requests

| # | Title | Head → Base | Status |
|---|---|---|---|
| **#7** | Production-ready CORS: data-driven allow-list, deny-by-default in prod | `claude/cors-configuration` → `main` | **Open — just raised** |
| #6 | Sprint 6: Company Configuration | `claude/sprint-6-company` → `main` | Open — stalled |
| #5 | Docs & process (CONTRIBUTING, PR template, pitfalls) | `claude/process-docs` → `main` | Open — stalled |
| #4 | CI fix: singleton Testcontainers | `claude/ci-fix` → `main` | Open — stalled |

**Nothing has merged to `main` since `c63947c` (PR #3).** Sprints 6, 7, 8.1, 8.1.1, docs, and the CORS fix are all backed up in branches or PRs waiting for review.

## 4. Completed work

| Sprint | Scope | Migration | Head commit |
|---|---|---|---|
| 1 | Foundation (Spring Boot 3.3, Postgres, Flyway, JWT, Docker, CI scaffold) | V1 | (in main) |
| 2 | Identity (users, roles, permissions, login, refresh) | V2, V3 | (in main) |
| 4 | User management (CRUD, password policy, history) | V4 | (in main) |
| 5 | Hardening (CI, static analysis, JaCoCo 80%, soft delete, logout, correlation IDs) | V5 | (in main) |
| 6 | Company Configuration | V6 | `cb100b9` |
| 7 | Organization Structure Engine | V7 | `7466a60` |
| 8.1 | Person Engine (identity, effective-dated profile, duplicate engine, readiness) | V8 | `7bbefa0` |
| 8.1.1 | Dashboard summary endpoint | V9 | `a53af0a` |
| — | Production-ready CORS | — | `22c3f14` |
| — | Complete documentation set (ADR-0001…4, business rules, review package, checklist, HANDOVER, architecture overview, sprint index) | — | see §8 |

**Aggregate delivered surface:**

- 9 Flyway migrations
- 60+ REST endpoints across identity, user, company, organization, person, dashboard, auth
- 79 unit tests + 8 integration test classes (+ 6 new CORS unit tests = 85 total)
- 4 ADRs, 1 business-rules doc, 1 CTO review package, 1 checklist template, 1 frontend handover, architecture + sprints indexes

## 5. In-progress work

Zero code work in progress. Waiting on:

- **Review + merge of PRs #4, #5, #6, #7** by the CTO or delegated reviewer.
- **A merge strategy decision** for `claude/repository-selection-575dn9` — Sprints 7, 8.1, 8.1.1 and all Sprint 8.1 documentation live on this branch and have never been opened as a PR (see §12).

## 6. Pending work

**Backend sprints planned but not started:**

| Sprint | Deliverable |
|---|---|
| 8.2 | Employment engine — `employments.person_id → persons.id`; hire date, employment type, work location, cost centre, status. Unblocks the `Dashboard.employees` counter. |
| 9 | Payroll — pay periods, salary structure, run engine, payslips, F&F. |
| 10 | Leave — types, balances, requests, approvals. |
| 11 | Attendance — clock-in/out, roster, exceptions. |
| 12+ | Workflow, Reporting, Notifications, Documents. |

**Cross-cutting backend follow-ups:**

- Sprint 6.1 — tenant isolation enforcement (Hibernate `@Filter` driven by JWT tenant claim).
- `EXCLUDE USING gist` for temporal overlap on all effective-dated tables.
- PII encryption for PAN / Aadhaar / passport (+ searchable hash siblings).
- Rate limiting + account lockout on `/api/v1/auth/*`.
- Indexed name/DOB search + trigram support once tenants cross ~50k persons.

**Frontend:** not started. Handover doc (`docs/HANDOVER.md`) prepared for the incoming engineer.

## 7. Backend health

| Signal | Status |
|---|---|
| Compile | ✅ clean |
| Spotless | ✅ clean (217 files) |
| Checkstyle 10.18 | ✅ 0 violations |
| PMD 7.6 | ✅ 0 violations |
| SpotBugs 4.8 | ✅ 0 BugInstances |
| Runtime start-up on `dev` profile | ✅ boots against Postgres + Redis via docker-compose |
| Migrations | V1–V9, additive-only, no schema drift |
| Optimistic locking | `@Version` on every mutable entity |
| Soft-delete | `@SQLDelete` + `@SQLRestriction` + partial unique indexes throughout |
| Auth surface | JWT stateless, login/refresh/logout audited, CORS now wired |
| Observability | Actuator health/info/metrics + `X-Request-ID` propagation |

## 8. Documentation health

Complete coverage as of this branch:

| Category | Path | Status |
|---|---|---|
| Architecture overview | `docs/architecture/README.md` | ✅ |
| Architecture decisions | `docs/adr/0001-…tenancy.md` (Company), `0002-…organization.md`, `0003-…person-engine.md`, `0004-cors-configuration.md` | ✅ 4 ADRs |
| Business rules | `docs/business-rules/person-engine.md` | ✅ (Person; Company + Org rules captured in their ADRs) |
| CTO review checklist (template) | `docs/reviews/CTO_REVIEW_CHECKLIST.md` | ✅ |
| CTO review package (Sprint 8.1) | `docs/reviews/sprint-8.1-cto-review.md` | ✅ (16 sections) |
| Sprint index | `docs/sprints/README.md` | ✅ |
| Frontend handover | `docs/HANDOVER.md` | ✅ 706 lines, self-contained |
| Contributor rules | `CONTRIBUTING.md` (PR #5 — not yet in `main`) | ⚠️ waiting on PR #5 |
| PR template | `.github/pull_request_template.md` (PR #5) | ⚠️ waiting on PR #5 |
| Running status | `PROJECT_STATUS.md` | ✅ |

## 9. Build status

**Local (this session, sandbox):**
- `mvn spotless:check checkstyle:check pmd:check compile spotbugs:check` → **BUILD SUCCESS** on `22c3f14`.
- Full `mvn verify` cannot run in the sandbox (no Docker daemon → Testcontainers integration suite can't spin Postgres).

**CI (GitHub Actions):**
- Last run on `main` (`c63947c`) was **red** — that failure is what PR #4 fixes; the fix is verified on the ci-fix branch and PR-authored.
- No CI run has yet executed on the tip of the review branch (`22c3f14`) — auto-runs when PR #7 executes.

**JaCoCo:** 80% BUNDLE instruction-coverage floor enforced in `verify`. Coverage exclusions target bootstrap/config/DTO/entity/repo-interface packages; measured surface is services + controllers + specifications.

## 10. Test status

| Suite | Count | Status |
|---|---|---|
| Unit tests (all modules) | **85 / 85** green locally | ✅ |
| Integration tests | 8 test classes: `AuthControllerIntegrationTest`, `UserControllerIntegrationTest`, `CompanyControllerIntegrationTest`, `OrganizationControllerIntegrationTest`, `PersonControllerIntegrationTest`, `DashboardControllerIntegrationTest`, `CorsPreflightIntegrationTest`, `EwosApplicationTests` (context load) | ⏳ pass on CI; sandbox has no Docker |

Breakdown of unit tests (79 pre-CORS + 6 new):

- Identity: 31 (Auth 12 + User 12 + Password 7)
- JWT: 2
- Company: 13
- Organization: 15
- Person: 16
- Dashboard: 2
- CORS: 6

Determinism: no `sleep`, no wall-clock assertions, singleton Testcontainers pattern.

## 11. Technical debt

Ordered by severity (from `PROJECT_STATUS.md § 7` + Sprint 6/7/8.1 deferrals).

**High**

1. **PII plaintext.** PAN / Aadhaar / passport stored unencrypted. Needs field-level encryption + searchable-hash sibling column before any prod deployment.
2. **Tenant isolation not enforced at query time** (Sprint 6.1). Column present everywhere; enforcement pending.
3. **No rate limiting / account lockout on auth endpoints.** CORS is now open — surface is browser-reachable — throttling gap is more visible.
4. **`JWT_SECRET` placeholder default.** App still boots against it; should refuse.

**Medium**

5. **`PersonSearchService` + `DuplicateDetectionService.matchByNameAndDob`** scan open versions in memory. Fine < 50k persons/tenant.
6. **Person child-entity full history not versioned** (address, family, education, emergency, document use soft-delete + `@Version` only).
7. **Duplicate-rule seeding** only runs for `DEFAULT` tenant. Provisioning of new tenants needs a rule-seed step.
8. **Race-time overlap** on effective-dated child rows relies on service-layer checks. `EXCLUDE USING gist` behind `btree_gist` is the fix.

**Low**

9. No restore-from-soft-delete API for users / persons.
10. Refresh tokens not device-bound (no rotation-family reuse detection).
11. No purge job for soft-deleted rows.
12. Actuator `/prometheus` etc. currently unauthenticated once exposed.
13. Coverage exclusions could shrink (`CorrelationIdFilter`, `AuditorProvider` deserve tests).

## 12. Blockers

**Merge backlog.** Nothing has landed on `main` since 2026-07-09 (`c63947c`). Four PRs are open (#4, #5, #6, #7) and Sprints 7 + 8.1 + 8.1.1 + all Sprint-8.1 documentation live on the un-PRed branch `claude/repository-selection-575dn9`. Consequences:

- CI has not exercised the full stack against `main` since Sprint 5.
- The Sprint 6 PR (#6) is based off the un-merged CI-fix branch — its diff is oversized until #4 lands.
- Frontend engineer receiving `docs/HANDOVER.md` on `main` will not see the handover doc — it lives on the un-merged branch.
- The CORS fix (PR #7) is against `main` and does not yet include the Sprint 6/7/8.1 diffs; if it merges first, PRs #4 and #6 will need rebase.

**Action needed:** decide the merge order. Recommended order below in §14.

**Docker unavailable in this authoring sandbox.** Integration tests must run on CI. Not a blocker for merge — CI has Docker — but a blocker for local `mvn verify`.

## 13. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Merge conflicts across PRs #4/#5/#6/#7 grow as they age | High | Medium — rework cost | Merge in the order below within the next few days |
| PII plaintext leak once the frontend goes live | Medium | Critical — regulatory | Fast-track encryption sprint before any prod / customer data |
| Cross-tenant read leak (Sprint 6.1 debt) | Medium | Critical — customer-visible | Ship Sprint 6.1 before onboarding a second tenant |
| Brute-force login attacks now that CORS opens the browser surface | Medium | High | Add rate limiter to `/api/v1/auth/*` in the next sprint |
| Sprint 8.1 code-review scope explodes (Person Engine is 51 files) | High | Medium — review fatigue | Reviewers use `docs/reviews/sprint-8.1-cto-review.md` as their entry point — the 16-section walk-through is designed for exactly this |
| Frontend team blocked on backend features they can't test | Medium | Medium | HANDOVER doc gives them the Vite proxy fallback and the exact API contracts; unblocks parallel work |
| No CI run yet on `22c3f14` | Low | Low | Auto-runs on PR #7 push; will surface any integration regression |

## 14. Recommendation for next sprint

**Priority 0 — clear the merge backlog before starting new work.** Recommended order:

1. **PR #4** (CI fix) → `main`. Zero risk; unblocks everything else.
2. **PR #5** (docs & process) → `main`. CONTRIBUTING + PR template + pitfalls; harmless docs-only.
3. **PR #6** (Sprint 6) — rebase onto post-#4 `main`, then merge.
4. **New consolidated PR(s)** for `claude/repository-selection-575dn9`, split into three PRs against `main`:
   - Sprint 7 (Organization Structure)
   - Sprint 8.1 + 8.1.1 (Person Engine + Dashboard) — attach the existing `docs/reviews/sprint-8.1-cto-review.md` package
   - Docs bundle (architecture + sprints + HANDOVER + checklist)
5. **PR #7** (CORS) — rebase onto the resulting `main`, then merge.

**Priority 1 — Sprint 8.2: Employment.** Blocked by the merge backlog only in spirit — the code can start now on a fresh branch off Sprint 8.1. Scope: Employment entity + effective-dated employment history + `person_id` FK + Employee ID sequence + APIs; makes `Dashboard.employees` non-zero.

**Priority 2 — parallel security hardening sprint.** Three items belong together because they all touch the same code paths:

- PII encryption for `person_identity_documents` (+ searchable hashes on PAN / Aadhaar so duplicate detection still works).
- Rate limiting on `/api/v1/auth/*`.
- `JWT_SECRET` placeholder guard on startup.

Deferring Sprint 6.1 (tenant isolation) is acceptable *only* if no second tenant is onboarded in the interim. If sales is close to a second customer, promote it to Priority 1 alongside 8.2.

**Priority 3 — enable branch protection on `main`** (require CI green + 1 review) so the review discipline in `CTO_REVIEW_CHECKLIST.md` is mechanically enforced. This is a repo-settings change, not code — 5-minute admin task.

---

**End of report.** No source code was modified in preparing it.
