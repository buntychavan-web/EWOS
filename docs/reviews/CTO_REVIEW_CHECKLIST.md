# CTO Review Checklist

> **Status:** Mandatory. Every sprint must pass this checklist before its PR is merged to `main`.
>
> **Owner:** Lead Backend Engineer prepares; CTO (or delegated reviewer) signs off in §11.
>
> **How to use.** Copy this template into a per-sprint file
> (`/docs/reviews/sprint-<N>-cto-review.md`) at the start of the review pass, fill in every
> checkbox with `[x]` (pass), `[!]` (waived — must be justified in the "Note" line below the box),
> or `[ ]` (open — blocks merge). Attach evidence links (file:line, commit SHA, CI run URL) next
> to each item. The **Sprint Results Log** at the bottom of this file is appended (never
> rewritten) after every completed sprint so history stays intact.
>
> **Waivers.** Any `[!]` requires a one-sentence justification and either (a) a Known Issues
> entry in §10 with an owner + target sprint, or (b) explicit written CTO sign-off in §11.

---

## 1. Architecture Review

Goal: the sprint fits inside the platform's established shape and doesn't rewrite the past.

- [ ] Follows package-by-feature layout `com.ewos.<context>.{api,application,domain,infrastructure}`.
- [ ] No cross-module leaks — application-layer code in module A does not import repositories / entities from module B without an ADR justifying the coupling.
- [ ] New entities extend `AuditableEntity` (or documented reason if not).
- [ ] Any new architectural decision has a matching ADR under `/docs/adr/`.
- [ ] No re-design of previously-approved decisions (Sprint 6 tenancy, Sprint 7 org-node history, Sprint 8.1 person identity, etc.) — deviations are called out explicitly and blessed here.
- [ ] Framework versions are pinned in `pom.xml`; no new snapshot / SNAPSHOT / RC dependencies introduced.
- [ ] Feature is disabled cleanly if its runtime prerequisites are missing (Redis, KMS, external API) — no NPE on boot.

**Evidence:** _link to package tree, ADR file, `pom.xml` diff._

---

## 2. Database Review

Goal: schema changes are additive, indexed, constrained, and reproducible.

- [ ] New Flyway migration file is the next unused version (`V<N>__<snake_name>.sql`) — no gaps, no renumbering, no edits to prior V files.
- [ ] Every new column that must be non-null has either a `NOT NULL` + `DEFAULT`, or a two-step migration plan documented.
- [ ] Every FK is explicit + has an `ON DELETE` clause (RESTRICT by default; CASCADE only when the child row makes no sense without its parent).
- [ ] Every hot-path query is backed by an index (verify with `EXPLAIN` on realistic data).
- [ ] Uniqueness that must survive soft delete uses **partial unique indexes** (`WHERE deleted_at IS NULL`), not table-level `UNIQUE`.
- [ ] Effective-dated tables have `CHECK (effective_to IS NULL OR effective_to >= effective_from)`.
- [ ] Effective-dated tables that require a single open row per key use `CREATE UNIQUE INDEX … WHERE effective_to IS NULL`.
- [ ] Seed data (permissions, roles, default tenants) uses `ON CONFLICT DO NOTHING` so re-runs are safe.
- [ ] No PII column is added in plaintext without a plan for encryption or a signed waiver in §10.
- [ ] `spring.jpa.hibernate.ddl-auto` is untouched (must remain `none` — Flyway owns the schema).

**Evidence:** _link to migration file, `EXPLAIN` snapshots for new hot paths._

---

## 3. API Review

Goal: every new endpoint is discoverable, documented, versioned, validated, and consistent.

- [ ] Path is versioned: `/api/v1/<resource>/…` unless the endpoint is deliberately outside the versioned surface (must be justified).
- [ ] Verb + status code align with REST: 201 on create, 200 on read/update, 204 on delete, 409 on conflict, 404 on missing, 400 on validation, 401 on missing auth, 403 on missing authority.
- [ ] Every controller method carries `@Operation(summary=…)` and `@ApiResponses` covering all non-200 status codes it can produce.
- [ ] Every response body is a `record` DTO in `<context>/api/dto` — no entity types cross the API boundary.
- [ ] Every request DTO carries `jakarta.validation` constraints AND is annotated `@Valid` at the controller.
- [ ] Error responses use the platform `ApiError` envelope (do not invent a new one).
- [ ] Correlation ID is preserved on every response header (`X-Request-ID`).
- [ ] List endpoints support Spring Data `Pageable` with a sensible `@PageableDefault(size = 20, sort = …)`; no unbounded lists returned.
- [ ] No breaking change to an existing endpoint's request shape, response shape, or error contract (if intended, needs an ADR + client migration plan).
- [ ] Swagger UI loads cleanly (`/swagger-ui.html`) and every new endpoint appears with a schema, not `Object`.

**Evidence:** _link to controller class, Swagger screenshot, DTO records._

---

## 4. Security Review

Goal: every endpoint enforces authentication + authorisation; no secrets or PII leak.

- [ ] Every mutating endpoint carries `@PreAuthorize("hasAuthority('…')")` — no bare `isAuthenticated()` on writes.
- [ ] Every read endpoint carries the appropriate `_READ` authority (or `isAuthenticated()` only for genuinely public data).
- [ ] New permissions are seeded via Flyway migration AND added to `com.ewos.identity.domain.Permissions`.
- [ ] Permission granularity separates dangerous operations (e.g. `PERSON_DUPLICATE_OVERRIDE` distinct from `PERSON_WRITE`).
- [ ] No new endpoint is added to a `permitAll()` matcher in `SecurityConfig` without CTO sign-off in §11.
- [ ] Tenant-scoped entities carry `tenant_id` even if enforcement is deferred; the deferral is called out in §10.
- [ ] PII fields (PAN, Aadhaar, passport, mobile, email, bank account) either are encrypted OR appear in §10 with an explicit encryption debt entry.
- [ ] JWT secrets, DB credentials, third-party keys are read from environment variables — never checked in.
- [ ] Rate-limiting / brute-force story for any new authentication path is documented.
- [ ] `application.yml` / `application-*.yml` diffs do not add a default credential, secret, or API key.

**Evidence:** _controller `@PreAuthorize` grep, `Permissions.java` diff, migration seed._

---

## 5. Performance Review

Goal: no N+1s, no unbounded scans, hot paths are indexed, tests cover realistic sizes.

- [ ] No JPA `@OneToMany` / `@ManyToMany` triggers N+1s in any served endpoint (use `@EntityGraph` or fetch join; verify with SQL log on integration test).
- [ ] Every DB query used by the sprint has been eyeballed with `EXPLAIN` on production-shaped data OR is trivially index-driven.
- [ ] No endpoint reads full tables into memory (`findAll()` without a Spec + `Pageable`) except where the row count is bounded by design (< 1000). Any in-memory scan is called out in §10 with a scale-threshold.
- [ ] Aggregate / summary endpoints use a **single native query** where possible instead of module-by-module counts.
- [ ] New services doing "walk up the tree" / "resolve from parent" work in O(depth) or O(1) — not O(all rows).
- [ ] Transaction boundaries are `@Transactional(readOnly = true)` for pure reads.
- [ ] No blocking I/O inside a `synchronized` block or long-running transaction.
- [ ] Caching is either not needed or documented (which cache, invalidation strategy, TTL).

**Evidence:** _`EXPLAIN` outputs, Hibernate SQL log snippet, JMH results if applicable._

---

## 6. Business Rule Review

Goal: every rule the product owner cares about is (a) explicitly documented and (b) enforced in the service layer (not just the UI).

- [ ] Every business rule the sprint introduces appears in `/docs/business-rules/<module>.md` with its enforcement point (service class, DB constraint, partial index).
- [ ] Rules are enforced in the **service layer**, not only in the controller / DTO validation. (DTO validation is the shallow gate; service is the source of truth.)
- [ ] Rules that must be configurable (weights, thresholds, feature flags, per-tenant policies) live in a DB table, not in Java constants.
- [ ] Effective-dated data uses the master + versions pattern — never overwrite historical rows.
- [ ] Data-loss operations (merge / split / deactivate) always write an audit / version row before mutating.
- [ ] Any rule that has an "override" path (e.g. `PERSON_DUPLICATE_OVERRIDE`) requires a distinct authority AND caller acknowledgement (explicit flag).
- [ ] No rule is silently changed from a prior sprint; changes need a diff entry in the business-rules doc + a note in §10.

**Evidence:** _link to `/docs/business-rules/<module>.md`, service class + line._

---

## 7. UI / UX Review

Goal: the API contract works for the UI; the UI works for real users.

_Note: this repository is currently backend-only. Mark items `[!]` with "Backend-only sprint — no UI surface" if that applies. When a UI is delivered, complete every item._

- [ ] API contract has been reviewed by the frontend team (link to sign-off).
- [ ] Every endpoint the UI needs is present with a stable shape; no "we'll add it later" gaps in the primary flow.
- [ ] Error responses are actionable — `ApiError.message` is a sentence a UI can show a user (not a stack trace).
- [ ] Long-running operations expose progress (either paged results or an async job pattern).
- [ ] i18n keys, if any, follow the platform convention.
- [ ] Accessibility (WCAG 2.1 AA) — colour contrast, keyboard navigation, screen-reader labels — verified.
- [ ] Empty states, loading states, and error states are designed for every new screen.
- [ ] Destructive actions (delete / merge / override duplicates) require confirmation.
- [ ] Mobile / responsive layout works at 375 px, 768 px, 1280 px, 1920 px.

**Evidence:** _Figma link, screenshot of Storybook, FE sign-off comment._

---

## 8. Testing Review

Goal: the test suite gives real confidence, not vanity coverage.

- [ ] Unit tests exist for every new service, targeting at least the golden path + one error path per public method.
- [ ] At least one integration test exercises every new controller end-to-end (login → call → assert on real DB).
- [ ] Tests are deterministic — no `sleep`, no wall-clock assertions, no order-dependent test methods.
- [ ] Testcontainers usage follows the singleton pattern in `AbstractIntegrationTest` (never `.stop()`; Ryuk cleans up at JVM exit).
- [ ] JaCoCo BUNDLE coverage ≥ 80 % on CI (green build linked below).
- [ ] Every quality gate is green on the review branch: Spotless, Checkstyle, PMD, SpotBugs, compile, tests, JaCoCo check.
- [ ] Concurrency / race conditions are either covered by a test OR called out as a known limitation in §10.
- [ ] Migration is exercised on an empty DB via the integration test (Testcontainers spins Postgres from scratch each CI run).

**Evidence:** _CI run URL, JaCoCo report link, test file count._

---

## 9. Documentation Review

Goal: someone joining the team in six months can understand the sprint from documentation alone.

- [ ] `PROJECT_STATUS.md` — "Delivered by sprint" section has a new subsection for this sprint.
- [ ] `PROJECT_STATUS.md` — "Remaining technical debt" section has entries for every deferral introduced by this sprint (or the change log calls out that there are none).
- [ ] `PROJECT_STATUS.md` — change log entry at the bottom describes what changed and why.
- [ ] ADR — if the sprint made an architectural decision, `/docs/adr/<NNNN>-<slug>.md` exists and follows the ADR template.
- [ ] Business rules — if the sprint introduced business rules, `/docs/business-rules/<module>.md` exists (or is updated) with every rule + enforcement point.
- [ ] README — updated if the sprint changes how to run the app (new env var, new dependency, new dev flow).
- [ ] Swagger — every new endpoint has `@Operation(summary=…)` and `@ApiResponses` (double-counted here so we don't ship silently-undocumented APIs).
- [ ] CTO Review Package — completed at `/docs/reviews/sprint-<N>-cto-review.md` (this file's per-sprint counterpart).

**Evidence:** _links to each of the files above._

---

## 10. Known Issues / Deferrals

Goal: nothing important is left implicit. Every deferred item is captured with an owner and a target sprint.

Format for each entry:

```
- [<severity>] <one-sentence description>
  - **Why deferred:** <one sentence>
  - **Owner:** <name>
  - **Target sprint:** <sprint>
  - **Tracked in:** <PROJECT_STATUS.md line / ADR link / issue link>
```

Severity: `HIGH` (would block production for a large customer), `MED` (would surprise a customer), `LOW` (housekeeping).

**Sprint entries:**

- [ ] All new tech debt appears here AND in `PROJECT_STATUS.md § "Remaining technical debt"`.
- [ ] All race conditions identified in §5 appear here with a mitigation plan.
- [ ] All PII / encryption gaps identified in §4 appear here with an owner.
- [ ] All in-memory-scan queries identified in §5 appear here with a scale threshold.
- [ ] All architecture deviations from §1 appear here OR are blessed in §11.

_(Copy the format above and fill in one bullet per issue.)_

---

## 11. CTO Approval

Goal: a single, dated, human sign-off that this sprint may merge.

**Reviewer:** _<name, role>_
**Date:** _<YYYY-MM-DD>_
**Commit reviewed:** _<SHA>_
**CI run:** _<URL>_

**Decision:** `[ ] APPROVED` / `[ ] APPROVED WITH CONDITIONS` / `[ ] REJECTED`

**Conditions / follow-ups:** _(numbered list; each becomes a §10 entry after merge if not resolved before)_

**Waivers granted:** _(list every `[!]` in the checklist that this approval blesses; each with a one-line justification)_

**Signature:** _<name>_

---

## Sprint Results Log

_Appended (never rewritten) after every completed sprint. One entry per sprint. Newest at the top._

### Sprint 8.1 — Person Engine + Dashboard Summary
- **Date:** 2026-07-14
- **Package:** [`docs/reviews/sprint-8.1-cto-review.md`](./sprint-8.1-cto-review.md)
- **Head:** `aa400b4` (review package) on top of `a53af0a` (Dashboard) on top of `7bbefa0` (Person Engine)
- **Migrations added:** V8, V9
- **New endpoints:** 32 (31 Person + 1 Dashboard)
- **New permissions:** `PERSON_READ`, `PERSON_WRITE`, `PERSON_DELETE`, `PERSON_DUPLICATE_OVERRIDE`, `DASHBOARD_READ`
- **Unit tests:** 79/79 green
- **Integration tests:** 2 written; require Docker (CI green, sandbox N/A)
- **Deviations flagged:** 4 (Dashboard path, in-memory search, child-entity soft-delete-not-versioning, PII plaintext)
- **CTO decision:** _pending_

### Sprint 7 — Organization Structure Engine
- **Date:** 2026-07-13
- **Head:** `7466a60`
- **Migrations added:** V7
- **New endpoints:** 23 on `OrganizationController`
- **New permissions:** `ORGANIZATION_READ/WRITE/DELETE`
- **ADR:** [`docs/adr/0002-organization-structure-engine.md`](../adr/0002-organization-structure-engine.md)
- **CTO decision:** _pending_

### Sprint 6 — Company Configuration
- **Date:** 2026-07-13
- **Head:** `cb100b9`
- **Migrations added:** V6
- **New endpoints:** 22 on `CompanyController`
- **New permissions:** `COMPANY_READ/WRITE/DELETE`
- **ADR:** [`docs/adr/0001-company-effective-dating-and-tenancy.md`](../adr/0001-company-effective-dating-and-tenancy.md)
- **CTO decision:** _pending_

### Sprints 1 – 5 (pre-checklist)
- Foundation, identity, user management, hardening. Not retroactively audited against this checklist; recorded in `PROJECT_STATUS.md` § 1.
