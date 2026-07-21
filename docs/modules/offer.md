# Offer Management & Pre-Boarding module

**Package:** `com.ewos.offer` | **Depends on:** `shared`, `identity`, `employee`, `organization`, `workflow`, `recruitment`, `ats`
**Introduced:** T4 (Business Bible Volume 5 — Talent Management)

## Purpose
Owns the last leg of hiring before an employee joins: draft offers, versioned revisions,
approval workflow, digital acceptance, negotiation, pre-boarding checklist, background /
medical / IT / joining-kit orchestration, and the joining confirmation that hands off to the
Employee master. Consumes {@link com.ewos.ats.domain.JobApplication ATS applications} and
{@link com.ewos.ats.domain.Candidate candidates} from T2; the T3 `ROUND_DECIDED / PROCEED` event
is the natural trigger for offer initiation (subscription is left to the deployment).

## Milestone

| Milestone | Migration | Scope |
|---|---|---|
| T4 Offer + Pre-Boarding | V24 | Templates, offers, negotiations, checklists, task templates, task instances |

## Domain

### Offer

* `OfferTemplate` — reusable per-tenant letter body + defaults (currency, notice, probation, expiry).
* `Offer` — versioned offer tied to an application. Revisions produce a new row with `version+1`
  and `previous_offer_id` pointing back at the row being revised.
* `OfferNegotiation` — one round of counter-proposal history.
* `CompensationBreakdown` — value object; `OfferPolicy` enforces `totalCtc == base + variable +
  one-time + hiring + retention`.
* `OfferPolicy` — status transition guards + coherent-total check + expiry check.

### Pre-Boarding

* `PreboardingTaskTemplate` — per-company task catalogue (task type + default owner + SLA).
* `PreboardingChecklist` — one per accepted offer.
* `PreboardingTaskInstance` — actual tasks on the checklist.

### Enums

`OfferStatus`, `EmploymentType`, `NegotiationParty`, `PreboardingChecklistStatus`,
`PreboardingTaskType`, `PreboardingTaskOwner`, `PreboardingTaskStatus`.

### Pluggable frameworks

* `BackgroundVerificationService` + `NoOpBackgroundVerificationService`.
* `MedicalCheckService` + `NoOpMedicalCheckService`.
* `ReferenceCheckService` + `NoOpReferenceCheckService`.
* `EmployeeIdGenerator` + `SequentialEmployeeIdGenerator` (produces `EMP-YYYYMM-XXXXXX`).
* `OfferNotifier` + `NoOpOfferNotifier` — covers extended / accepted / declined / revised / expired / withdrawn / joined **and** on-demand reminders for open offers and outstanding pre-boarding tasks.

## Offer lifecycle

```
DRAFT ─submit─▶ PENDING_APPROVAL ─approve─▶ APPROVED ─extend─▶ EXTENDED
                                  ─reject─▶ REJECTED
EXTENDED ─accept─▶ ACCEPTED   (digital signature captured)
EXTENDED ─decline─▶ DECLINED
EXTENDED / PENDING_APPROVAL ─revise─▶ REVISED   (new version row)
EXTENDED ─expire─▶ EXPIRED    (past expires_at)
* ─withdraw─▶ WITHDRAWN       (from any non-terminal state)
```

## Pre-boarding lifecycle

```
PENDING → IN_PROGRESS → COMPLETED → JOINED   (all mandatory tasks done + joining confirmed)
                                  → NO_SHOW
                                  → CANCELLED
```

Checklist creation from an ACCEPTED offer is idempotent and materialises every active
`PreboardingTaskTemplate` into an instance. On creation:

* `BACKGROUND_VERIFICATION`, `MEDICAL_CHECK`, and `REFERENCE_CHECK` tasks auto-initiate via the
  framework contracts and jump to `IN_PROGRESS` when the vendor returns a reference.
* `EMPLOYEE_ID` tasks auto-generate a value and jump to `COMPLETED` immediately.
* Other task types (`DOCUMENT_COLLECTION`, `ID_VERIFICATION`, `IT_ASSET`, `EMAIL_ACCOUNT`,
  `JOINING_KIT`, `JOINING_CONFIRMATION`, `OTHER`) stay `PENDING` for the owner to drive.

## Notifications
`OfferNotifier` covers candidate, recruiter, hiring manager, and HR touchpoints for offer
extended / accepted / declined / revised / expired / withdrawn and candidate joined. It also
exposes on-demand reminder hooks (`POST /offers/{id}/remind`,
`POST /preboarding/tasks/{id}/remind`) that a scheduler or ops team can trigger for open offers
and outstanding pre-boarding tasks.

## Permissions

| Code | Grants |
|---|---|
| `OFFER_READ` | View offers, templates, negotiations |
| `OFFER_WRITE` | Draft / revise / extend / withdraw offers; log negotiations |
| `OFFER_APPROVE` | Approve or reject offers pending approval |
| `OFFER_ACCEPT` | Record candidate acceptance / decline |
| `OFFER_ADMIN` | Delete templates; administer configuration |
| `PREBOARDING_READ` | View checklists and task instances |
| `PREBOARDING_WRITE` | Manage tasks: assign, update status, confirm joining |
| `PREBOARDING_ADMIN` | Administer task templates |

## REST surface

| Base | Purpose |
|---|---|
| `/api/v1/offers/templates` | Offer-template CRUD |
| `/api/v1/offers` | Offer lifecycle (draft / update / submit / approve / reject / extend / accept / decline / revise / withdraw / expire) + queries |
| `/api/v1/offers/{id}/negotiations` | Negotiation log + list |
| `/api/v1/preboarding/task-templates` | Task-template CRUD |
| `/api/v1/preboarding/checklists/from-offer/{offerId}` | Materialise a checklist |
| `/api/v1/preboarding/checklists/{id}` | Read + confirm-joining / no-show / cancel + task list |
| `/api/v1/preboarding/tasks/{id}/status` | Update task status |
| `/api/v1/preboarding/checklists` | List by status |

Every request requires the `X-Tenant-Id` header.

## Workflow integration
Submitting an offer for approval starts a workflow instance with subject-type
`offer.approval`. Withdrawing a `PENDING_APPROVAL` offer cancels the workflow instance.

## Events — topic `ewos.offer.event`
Kafka-optional publisher on `AFTER_COMMIT`. Codes in `OfferEventType`:

`OFFER_TEMPLATE_CREATED / _UPDATED / _DEACTIVATED`,
`OFFER_DRAFTED / _SUBMITTED / _APPROVED / _REJECTED / _EXTENDED / _ACCEPTED / _DECLINED /
_REVISED / _EXPIRED / _WITHDRAWN / _NEGOTIATION_LOGGED`,
`PREBOARDING_CHECKLIST_CREATED / _COMPLETED / _JOINED / _CANCELLED / _NO_SHOW`,
`PREBOARDING_TASK_CREATED / _STARTED / _COMPLETED / _SKIPPED / _FAILED`.

## Persistence

V24 tables:

* `offer_templates` — partial-unique on `(tenant, company, LOWER(code))` alive.
* `offers` — partial-unique on `(tenant, company, LOWER(offer_number))`; secondary indexes on
  application / candidate / status / (status + expires_at) for cron-driven expiry sweep.
* `offer_negotiations` — append-only (no soft delete); indexed by offer + submitted_at.
* `preboarding_task_templates` — partial-unique on `(tenant, company, LOWER(code))` alive.
* `preboarding_checklists` — partial-unique on `(tenant, offer)` alive.
* `preboarding_task_instances` — indexed by checklist + sort_order.

Every table uses `AuditableEntity` and `@Version` optimistic locking.

## Handoff to Employee master
`POST /preboarding/checklists/{id}/confirm-joining` flips the checklist to `JOINED`, links the
resulting Employee ID (if supplied), and emits `PREBOARDING_CHECKLIST_JOINED`. The Employee
module can subscribe to this event to create the employment record; T5 (Onboarding) will drive
the subsequent employee-record activation.

## What T4 does NOT include (deferred)
* Real letter-template rendering (Freemarker / Handlebars — hooks are in place; the body is
  stored as a text blob today).
* Real BGV / medical / reference-check vendor bindings — the framework contracts are ready;
  vendors ship per tenant.
* Scheduled reminder & expiry sweep — endpoints are provided (`POST /offers/{id}/expire`,
  `POST /offers/{id}/remind`, `POST /preboarding/tasks/{id}/remind`); wiring them to a cron is
  a deployment concern.
* Auto-creation of an Employee master row on join — the checklist knows how to store an
  `employee_id`; the Employee module handles insertion via T5.
