# Business Rules — Person Engine (Sprint 8.1)

This document captures the business rules encoded in the Person module.
It is intentionally exhaustive so QA, product, and downstream sprints
have one place to check "how is X supposed to behave?".

## 1. Identity

| # | Rule | Enforcement |
|---|---|---|
| 1.1 | A Person is the permanent identity of a human. It is **not** an employee. | Package boundary: `com.ewos.person` owns identity only. Employment (Sprint 8.2) will live in a separate package. |
| 1.2 | Every Person has an immutable **Group Person ID** matching `P` + 9 digits (`P000000001`). | `PersonIdGenerator` + Postgres sequence `person_id_sequence`. |
| 1.3 | Group Person IDs are **never reused**, even after soft-delete. | Sequence is monotonic; the value is fixed at creation and never rewritten. Rollbacks consume the number. |
| 1.4 | A Person belongs to exactly one tenant. | `persons.tenant_id NOT NULL`, FK to `tenants`. |
| 1.5 | A Person may be soft-deleted. Soft-deletion hides them from all reads. | `@SQLDelete` + `@SQLRestriction("deleted_at IS NULL")`. |
| 1.6 | A Person can be deactivated without deletion. Deactivation preserves history and prevents future Employment. | `persons.active` flag; API `PATCH /api/v1/persons/{id}/status`. |

## 2. Effective-dated profile

| # | Rule | Enforcement |
|---|---|---|
| 2.1 | Core profile fields (names, gender, DOB, marital status, blood group, nationality, photo) live in `person_versions`. | Schema. |
| 2.2 | A profile change **never** overwrites an existing row. It closes the previous row (`effective_to = newFrom - 1`) and appends a new row. | `PersonService.updateProfile`. |
| 2.3 | At any moment there is at most one **open** version per Person (row with `effective_to IS NULL`). | `ux_person_versions_open` partial unique index. |
| 2.4 | `effective_from` of a new version must be **strictly after** the current open version's `effective_from`. | Service check throws `400 BAD_REQUEST`. |
| 2.5 | Version numbers are monotonically increasing per Person (`1, 2, 3, …`). | `PersonVersion.versionNumber` bumped in service; `ux_person_versions_number` unique index. |
| 2.6 | Every version carries `change_reason` + auditor's user id (`created_by`) + optional `approved_by`. | Auditor listener + explicit request field. |
| 2.7 | Historical reads use `GET /api/v1/persons/{id}?asOf=YYYY-MM-DD` to return the version live on that date. | `PersonVersionRepository.findEffectiveAt`. |

## 3. Contact channels

| # | Rule | Enforcement |
|---|---|---|
| 3.1 | Contact channels (personal / alternate mobile + email) are effective-dated on `person_contacts`. | Schema. |
| 3.2 | Setting new contact channels closes the current open row and inserts a new one. | `PersonContactService.setContact`. |
| 3.3 | New contact `effective_from` cannot backdate the current open row's `effective_from`. | Service check. |

## 4. Addresses

| # | Rule | Enforcement |
|---|---|---|
| 4.1 | Address kind is one of PERMANENT / CURRENT / COMMUNICATION. Multiple addresses per Person are allowed. | `AddressKind` enum + DB check. |
| 4.2 | Addresses are effective-dated; the sprint does not enforce single-open-window per (person, kind) — a Person can hold overlapping communication addresses if they choose. | Documented as a product choice. |
| 4.3 | Retire = set `effective_to`. | `PersonAddressService.retire`. |

## 5. Emergency contacts

| # | Rule | Enforcement |
|---|---|---|
| 5.1 | Multiple emergency contacts allowed; priority orders them (`1` = highest). | `PersonEmergencyContact.priority`. |
| 5.2 | Each emergency contact carries name, relationship (free text), mobile (required), optional alternate mobile / email / address. | DTO validators + schema. |
| 5.3 | Soft-delete removes them from lists. | `@SQLDelete` + `@SQLRestriction`. |

## 6. Family

| # | Rule | Enforcement |
|---|---|---|
| 6.1 | Family relations are enumerated: FATHER, MOTHER, SPOUSE, SON, DAUGHTER, SIBLING, GUARDIAN, OTHER. | `FamilyRelation` enum. |
| 6.2 | A family member may be flagged `dependent` — this drives tax + benefits in later sprints. | Column present today, consumers TBD. |
| 6.3 | Soft-delete removes them from lists. | `@SQLDelete` + `@SQLRestriction`. |

## 7. Education

| # | Rule | Enforcement |
|---|---|---|
| 7.1 | Each education record has qualification, institution, passing year (1900–2200), optional grade / specialization / document URL. | DTO + DB check constraint. |
| 7.2 | Soft-delete removes them from lists. | `@SQLDelete` + `@SQLRestriction`. |

## 8. Identity documents

| # | Rule | Enforcement |
|---|---|---|
| 8.1 | Kinds enumerated: PAN, AADHAAR, PASSPORT, DRIVING_LICENCE, VOTER_ID, OTHER. | `IdentityDocumentKind` enum + DB check. |
| 8.2 | **PAN is unique across all live rows in the database.** | `ux_person_identity_documents_pan` partial unique index. |
| 8.3 | **Aadhaar is unique across all live rows in the database.** | `ux_person_identity_documents_aadhaar` partial unique index. |
| 8.4 | Passport / DL / Voter ID uniqueness is not enforced globally (documents can be re-issued with the same number in some regimes). | Documented. |
| 8.5 | Documents are effective-dated (validity window + optional expiry). | Schema. |
| 8.6 | A document may carry a `verified` flag once a KYC step confirms it. | `PersonIdentityDocument.verified`. |

## 9. Duplicate detection

| # | Rule | Enforcement |
|---|---|---|
| 9.1 | Duplicate detection runs **before** every Person creation. | `PersonService.create` calls `DuplicateDetectionService.check` first. |
| 9.2 | Rules are per-tenant, data-driven. Each rule row has `enabled` + `weight`. | `person_duplicate_rules` table + `DuplicateRuleService`. |
| 9.3 | Rule kinds supported: PAN, AADHAAR, PASSPORT, MOBILE, EMAIL, NAME_DOB. | `DuplicateRuleKind` enum. |
| 9.4 | If any enabled rule matches, creation is refused with `409 CONFLICT` and the match list is returned. | `assertOverrideAllowedIfDuplicates`. |
| 9.5 | To override, the caller must pass `overrideDuplicates=true` AND hold `PERSON_DUPLICATE_OVERRIDE`. Missing either yields `409` or `403` respectively. | Same helper. |
| 9.6 | Multiple rules matching the same Person collapse to the highest-weight match. | `DuplicateDetectionService.check` de-dupes by `personId`. |
| 9.7 | Rules are seeded for the DEFAULT tenant on V8 migration; new tenants must be seeded by product. | Migration `V8__person_engine.sql`. |

## 10. Profile readiness

| # | Rule | Enforcement |
|---|---|---|
| 10.1 | Readiness is a **signal, not a gate.** A partial profile is always creatable. | Explicit design. |
| 10.2 | Readiness is computed per section (basic / contact / address / emergency / education / family / documents). | `ProfileReadinessService.compute`. |
| 10.3 | Basic section counts 7 fields (first, last, gender, DOB, marital status, blood group, nationality). Each contributes 100/7 %. | Service logic. |
| 10.4 | Contact section counts 2 fields (personal mobile + personal email). | Service logic. |
| 10.5 | Address / emergency / education / family / documents are binary: 0 if no rows, 100 if any row. | Service logic. |
| 10.6 | Overall = average of the seven section percentages. | Service logic. |

## 11. Search

| # | Rule | Enforcement |
|---|---|---|
| 11.1 | `GET /api/v1/persons/search?q=…` matches Person ID exactly, name substring (against open version), mobile exact, email exact, PAN / Aadhaar / Passport / any document number exact. | `PersonSearchService.search`. |
| 11.2 | Paged listing at `GET /api/v1/persons` supports filters on tenantId, groupPersonId, active + Spring Data `Pageable`. | `PersonService.search` + `PersonSpecifications`. |

## 12. Security

| # | Rule | Enforcement |
|---|---|---|
| 12.1 | Every write on `/api/v1/persons/**` requires `PERSON_WRITE`. | Method-level `@PreAuthorize`. |
| 12.2 | Every read requires `PERSON_READ`. | Method-level `@PreAuthorize`. |
| 12.3 | Soft-delete requires `PERSON_DELETE`. | Method-level `@PreAuthorize`. |
| 12.4 | Overriding duplicate warnings requires `PERSON_DUPLICATE_OVERRIDE`. | Checked in `PersonService.create`. |
| 12.5 | `SYSTEM_ADMIN` is granted all four permissions by V8 migration. | Migration seed. |

## 13. Audit

| # | Rule | Enforcement |
|---|---|---|
| 13.1 | Every entity extends `AuditableEntity` — `created_at` / `updated_at` / `created_by` / `updated_by` fill automatically. | `AuditingEntityListener` + `AuditorProvider`. |
| 13.2 | Every soft-deleted row is retained forever (no purge job). | No delete job yet — see PROJECT_STATUS.md tech debt. |
| 13.3 | Every profile version records `change_reason` supplied by the caller. | Version row. |
