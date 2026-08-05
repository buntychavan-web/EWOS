# Company Configuration — Deferred Capabilities Backlog

## Status

**Backlog / future work.** Nothing in this document is implemented. No code changes accompany
this file.

## Background

An early branch, `claude/sprint-6-company` (PR #6, "Sprint 6: Company Configuration"), proposed a
dedicated `com.ewos.company` module: a full company-profile management surface with
effective-dated versioning, statutory registration tracking, company-level bank accounts, policy
assignments, and shared-service team assignments — all keyed off a flat `Tenant`/`Company` pair.

That module was never merged. The platform's actual multi-tenancy foundation instead grew as
`com.ewos.tenancy` (`Tenant → Client → Company`), which independently solves the core need PR #6
was addressing — giving the bare `company_id` UUID (already referenced by every tenant-scoped
table) a real backing row — via a different, and for this platform's purposes more complete,
design: it adds a `Client` layer between `Tenant` and `Company` that the current
payroll-service-provider multi-client model (`ClientAccessGuard`, `TenantAccessGrant`,
`PayrollServiceProvider`) depends on.

PR #6 was closed on 2026-08-05 as superseded by the current owner, following a production-readiness
audit (Sprint 25B) that confirmed the foundational architecture is covered. That same audit found
that several of PR #6's **specific** capabilities were never rebuilt under the new architecture —
not reimplemented differently, simply absent. This document is the record of what those are, so
the decision to skip them is visible and revisitable rather than silently lost with the branch.

## Verified current state (`main` @ commit `ed61369`)

| PR #6 concept | Package/table | Exists in `main` today? |
|---|---|---|
| `Tenant` / `Company` anchor rows | `com.ewos.company.domain.{Tenant,Company}` | Yes, differently — as `com.ewos.tenancy.domain.{Tenant,Company}`, plus a `Client` layer PR #6 never had |
| Effective-dated company **profile version history** | `com.ewos.company.domain.CompanyVersion`, `company_versions` table, `?asOf=` reads | **No** — `com.ewos.tenancy.domain.Company` has no version history of any kind |
| Company-level **statutory registrations** (PAN/TAN/GST/PF/ESIC/PT/LWF) | `com.ewos.company.domain.StatutoryRegistration`, `statutory_registrations` table | **No** — payroll's statutory *calculation* config (jurisdictions, PT slabs) is unrelated; nothing tracks a company's own registration numbers |
| Company-level **bank accounts** | `com.ewos.company.domain.CompanyBankAccount`, `company_bank_accounts` table | **No** — `main` only has `com.ewos.payroll.domain.EmployeeBankAccount` (per-employee, for payroll disbursement — a different concept) |
| **Policy assignments** | `com.ewos.company.domain.CompanyPolicyAssignment`, `company_policy_assignments` table | **No** |
| **Shared-service team assignments** | `com.ewos.company.domain.CompanySharedService`, `company_shared_services` table | **No** |

## Backlog items

### 1. Company profile version history

Track effective-dated snapshots of a company's profile (name, fiscal-year start, registered
address, etc.) so that a profile edit opens a new version row and closes the previous one's
window, rather than overwriting history — and so reads can ask "what did this company's profile
look like as of date X." PR #6's `CompanyVersion` entity and `ux_company_versions_open` partial
unique index (one open window per company) are a reference design, not a mandate — the current
architecture's `Company` entity and `AuditableEntity` conventions should be the starting point for
a fresh design, not a port of PR #6's schema.

### 2. Company statutory registrations (PAN, TAN, GST, PF, ESIC, PT, LWF)

Track each company's own statutory registration numbers — Permanent Account Number, Tax
Deduction Account Number, GST registration, and PF/ESIC/PT/LWF establishment codes — with
uniqueness constraints where legally required (e.g., PAN uniqueness across live rows) and a
retire/deactivate lifecycle for a registration that lapses or is replaced. This is distinct from
the existing statutory *calculation* configuration (jurisdictions, slabs, rates) that payroll
already has — this backlog item is about recording the company's own registered identifiers, not
computing deductions.

### 3. Company bank accounts

Track bank accounts that belong to the company itself — for salary funding, full-and-final
settlement funding, reimbursement disbursement, statutory remittance, and vendor payments — as
distinct from `EmployeeBankAccount` (an individual employee's own account for receiving pay).
Needs a purpose/kind enumeration (SALARY / FF / REIMBURSEMENT / STATUTORY / VENDOR / OTHER, per
PR #6's original design, open to revision) and an active/inactive lifecycle per account.

### 4. Policy assignments

A way to associate a company with policy configuration (e.g., which leave policy, which
attendance policy, which approval-workflow definitions apply to it) with an effective-dated
window and non-overlap enforcement per `(company, policy_type)`. PR #6 modeled this as an opaque
reference (a bare UUID + type tag) because no policy tables existed yet at the time; today's
architecture already has concrete policy entities in several modules (`AttendancePolicy`,
`LeaveType`, `WorkflowDefinition`, `PayrollApprovalPolicy`) that a real implementation should
reference directly rather than reintroducing an opaque pointer.

### 5. Shared-service team assignments

A way to record which internal team (HR / Payroll / Finance / IT, per PR #6's original
enumeration) is responsible for servicing a given company — relevant for the
payroll-service-provider model where one operating team supports multiple client companies.
Should be designed against the current `com.ewos.tenancy` `Client`/`ClientAssignment` model
(`ClientAccessGuard`, `ClientAssignmentRepository`) rather than PR #6's flat, pre-`Client`-layer
design, since that model didn't exist when PR #6 was written.

## Explicitly out of scope for this document

- No code, entities, migrations, or API endpoints are being proposed as final designs here — the
  descriptions above are the *capability*, not a schema to copy verbatim from the closed PR #6
  branch.
- No priority, sprint assignment, or owner is set. This is a backlog record, not a committed plan.
- No claim is made that any of these five items are required for the platform's current
  direction — they are documented so the decision to omit them (made when PR #6 was closed) is an
  explicit, visible one rather than something later engineers have to rediscover by reading a
  closed pull request.

## Provenance

- Original proposal: PR #6, "Sprint 6: Company Configuration — companies, versions, statutory,
  banks, policies, shared services" (branch `claude/sprint-6-company`, closed without merging,
  2026-08-05, closed by repository owner as superseded).
- Gap verified against `main` @ `ed61369c664809e5afeba03c86525cee66669d8a` during the Sprint 25B
  production-readiness audit's branch-cleanup review.
