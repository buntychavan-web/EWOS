# ADR-0001: Company effective-dating and multi-tenant model

- **Status:** Accepted
- **Date:** 2026-07-13
- **Sprint:** Sprint 6 — Company Configuration
- **Authors:** Backend team

## Context

Sprint 6 introduces a first-class **Company** in the platform, plus its statutory registrations, bank accounts, policy assignments, and shared-service (team) assignments. Every prior sprint (Users, Roles, Permissions, refresh tokens, login history) modelled the platform's *own* identity — Sprint 6 is the first time we model an HRMS-operational entity, and it forces two decisions we can't defer without paying for them repeatedly:

1. **How do we store change over time?** Company profile, statutory numbers, policy assignments, and team assignments are all things that change with real-world timing (e.g. "PAN valid from 2024-04-01", "leave policy X applies from 2026-04-01"). Payroll, statutory filings, and audit obligations require us to answer "what did the company look like on date D?" for any historical D.
2. **How do we structure tenancy?** Even a single-customer install can have multiple companies (subsidiaries, joint ventures). The sprint brief explicitly names *Shared Enterprise* and *Segregated Enterprise* as first-class business modes with a *configurable* data-isolation policy.

Doing these wrong here is expensive later: every future domain module (payroll, leave, attendance) inherits both concerns.

## Decision

### 1. Effective-dated versioning via a separate "version" table

Company profile lives in **`company_versions`**, not on the `companies` master row. Each version has an `effective_from` and a nullable `effective_to`; a null `effective_to` marks the currently-open version.

- Rows are **immutable except for `effective_to`.** A "profile edit" **never** overwrites the current row; instead, the service closes the current row's window and inserts a new row.
- At most **one open row per company** is enforced at the DB layer by `CREATE UNIQUE INDEX … WHERE effective_to IS NULL`.
- Historical reads use `WHERE effective_from ≤ :date AND (effective_to IS NULL OR effective_to ≥ :date)`, exposed as `GET /api/v1/companies/{id}?asOf=YYYY-MM-DD`.

The same pattern (effective_from/to columns + application-layer window enforcement) is applied to `statutory_registrations`, `company_bank_accounts`, `company_policy_assignments`, and `company_shared_services`, so every dated fact in Sprint 6 has the same shape.

### 2. Overlap prevention in the service layer, not via `EXCLUDE USING gist`

PostgreSQL's range-exclusion constraints (`EXCLUDE USING gist (company_id WITH =, daterange(...) WITH &&)`) are the "correct" tool, but require the `btree_gist` extension. We chose to enforce non-overlap in the service layer (`PolicyAssignmentService#assertNoOverlap`, `CompanyService#updateProfile`) plus a targeted unique index (`ux_company_versions_open`) for the single-open-window invariant, because:

- Portability: keeps the Flyway migrations working against any Postgres install without an extension prerequisite.
- Testability: the check is unit-testable without a live database.

Trade-off: race conditions between concurrent inserts can slip past the service-layer check. Under real load we should add the `EXCLUDE USING gist` constraint (feature-flagged behind an extension guard) and let the DB be the final arbiter. Tracked in the tech-debt list.

### 3. Tenant is a real entity; isolation policy is stored, not yet enforced

A `tenants` table owns companies and carries `isolation_policy ∈ {SHARED, SEGREGATED}`. Every `companies` row FKs to a tenant. A default tenant (`code = 'DEFAULT'`) is seeded by the V6 migration so any pre-existing rows (there are none in this codebase, but that's the future-proof choice) still have a valid owner.

**Enforcement of SHARED vs SEGREGATED is deliberately deferred to Sprint 6.1.** The Sprint-6 code stores the policy and exposes it via APIs, but query-time enforcement (a Hibernate `@Filter` driven by the tenant id in the JWT) is not wired. The alternative — building the enforcement now, without a tenant claim in the JWT and without any need for cross-tenant queries yet — would land untested and would tie down the JWT shape before we know it. Storing the field now means the schema doesn't need to change when Sprint 6.1 wires enforcement.

### 4. Policies referenced by opaque UUID, not enforced FK

Company policy assignments carry `policy_ref UUID NOT NULL` plus a denormalised `policy_label`, but **no FK constraint**. The actual policy tables (holiday calendars, leave policies, shift patterns, workflows) belong to their own bounded contexts in later sprints. Referring to them by UUID:

- Lets Sprint 6 ship without waiting for those contexts.
- Keeps the company module's bounded context clean — it doesn't own policy content.
- Requires the future policy service to validate `policy_ref` at read time.

The same reasoning applies to `team_ref` in `company_shared_services`.

### 5. Clone semantics

`POST /api/v1/companies` accepts an optional `cloneFromId` and an optional `clonePolicyTypes` set:

- `cloneFromId` null → **blank company**: master + initial version, nothing else.
- `cloneFromId` present, `clonePolicyTypes` empty/null → **structure only**: creates the master, but does not copy policy assignments, statutory registrations, or bank accounts. Statutory numbers and account numbers should never be cloned; they must be re-entered per company. This is a rule we bake into the code, not a configuration.
- `cloneFromId` present, `clonePolicyTypes` non-empty → **reference existing policies**: same as structure-only, plus a new assignment row for each requested policy type carrying the *same* `policy_ref` as the source's currently-active assignment, with `effective_from` set to the new profile's `effective_from`.

## Consequences

**Good**
- Historical audit is a first-class read path: `GET /companies/{id}?asOf=…` returns the profile that was live on that date.
- Every dated fact in Sprint 6 (profile, statutory reg, bank account, policy assignment, shared service) uses the same pattern, so future sprints (payroll cycles, salary revisions, leave balances) can copy it verbatim.
- The tenant column is present everywhere it needs to be, so Sprint 6.1 only has to add enforcement code — no migration.

**Neutral**
- Two-table pattern (master + versions) doubles the join cost for basic reads. The current index `company_versions(company_id, effective_from DESC)` keeps the common lookup (open version) cheap.

**Bad / debts explicitly accepted**
- Race-time overlap is only best-effort. Concurrent inserts of two policy assignments for the same `(company, policy_type)` could both pass the service check and both commit. Mitigation: add `EXCLUDE USING gist` in a follow-up migration once we accept the `btree_gist` extension.
- `SHARED` isolation policy has zero enforcement today. If a caller ends up with a JWT for tenant A and passes `tenantId=B` in a query param, `CompanySpecifications` will filter accordingly, but nothing prevents the caller from doing so. Sprint 6.1 must resolve this before any second tenant exists in a real environment.
- `policy_ref` / `team_ref` are unvalidated at write time. Callers can insert garbage UUIDs. Acceptable for now because the consuming code doesn't exist yet — but Sprint 6.1+ must validate them once the tables are there.

## Alternatives considered

- **Store profile columns directly on `companies` with an audit-trail table.** Rejected: audit tables are for *change history*, not for *"the state at date D"*. Queries against the audit table for "what was the timezone on 2025-03-01" become expensive and error-prone.
- **`EXCLUDE USING gist` for temporal uniqueness at the DB layer.** Rejected for now — see § 2. Kept as a follow-up.
- **Row-level security (RLS) for tenant isolation.** Rejected: adds a mandatory Postgres session-variable protocol for every connection, complicates Testcontainers use, and is harder to unit-test. Hibernate filters are the standard middle ground and enough for the SHARED/SEGREGATED distinction.
- **Full multi-tenant DB-per-tenant.** Out of scope; the sprint calls for isolation policy per tenant *inside* one database.

## Follow-ups (Sprint 6.1)

1. Add tenant id claim to the JWT; wire a `TenantContext` populated by `JwtAuthenticationFilter`.
2. Add a Hibernate `@Filter` on every tenant-scoped entity, activated by `TenantContext` for SEGREGATED tenants.
3. Add `EXCLUDE USING gist` constraints for temporal non-overlap (guarded by the `btree_gist` extension check).
4. Once policy modules exist, replace opaque `policy_ref` / `team_ref` with FK-enforced references OR keep them opaque but add read-time validation in the consumer.
