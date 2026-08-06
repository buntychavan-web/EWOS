# Sprint 26 — Exit Management V1 — Release Notes

**Date:** 2026-08-06
**Repository:** `buntychavan-web/EWOS`
**Branch:** `claude/sprint-26-exit-management-v1` → `main`
**Pull Request:** [#38](https://github.com/buntychavan-web/EWOS/pull/38)

---

## Features delivered

Sprint 26 builds Exit Management V1 on top of a pre-existing `com.ewos.exit` module (resignation
lifecycle, clearance, knowledge transfer, exit interview, exit documents, alumni), closing the gaps
against the sprint's 10-item scope across 7 increments, followed by a Sprint 26A remediation pass
against an independent audit.

1. **Resignation types + employee self-service** — `ResignationType` (self-resignation, HR-initiated,
   manager-initiated, retirement, termination, death, absconding) and a dedicated self-service
   submission path (`/api/v1/exit/self-service/*`) alongside the existing HR-facing endpoint.
2. **Notice period management** — recovery, waiver, garden leave, extension, and early release
   (buyout already existed pre-sprint).
3. **Multi-level approvals** — a resignation can optionally attach an instance of the platform's
   existing generic workflow engine; acceptance is gated on that instance completing. Fully
   optional — a tenant without a configured approval workflow definition falls back to the
   pre-existing direct-approval path.
4. **Configurable exit checklist** — company/business-unit/department-scoped clearance checklist
   templates (most-specific-wins resolution, mirroring Payroll's statutory-configuration pattern);
   auto-populates clearance items when a resignation is accepted.
5. **Full & Final settlement linkage** — a nullable link from Payroll's existing
   `FinalSettlementService`/`final_settlements` back to the triggering resignation. No settlement
   math was duplicated; F&F continues to run entirely through Payroll's existing engine.
6. **Configurable exit document generation** — real PDF generation (Acceptance Letter, Relieving
   Letter, Experience Letter, Service Certificate, F&F Statement) from company-configurable
   templates with `{{token}}` substitution, rendered on demand via PDFBox (same approach as
   Payroll's payslip PDFs — nothing persisted as a blob).
7. **Knowledge transfer refinement** — successor assignment on the resignation (distinct from the
   per-item `transferredTo` routing) and KT item classification (task / document handover / client
   handover).

**Explicitly out of scope, documented rather than built:** checklist/document-template scoping by
Grade, Designation, or Employee Category — none of that master data exists anywhere in EWOS today,
and inventing it here would preempt whatever sprint properly introduces it (see "Known
limitations" below).

---

## Database migrations (V66–V71)

All additive and backward compatible — new tables and new nullable/defaulted columns only, no
destructive changes, no edits to any previously merged migration.

| Migration | Purpose |
|---|---|
| `V66__exit_resignation_type.sql` | Adds `resignation_type` to `resignations` (`NOT NULL DEFAULT 'SELF_RESIGNATION'`) |
| `V67__exit_notice_period_actions.sql` | Adds notice-recovery, waiver, garden-leave, extension, and early-release columns to `resignations` |
| `V68__exit_checklist_templates.sql` | New `exit_checklist_templates` / `exit_checklist_template_items` tables; adds nullable `item_name` to `exit_clearances` |
| `V69__final_settlement_resignation_link.sql` | Adds nullable `resignation_id` (FK) to `final_settlements` |
| `V70__exit_document_templates.sql` | New `exit_document_templates` table; widens the `exit_documents.document_type` check constraint to add `ACCEPTANCE_LETTER` and `SERVICE_CERTIFICATE` |
| `V71__exit_kt_successor.sql` | Adds nullable `successor_employee_id` (FK) to `resignations`; adds `item_type` (`NOT NULL DEFAULT 'TASK'`) to `knowledge_transfer_items` |

---

## APIs added

- `POST /api/v1/exit/self-service/resignations`, `GET .../resignations`, `POST .../resignations/{id}/withdraw`
- `POST /api/v1/exit/resignations/{id}/notice-recovery`, `.../notice-waiver`, `.../garden-leave`, `.../notice-extension`, `.../early-release`, `.../successor`
- `/api/v1/exit/checklist-templates` — create, get, list-for-company, activate, deactivate
- `/api/v1/exit/document-templates` — create, get, list-for-company, activate, deactivate
- `GET /api/v1/exit/resignations/{resignationId}/documents/{documentType}/pdf` — on-demand letter generation
- `GET /api/v1/payroll/settlements/by-resignation/{resignationId}` — read-only F&F lookup by resignation

---

## Security fixes

- **`ResignationController.submit()`** (Increment 1) previously took `tenantId` as a client-supplied
  request-body field with no `X-Tenant-Id` header, bypassing `TenantHeaderValidationFilter` — any
  caller holding `EXIT_WRITE` could plant a resignation under an arbitrary tenant. Fixed by removing
  `tenantId` from the request DTO and sourcing it from the header, matching the rest of the
  platform's controllers.
- **`AlumniController.create()`** (Sprint 26A P0-3) had the identical bypass — `tenantId` was a
  duplicate client-supplied body field, letting any caller holding `ALUMNI_MANAGE` plant an alumni
  record under an arbitrary tenant. Fixed the same way; see the Sprint 26A section below.

---

## Audit findings fixed in Sprint 26A

An independent audit of Sprint 26 raised 4 findings; all 4 were remediated in a single follow-up
commit before merge.

| ID | Finding | Fix |
|---|---|---|
| **P0-1** | `Resignation.employee` was a required (`optional = false`), no-not-found-handling association to a soft-deleted entity (`Employee` uses `@SQLRestriction("deleted_at IS NULL")`). Once an employee was soft-deleted, Hibernate threw `EntityNotFoundException` the instant anything touched `getEmployee()` on that resignation — mapper responses, dashboard counts, etc. — making the resignation itself unreadable, even though every caller in the module already null-checked `getEmployee()` defensively. | Added `@NotFound(action = NotFoundAction.IGNORE)` to the association. `employee_id` itself is untouched — still `NOT NULL`, never nulled, no migration needed. |
| **P0-2** | `ExitSecurity.currentActor()` silently returned `null` for *any* `IllegalArgumentException` while parsing the security principal name as a UUID, including a genuinely malformed/unexpected principal — not just the legitimate "no authenticated user" case — letting an audit-trail actor (`submittedBy`, `acceptedBy`, ...) silently disappear instead of surfacing the anomaly. | Genuinely-unauthenticated access (no `Authentication`, or a null principal name) still returns `null`, preserving the legitimate system-initiated-action case. An authenticated principal whose name isn't a parseable UUID now throws `IllegalStateException` instead. |
| **P0-3** | `AlumniController.create()` sourced `tenantId` from the request body instead of `X-Tenant-Id`, bypassing tenant-header validation (see "Security fixes" above). | `CreateAlumniRequest` no longer carries `tenantId`; the controller now takes `X-Tenant-Id` and passes it through, matching every other endpoint on the controller. |
| **P1-3** | `ExitDocumentPdfGenerationService` used PDFBox's Standard-14 Helvetica fonts, which use a single-byte WinAnsiEncoding and throw `IllegalArgumentException` for any character outside it — Devanagari and most other non-Latin-1 text included — making the service unusable for Indian names or other multilingual letter content. | Replaced with an embedded GNU FreeSans (Regular + Bold, bundled under `src/main/resources/fonts/`, GPL-3+ with the Font Embedding Exception) loaded via `PDType0Font`. Verified full Devanagari Unicode-block coverage before bundling. Scoped to the exit-document generator only — the one PDF path this sprint added; Payroll's pre-existing `PayslipPdfGenerationService` has the same underlying limitation but was out of scope for this remediation. |

---

## Test summary

- **605 new/updated test cases** across the 7 feature increments plus the audit-remediation commit,
  including a new `ExitSecurityTest`, a new `ResignationEmployeeSoftDeleteIntegrationTest`
  (Testcontainers/Postgres), and a Devanagari/Indian-name PDF-rendering regression test.
- **Local (non-Docker) suite:** 1,690 tests run, 0 failures, on every increment's commit and the
  final pre-merge state. All errors present locally were exclusively the sandbox's
  Testcontainers/Docker-unavailable limitation (pre-existing, spanning identity/tenancy/attendance/
  workflow/payroll modules untouched by this sprint) — not regressions.
- **CI (GitHub Actions, Docker available):** `mvn verify` — including every Docker-backed
  integration test and the JaCoCo coverage gate — passed on the PR head commit, and again on the
  post-merge commit on `main`. Spotless, Checkstyle, PMD, SpotBugs, and the gitleaks secret scan all
  passed clean.

---

## Known limitations

- **Grade / Designation / Employee Category scoping** for exit checklist and document templates is
  not implemented — no such master data exists anywhere in EWOS yet; scoping is currently limited to
  Company and Business Unit/Department (via `OrganizationUnit`).
- **`PayslipPdfGenerationService`** (Payroll module, predates this sprint) still uses the Standard-14
  Helvetica fonts and shares P1-3's underlying limitation — out of scope for this sprint's
  remediation.
- **KT successor/handover tracking** is intentionally minimal: no due dates or handover-completeness
  tracking beyond the existing per-item `completed` flag.
- **Exit interview analytics** and gating exit completion on F&F settlement status were considered
  and deliberately not built — not required by this sprint's scope.

---

## Commit SHA

`1c14bda5b3f121cef017380a75f8437cb0c2b6a6` — last commit on `claude/sprint-26-exit-management-v1`
(Sprint 26A audit remediation), the PR head commit that CI validated.

## Merge commit SHA

`091994cc19d3f61e9d40a99bede2d769d55fb397` — merge of PR #38 into `main`.
