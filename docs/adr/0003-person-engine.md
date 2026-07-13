# ADR-0003: Person Engine — permanent identity, sequence-backed group ID, configurable duplicate detection

- **Status:** Accepted
- **Date:** 2026-07-13
- **Sprint:** Sprint 8.1 — Person Engine
- **Authors:** Backend team

## Context

Sprint 8.1 introduces **Person** — the platform's permanent human identity. A Person is not an
Employee; Sprint 8.2 will introduce Employment records that reference a Person, so a single Person
may hold zero, one, or many Employment relationships over their working life (joins, exits, rehires,
concurrent contracts). Getting this shape right now is critical: everything downstream
(payroll, statutory filings, background checks, longitudinal analytics) reads through Person.

Four decisions had to be nailed down that all downstream sprints inherit.

## Decision

### 1. Group Person ID is a monotonic, never-reused sequence

The user-facing identifier is `P` + 9-digit zero-padded serial (`P000000001`). It is generated
by the Postgres sequence `person_id_sequence`. Rollbacks consume the number rather than release
it — a released number could later be handed to a different Person, violating the sprint brief's
"never reuse" rule.

- **Alternative rejected:** UUID-only. UUIDs are already the primary key; the Group Person ID is
  the *human* identifier stamped on offer letters, statutory forms, and payslips. It has to be
  short, greppable, and stable — UUIDs are none of those.
- **Alternative rejected:** app-side `AtomicLong`. Cannot survive multi-node deployment or
  restart-with-in-flight-inserts safely.

### 2. Person master + effective-dated versions is a hard boundary

Person master (`persons`) carries **only** identity: `group_person_id`, `tenant_id`, `active`,
soft-delete + `@Version`. Every mutable profile field — first/middle/last name, gender, DOB,
marital status, blood group, nationality, photo — lives on `person_versions`, keyed by
`(person_id, version_number)` with an `effective_from` / `effective_to` window.

- A profile edit **appends** a new version and closes the previous window at `newFrom - 1`.
  Historical rows are immutable except for `effective_to`.
- `ux_person_versions_open` (partial unique on `person_id WHERE effective_to IS NULL`) guarantees
  at most one open version per Person.
- Every version row carries `change_reason` + `changed_by` (via auditor) + optional `approved_by`
  — the "why / who / who approved" required by statutory audit.

This is the same pattern established by Sprint 6 (Company) and Sprint 7 (Organization). Employment
in Sprint 8.2 will inherit it verbatim.

### 3. Duplicate detection is a data-driven engine, not hardcoded rules

`person_duplicate_rules` holds a per-tenant row for each rule kind (PAN / Aadhaar / Passport /
Mobile / Email / Name+DOB). Every row carries `enabled` and `weight` — the code loops the enabled
rules in weight order and returns the union of match candidates, ordered by highest matching
weight.

- The **create** flow always runs the engine first. If any rule matches and the caller did not
  pass `overrideDuplicates=true`, we return `409 CONFLICT` with the match list — creation is
  refused. The user sees the candidates and picks one of: use an existing Person, edit inputs,
  or override.
- Override requires **both** `overrideDuplicates=true` on the request **and** the
  `PERSON_DUPLICATE_OVERRIDE` authority. Without both, `403 FORBIDDEN`.
- Rules are surfaced via `GET /api/v1/persons/duplicate-rules` and mutable via
  `PUT /api/v1/persons/duplicate-rules/{id}` — tenants tune their own risk posture.

**Never silently create duplicates** — the sprint brief's non-negotiable, wired into
`PersonService.create` and enforced from the DB (partial unique indexes on PAN + Aadhaar across
live rows).

- **Alternative rejected:** hardcoded rule set. Would ship correct-for-India-only; the moment a
  tenant needs a different jurisdiction or weight profile the schema changes.

### 4. Missing information is never a create blocker; readiness is a signal

`ProfileReadinessService` computes a per-section percentage (basic, contact, address, emergency,
education, family, documents) plus an overall `overallPct`. It never blocks; the API returns the
readiness object so the UI can nudge users to complete their profile.

The sprint brief was explicit — profile completion is a signal, not a gate. A Person can exist
with only First/Last name + `effective_from`; the rest can be filled in later. This is critical
for Employment (Sprint 8.2), where an incomplete Person may already need to appear on a payroll
run under a background-check exception.

## Consequences

**Good**
- The Person → Employment split is honored from day one: employment cannot leak into identity, and
  the DB shape prevents anyone from adding job-title / department / salary to the Person entity.
- Group Person IDs are safe to embed in external documents — they cannot collide, cannot be
  reused, cannot be regenerated.
- Duplicate detection is a first-class product feature, tuneable per tenant without code changes.
- Historical audit works the same way it does in Sprint 6 & 7 — a single mental model.

**Neutral**
- Two-row read pattern for "Person with current profile": one query for the master, one for the
  open version. The `idx_person_versions_person` index makes both cheap.
- Sequence-consumed-on-rollback means the ID space is not perfectly dense. Acceptable — density
  isn't a requirement, "never reuse" is.

**Bad / debts explicitly accepted**
- Race-time overlap on child effective-dated rows (contacts, addresses, documents) is
  best-effort in the service layer; same trade-off as Sprint 6 & 7 (`EXCLUDE USING gist`
  follow-up).
- `PersonSearchService` currently loads open versions in-memory for name/ID contains searches.
  Fine at HR scale; when we start caring about latency the trigram indexes are already in the
  right place (`idx_person_versions_name`) and this can move to a proper `LIKE` query.
- Duplicate detection's `NAME_DOB` rule scans all open versions in memory. Same follow-up as
  above — index-driven query when the tenant crosses ~50k persons.

## Alternatives considered

- **Merge Person + Employee.** Rejected on principle — the sprint brief said this explicitly, and
  the "one human, many employments" invariant is what makes rehire / concurrent contracts / joint
  ventures work cleanly.
- **UUID-only public identifier.** Rejected — see § 1.
- **Silent duplicate merge on match.** Rejected — a false positive would irreversibly conflate
  two humans. The caller must make the call, and the DB will enforce PAN/Aadhaar uniqueness
  regardless.
- **Blocking incomplete profile creation.** Rejected — see § 4.

## Follow-ups

1. Sprint 8.2 introduces Employment. Every Employment record will FK to `persons.id`.
2. When PII encryption is turned on (a later sprint), PAN / Aadhaar / passport columns need
   field-level encryption + a searchable hash sibling column. The current schema keeps them in
   plaintext for Sprint 8.1 — call this out in the security-review pass.
3. `PersonSearchService` should move to indexed `ILIKE` / trigram queries once the tenant crosses
   ~50k persons. Ditto `DuplicateDetectionService.matchByNameAndDob`.
4. The audit trail on child entities (address, family, emergency, education, document) is
   soft-delete + `@Version` today. If regulators demand full history for those too, promote them
   to the effective-dated version pattern used for the core profile.
