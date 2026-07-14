# EWOS Project Handover — for the Frontend Team

> **Reader:** Kimi (incoming frontend engineer).
> **Prepared by:** Backend team.
> **Date:** 2026-07-14.
> **Repository:** `buntychavan-web/EWOS` — branch `claude/repository-selection-575dn9`.
> **Backend head commit:** `79bb48e`.
>
> This document is self-contained. You do not need any chat history to start work.

---

## 1. Project Overview

**EWOS** — Enterprise Workforce Operating System — is a multi-tenant HRMS backend. It manages the
core building blocks of an HR platform (companies, organization structure, people, employment,
payroll, leave, attendance) with strict effective-dating, per-tenant configurability, and full
audit trails.

**Domain shape.**

- A **Tenant** owns one or more **Companies**.
- A **Company** is configured with statutory registrations, bank accounts, policy references, and
  shared-service (HR / Payroll / Finance / IT) teams.
- Each Company has an **Organization Structure** made of configurable **Levels** (Business Unit /
  Region / Branch — customer-defined) and **Nodes** arranged in a tree. The tree can move / merge
  / split / rename over time without ever losing history.
- **People** are the permanent human identities in the platform. A Person is *not* an Employee —
  future Employment records will FK into `persons.id` so one Person can hold many jobs over time.
- Every configurable resource — policies, cost centres, shared services — can be overridden per
  Organization Node with an effective-dated window; if there's no override, the value inherits
  from the parent chain and ultimately from the Company.

**Where we are today.** Sprints 1 through 8.1 are shipped. Person Engine, Organization Structure,
Company Configuration, User Management, Identity, and the platform foundation are all in place.
Employment (Sprint 8.2), Payroll, Leave, and Attendance are not yet implemented — see §6.

---

## 2. Technology Stack

### Backend (already built)

- **Runtime:** Java 21, Spring Boot 3.3.5
- **Build:** Maven 3.9
- **Database:** PostgreSQL 16 (schema owned by Flyway; `ddl-auto=none`)
- **Cache:** Redis 7 (config wired, not yet used at runtime)
- **Security:** Spring Security stateless + HS256 JWT via JJWT 0.12.6
- **API docs:** springdoc-openapi 2.6 → Swagger UI at `/swagger-ui.html`, raw at `/v3/api-docs`
- **Auditing:** Spring Data JPA auditing, `AuditorProvider` reads JWT subject
- **Observability:** Spring Boot Actuator (`/actuator/health`, `/actuator/info`, `/actuator/metrics`), SLF4J + logback with `X-Request-ID` correlation
- **Testing:** JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers Postgres (singleton pattern)
- **Quality gates:** Spotless (Google Java Format AOSP), Checkstyle 10.18, PMD 7.6, SpotBugs 4.8, JaCoCo 0.8.12 (80 % BUNDLE instruction floor)
- **CI:** GitHub Actions (`.github/workflows/ci.yml`) runs `mvn -B -ntp verify` on every push / PR

### Frontend (Kimi's choice — recommendations only)

The backend is framework-agnostic. Any of these work fine:

| Stack | Notes |
|---|---|
| **React + Vite + TypeScript** _(recommended default)_ | Ecosystem maturity, easy to hire for, matches API shape trivially with `zod` schemas. |
| **Next.js 14** | Only if you want SSR/edge/RSC. Overkill for an internal HRMS admin UI. |
| **Vue 3 + Vite + TypeScript** | Fine if the team knows Vue better. |

**Recommended sidekicks regardless of framework:**

- **HTTP:** `axios` or `ky` with a single client wrapping auth + refresh.
- **Data-fetching:** `@tanstack/react-query` (or Vue Query) — matches the paged/filtered API shape.
- **Forms:** `react-hook-form` + `zod` resolver; server-side validation errors surface via the platform's uniform `ApiError` envelope (see §7).
- **State:** Query cache handles most server state; use `zustand` for the auth token store; don't reach for Redux unless you outgrow both.
- **UI kit:** headless (`radix-ui` or `headlessui`) + `tailwindcss`.
- **Types from OpenAPI:** run `openapi-typescript` against `http://localhost:8080/v3/api-docs` to generate `types/api.d.ts` — do not hand-write DTOs.

---

## 3. Folder Structure

### Backend (as it stands)

```
EWOS/
├── src/
│   ├── main/
│   │   ├── java/com/ewos/
│   │   │   ├── EwosApplication.java
│   │   │   ├── common/           ← ApiError, GlobalExceptionHandler, AuditableEntity, CorrelationIdFilter
│   │   │   ├── config/           ← Spring configuration
│   │   │   ├── security/         ← SecurityConfig, JwtService, JwtAuthenticationFilter
│   │   │   ├── identity/         ← users, roles, permissions, refresh tokens (Sprint 2/4/5)
│   │   │   ├── company/          ← Sprint 6
│   │   │   ├── organization/     ← Sprint 7
│   │   │   ├── person/           ← Sprint 8.1
│   │   │   └── dashboard/        ← Sprint 8.1.1
│   │   └── resources/
│   │       ├── application.yml (+ -dev / -prod / -test overrides)
│   │       ├── db/migration/V1…V9__*.sql
│   │       └── logback-spring.xml
│   └── test/java/com/ewos/       ← mirror of main, one unit test per service + one integration test per controller
├── docs/
│   ├── HANDOVER.md               ← this file
│   ├── architecture/README.md
│   ├── adr/*.md                  ← ADR-0001, ADR-0002, ADR-0003
│   ├── business-rules/*.md       ← per-module rule catalogues
│   ├── reviews/
│   │   ├── CTO_REVIEW_CHECKLIST.md
│   │   └── sprint-8.1-cto-review.md
│   └── sprints/README.md
├── config/                       ← checkstyle.xml, pmd-ruleset.xml, spotbugs-exclude.xml
├── .github/workflows/ci.yml
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── PROJECT_STATUS.md             ← always-current single source of truth
├── CONTRIBUTING.md
└── README.md
```

### Frontend (suggested layout — you'll create this)

```
ewos-web/
├── src/
│   ├── main.tsx
│   ├── app/                      ← route shells, layout, providers
│   ├── features/                 ← one folder per backend module — mirror the API
│   │   ├── auth/                 ← login form, token store, refresh interceptor
│   │   ├── users/
│   │   ├── companies/
│   │   ├── organization/
│   │   ├── person/               ← 31 endpoints — biggest surface today
│   │   └── dashboard/
│   ├── lib/
│   │   ├── api/                  ← axios/ky client + typed wrappers generated from OpenAPI
│   │   ├── auth/                 ← accessToken + refreshToken storage, refresh flow
│   │   └── format/               ← date, currency, i18n helpers
│   ├── components/               ← app-wide reusable UI (Button, Modal, Table, DataGrid, Toast)
│   ├── hooks/
│   ├── types/api.d.ts            ← generated via openapi-typescript
│   └── styles/
├── public/
├── tests/                        ← unit (vitest) + e2e (playwright)
├── .env.example
├── package.json
└── vite.config.ts
```

**Feature-folder rule.** One folder per backend module — if you're building the Persons list
page, everything lives under `src/features/person/`. Never scatter Person UI across `pages/`,
`components/`, and `stores/` — a new engineer should be able to grep one folder for the full
surface.

---

## 4. Current Implementation Status

| Layer | Status |
|---|---|
| Backend foundation (Spring Boot, DB, Flyway, JWT, CI, quality gates) | **Done** — Sprints 1, 2, 5 |
| Identity & user management | **Done** — Sprints 2, 4 |
| Company Configuration | **Done** — Sprint 6 |
| Organization Structure Engine | **Done** — Sprint 7 |
| Person Engine (permanent identity, duplicate detection, readiness) | **Done** — Sprint 8.1 |
| Dashboard summary API | **Done** — Sprint 8.1.1 |
| Employment | **Not started** — Sprint 8.2, next up |
| Payroll / Leave / Attendance / Workflow / Reporting | **Not started** |
| **Frontend** | **Not started — this is your greenfield.** |
| CORS for browser clients | **Not configured** — see §14 Known Issues |
| PII encryption (PAN / Aadhaar / passport) | **Deferred** — see §14 |

Backend test posture as of head commit: **79/79 unit tests green**, integration tests pass on CI
(require Docker). All quality gates green.

---

## 5. Completed Sprints

| # | Scope | Migration | Documentation |
|---|---|---|---|
| 1 | Project foundation (Spring Boot, Postgres, Flyway, JWT scaffold, Actuator, OpenAPI, Docker) | V1 | `PROJECT_STATUS.md § 1` |
| 2 | Identity — users, roles, permissions, JWT login, refresh rotation | V2, V3 | `PROJECT_STATUS.md § 1` |
| 4 | User Management — CRUD, password policy, history, search (Sprint 3 was skipped) | V4 | `PROJECT_STATUS.md § 1` |
| 5 | Hardening — CI, static analysis, JaCoCo 80 %, soft delete, `@Version`, logout, correlation IDs | V5 | `PROJECT_STATUS.md § 1` |
| 6 | **Company Configuration** — tenants, companies, versions, statutory, bank accounts, policy assignments, shared services | V6 | [ADR-0001](adr/0001-company-effective-dating-and-tenancy.md) |
| 7 | **Organization Structure Engine** — configurable levels, nodes, append-only history, walk-up inheritance | V7 | [ADR-0002](adr/0002-organization-structure-engine.md) |
| 8.1 | **Person Engine** — permanent identity, effective-dated profile, duplicate detection, readiness | V8 | [ADR-0003](adr/0003-person-engine.md), [business rules](business-rules/person-engine.md), [CTO review](reviews/sprint-8.1-cto-review.md) |
| 8.1.1 | Dashboard summary — `GET /api/dashboard/summary` in one native query | V9 | (included in Sprint 8.1 review) |

Running sprint index: [`/docs/sprints/README.md`](sprints/README.md).

---

## 6. Pending Sprints

| # | Planned scope | Frontend impact |
|---|---|---|
| **8.2** | **Employment** — Employment records referencing `persons.id`. One Person, many Employments over time (joins, exits, rehires, concurrent contracts). Includes Employee ID, hire date, employment type, work location, cost centre, current status. | Employee list/detail screens; `Dashboard.employees` flips from `0` to a real count automatically. |
| 9 | Payroll — pay periods, salary structure, run engine, payslips, F&F | Payroll admin UI, payslip download |
| 10 | Leave — leave types, balances, requests, approvals | Leave request flow, manager approval queue |
| 11 | Attendance — clock-in/out, roster, exceptions | Attendance dashboard, calendar view |
| 12+ | Workflow, Reporting, Notifications, Documents | Cross-cutting UI |

**Frontend sprints are not yet planned** — coordinate with the backend PM before designing pages
that assume unreleased endpoints.

---

## 7. Backend APIs Available

**Base URL (dev):** `http://localhost:8080`
**Swagger UI:** `http://localhost:8080/swagger-ui.html`
**OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

### Uniform response envelope (errors)

Every non-2xx response is a JSON `ApiError`:

```json
{
  "timestamp": "2026-07-14T09:12:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Company code 'ACME' already exists for this tenant",
  "path": "/api/v1/companies",
  "correlationId": "b3a64d56-9c39-5e2c-8ce0-f64e5f985602"
}
```

Rules:

- `message` is human-readable — safe to show in a toast.
- `correlationId` is echoed back in the `X-Request-ID` response header. Log it in the browser
  console and the backend log to correlate.
- Field validation errors surface at HTTP 400 with a message like
  `Validation failed: firstName must not be blank; email must be a well-formed email address`.

### Endpoint catalogue (all 60+)

Every mutating endpoint is guarded by an authority. See §8 for how to hold one.

#### Auth (public — no bearer needed)
| Verb | Path | Purpose |
|---|---|---|
| POST | `/api/v1/auth/login` | `{username, password}` → `TokenResponse` |
| POST | `/api/v1/auth/refresh` | `{refreshToken}` → new `TokenResponse`; old refresh is revoked |
| POST | `/api/v1/auth/logout` | `{refreshToken}` → 204; idempotent |

#### Users (`USER_READ` / `USER_WRITE` / `USER_DELETE`)
| Verb | Path | Purpose |
|---|---|---|
| POST | `/api/v1/users` | Create |
| GET | `/api/v1/users` | Paged search |
| GET | `/api/v1/users/{id}` | Get |
| PUT | `/api/v1/users/{id}` | Update profile / roles |
| PATCH | `/api/v1/users/{id}/status` | Enable / disable |
| POST | `/api/v1/users/{id}/reset-password` | Admin reset |
| POST | `/api/v1/users/me/change-password` | Self-service |
| DELETE | `/api/v1/users/{id}` | Soft delete |

#### Companies (`COMPANY_READ` / `COMPANY_WRITE` / `COMPANY_DELETE`)
`/api/v1/companies` — create / search / get (`?asOf=`) / update-profile / status / soft-delete
`/api/v1/companies/{id}/versions` — profile history
`/api/v1/companies/{id}/statutory-registrations` — add / list / retire
`/api/v1/companies/{id}/bank-accounts` — add / list / status
`/api/v1/companies/{id}/policy-assignments` — assign / list / retire
`/api/v1/companies/{id}/shared-services` — assign / list / retire

#### Organization (`ORGANIZATION_READ` / `ORGANIZATION_WRITE` / `ORGANIZATION_DELETE`)
Levels: `/api/v1/organization/levels` — CRUD + status
Nodes: `/api/v1/organization/nodes` — create / search / get / tree / forest
Structural ops: `/api/v1/organization/nodes/{id}/{rename|move|merge|split|deactivate|reactivate}`
History: `/api/v1/organization/nodes/{id}/versions`
Inheritance: `/api/v1/organization/nodes/{id}/inheritance-overrides` + `/inheritance/{kind}?asOf=`

#### Persons (`PERSON_READ` / `PERSON_WRITE` / `PERSON_DELETE` / `PERSON_DUPLICATE_OVERRIDE`)
Core: `/api/v1/persons` — create (with duplicate check) / paged list / get (`?asOf=`) / by-group-id / update-profile / status / delete
Versions: `/api/v1/persons/{id}/versions`
Readiness: `/api/v1/persons/{id}/readiness`
Search: `/api/v1/persons/search?q=`
Contacts: `/api/v1/persons/{id}/contact` (PUT to replace) + `/contacts` (history)
Sub-resources: `/addresses` / `/emergency-contacts` / `/family` / `/education` / `/documents` — all with add/list, most with retire or soft-delete
Duplicate detection: `POST /api/v1/persons/duplicate-check` + `GET/PUT /duplicate-rules`

#### Dashboard (`DASHBOARD_READ`)
| Verb | Path | Purpose |
|---|---|---|
| GET | `/api/dashboard/summary` | `{employees, users, departments, roles}` (employees + departments are `0` until those modules ship) |

**Note the path.** Dashboard sits at `/api/dashboard/…`, not `/api/v1/dashboard/…`. This
deliberate deviation is documented in the Sprint 8.1 review §16.1.

---

## 8. Authentication Flow

### Sequence

```
Browser                                 EWOS backend
   │                                          │
   │ 1. POST /api/v1/auth/login               │
   │    { username, password }                │
   │─────────────────────────────────────────►│
   │                                          │  bcrypt verify + issue tokens
   │◄─────────────────────────────────────────│
   │ 200  { accessToken (JWT, 15m),           │
   │        refreshToken (opaque, 7d),        │
   │        tokenType: "Bearer",              │
   │        expiresIn: 900 }                  │
   │                                          │
   │ 2. Any secured call                      │
   │    Authorization: Bearer <accessToken>   │
   │─────────────────────────────────────────►│
   │◄─────────────────────────────────────────│
   │ 200 …                                    │
   │                                          │
   │ 3. When access expires (401)             │
   │    POST /api/v1/auth/refresh             │
   │    { refreshToken }                      │
   │─────────────────────────────────────────►│
   │                                          │  rotate: revoke old, mint new
   │◄─────────────────────────────────────────│
   │ 200 { accessToken, refreshToken, … }     │
   │                                          │
   │ 4. On sign-out                           │
   │    POST /api/v1/auth/logout              │
   │    { refreshToken }                      │
   │─────────────────────────────────────────►│
   │◄─────────────────────────────────────────│
   │ 204                                      │
```

### Token characteristics

- **Access token.** JWT signed HS256. Default TTL **15 minutes**. Carries `sub` = user id (UUID), `authorities` = list of permission codes (`USER_READ`, `PERSON_WRITE`, `DASHBOARD_READ`, …), `iss`, `iat`, `exp`. Do **not** trust the token client-side — the backend re-checks on every call.
- **Refresh token.** Opaque, 48-byte URL-safe base64 (approx 64 chars). Default TTL **7 days**. Stored in DB as SHA-256 hash. Rotated on every use; the old refresh token is revoked before a new pair is issued. Reuse of a revoked refresh returns 401.
- **Logout.** Revokes the refresh token immediately; the access token continues to work until its `exp` (max 15 minutes). Clients should discard both.

### What the frontend must do

1. **Storage.** Prefer `sessionStorage` for the access token and an httpOnly cookie for the refresh token (needs backend change to set the cookie — coordinate with backend before you go there). Interim: keep both in memory + `localStorage`. Never ship a build that logs tokens.
2. **Attach.** `Authorization: Bearer <accessToken>` on every request except `/api/v1/auth/*`.
3. **Handle 401.** Silently attempt one refresh; on refresh failure, redirect to `/login`. **Guard against refresh storms** — queue concurrent 401-triggered refreshes behind a single in-flight promise.
4. **Preemptive refresh** (nice to have): refresh at `expiresIn - 60s`.
5. **Logout.** Call `POST /api/v1/auth/logout`, then wipe local state, then navigate to `/login`.
6. **Correlation ID.** Read `X-Request-ID` from responses; include it in bug reports and error toasts so backend can find the log line.

### Default admin (dev only)

- Username: `admin`
- Password: `ChangeMe!Admin123`
- Reset on first login via `POST /api/v1/users/me/change-password`.
- Change these before any non-dev deployment.

### Permission list (as of this handover)

`SYSTEM_ADMIN`, `USER_READ/WRITE/DELETE`, `ROLE_READ/WRITE`,
`COMPANY_READ/WRITE/DELETE`, `ORGANIZATION_READ/WRITE/DELETE`,
`PERSON_READ/WRITE/DELETE/DUPLICATE_OVERRIDE`, `DASHBOARD_READ`.

`SYSTEM_ADMIN` holds all of them by seed (V3 / V6 / V7 / V8 / V9).

---

## 9. Coding Standards

### Backend (already in force; you'll see them enforced by CI)

- Spotless (Google Java Format AOSP), Checkstyle 10.18, PMD 7.6, SpotBugs 4.8 — all bound to `verify`.
- No comments unless the *why* is non-obvious.
- Package-by-feature (`com.ewos.<module>.{api,application,domain,infrastructure}`).
- Entities extend `AuditableEntity`. Soft delete uses `@SQLDelete` + `@SQLRestriction`.
- REST: `/api/v1/<resource>/…`, `@PreAuthorize` on every mutation, `@ApiResponses` on every endpoint.
- DTOs are Java `record`s; requests use `jakarta.validation`; entities never cross the API boundary.

### Frontend (recommended — nothing enforced yet)

- **TypeScript strict mode on** (`strict: true`, `noImplicitAny: true`, `noUncheckedIndexedAccess: true`).
- **ESLint + Prettier** with `eslint-config-airbnb-typescript` or the `@typescript-eslint/recommended-type-checked` preset.
- **Test:** vitest for unit / component tests, Playwright for end-to-end.
- **API types generated, never handwritten.** `openapi-typescript` → `src/types/api.d.ts`. If a backend endpoint changes shape, regenerate the file; every consumer breaks at compile time.
- **Error handling:** the `ApiError` envelope is your contract — never assume the shape of a 4xx/5xx body outside it. Show `message` to the user; log `correlationId`.
- **Forms:** `react-hook-form` + `zod` — mirror the backend `jakarta.validation` constraints in the zod schema so client-side errors match server-side errors.
- **Feature folders.** One folder per backend module (see §3).
- **No leaking JPA field names** — the API DTO shape is what the frontend sees. If a backend DTO field is renamed, regenerate types.
- **Accessibility.** Use `radix-ui` or `headlessui` primitives. Verify keyboard nav and screen-reader labels on every page.
- **PII in the browser.** Do not persist PAN / Aadhaar / passport / mobile / email in `localStorage`. Keep them in memory only.

---

## 10. UI Architecture

### Application shell

- **Public routes:** `/login` (only). Everything else requires auth.
- **Authenticated shell:** left sidebar navigation, top bar with tenant/company switcher, breadcrumb + page title, main content, right-side drawer for quick actions.
- **Empty states, loading states, error states** for every page.

### Recommended route map (aligned to backend modules)

```
/login
/dashboard                           ← GET /api/dashboard/summary
/users                               ← list + search
  /users/new
  /users/:id
/companies
  /companies/new
  /companies/:id                     ← profile, statutory, banks, policies, shared services, versions
/organization                        ← forest view (whole tree)
  /organization/levels               ← configurable levels
  /organization/nodes/:id            ← node detail: tree, versions, inheritance
/persons
  /persons/new                       ← includes live duplicate-check preview
  /persons/:id                       ← overview, profile versions, contacts, addresses, family,
                                        emergency, education, documents, readiness
  /persons/duplicate-rules           ← tenant admin
/settings
  /settings/profile                  ← change password
```

### Data-fetching pattern

Use React Query (or Vue Query). One hook per endpoint under `src/features/<module>/queries.ts`:

```ts
// features/person/queries.ts (illustrative)
export const usePerson = (id: string) =>
  useQuery({ queryKey: ['person', id], queryFn: () => api.getPerson(id) });

export const useCreatePerson = () =>
  useMutation({
    mutationFn: (input: CreatePersonRequest) => api.createPerson(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['persons'] }),
  });
```

Cache invalidation is the discipline that keeps the UI consistent — every mutation must invalidate
its list + detail keys.

### Effective-dated screens

The API is **effective-dated**. Any screen showing a Company / Person / Node profile should:

- Display the current version by default.
- Offer an "As of" date picker that calls `GET /…/{id}?asOf=YYYY-MM-DD`.
- Offer a "Version history" tab that calls `GET /…/{id}/versions`.
- On edit, require an **effective from** date (must be strictly after the current version's start) and a **change reason**. The backend will 400 if you backdate.

### Duplicate detection UX

When creating a Person:

1. As the user types PAN / Aadhaar / passport / mobile / email / name+DOB, fire
   `POST /api/v1/persons/duplicate-check` (debounced) to surface possible matches.
2. On submit, if the server returns **409** with a match list, show the matches inline and offer:
   *(a)* "Use existing person" (link out), *(b)* "Edit inputs", *(c)* "Create anyway" — button only
   visible if the caller holds `PERSON_DUPLICATE_OVERRIDE`. Sending `overrideDuplicates=true`
   without the permission returns 403.

### Readiness meter

Every Person page should show a readiness gauge fed by `GET /api/v1/persons/{id}/readiness`.
Backend returns 7 section percentages + overall — turn each section into a "nudge to complete"
call-to-action. It is a **signal, never a gate** — never block form submission on low readiness.

### Organization tree UX

`GET /api/v1/organization/nodes/{id}/tree` returns the full subtree; render with a virtualised
tree component. Move / merge / split are POST operations that mutate the tree — always refetch
the tree after these.

---

## 11. Important Architecture Decisions

Full docs under `/docs/adr/`. Summary of what you must respect from the frontend side:

### ADR-0001 — Company effective-dating and tenancy
- **Never send a request that overwrites a historical row.** Profile edits ALWAYS carry a new `effectiveFrom` and get a new version; the backend closes the previous window.
- **Tenant isolation policy** (`SHARED` vs `SEGREGATED`) is stored on the tenant. Query-time enforcement is deferred to Sprint 6.1 — treat `tenantId` filters as advisory today.
- **Statutory numbers + bank account numbers are never cloned** across companies. Clone company only carries policy references, never numbers.

### ADR-0002 — Organization structure engine
- **Never hard-code level names.** Business Unit / Division / Region / Branch are per-tenant configuration. The UI must fetch levels from `GET /organization/levels`.
- **Employees will reference `organizationNodeId` only.** Never store `regionId` / `departmentId` / `branchId` on an employee — the org-node reference IS the region / department / branch, and it moves for free when the node moves.
- **Inheritance resolution.** For any inheritable resource, call `GET /nodes/{id}/inheritance/{kind}?asOf=`. Backend walks the parent chain and returns the first live override; `fromOverride=false` means "fall through to the company-level assignment".

### ADR-0003 — Person Engine
- **Group Person ID (`P000000001`) is immutable and never reused** — safe to embed in printed docs, offer letters, external forms.
- **Person is NOT an Employee.** Never merge them into one screen. A Person section should read "Employments" (plural) — you'll wire it up in Sprint 8.2.
- **Duplicate detection runs before create.** Design the form to handle 409-with-matches gracefully (see §10).
- **Readiness is signalling, not gating.** A user with 5 % completion is still creatable.

---

## 12. Environment Setup

### Prerequisites

| Tool | Version |
|---|---|
| JDK | 21 (Temurin recommended) |
| Maven | 3.9+ (or use the wrapper `./mvnw`) |
| Docker | 20+ (for docker-compose + Testcontainers) |
| Docker Compose | v2 |
| Node.js | 20+ (for the frontend you'll build) |
| pnpm / npm / yarn | pnpm 9 recommended for the frontend |
| Git | 2.40+ |

### Backend clone + install

```bash
git clone <repo-url> EWOS
cd EWOS
git switch claude/repository-selection-575dn9    # current dev branch
mvn -q -DskipTests install
```

### Environment variables (with defaults)

| Var | Default | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Active profile |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/ewos` | DB URL |
| `SPRING_DATASOURCE_USERNAME` | `ewos` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `ewos` | DB password |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis |
| `JWT_SECRET` | *dev placeholder — CHANGE IN PROD* | HMAC signing key (≥ 256 bits) |
| `JWT_ISSUER` | `ewos` | JWT `iss` claim |
| `JWT_ACCESS_TTL` | `15m` | Access-token lifetime |
| `JWT_REFRESH_TTL` | `7d` | Refresh-token lifetime |
| `ADMIN_USERNAME` / `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin` / `admin@ewos.local` / `ChangeMe!Admin123` | Bootstrap admin |
| `PASSWORD_MIN_LENGTH` / `MAX_LENGTH` | `8` / `128` | Password policy |
| `PASSWORD_REQUIRE_UPPERCASE` / `LOWERCASE` / `DIGIT` / `SPECIAL` | `true` × 4 | Password policy |
| `PASSWORD_HISTORY_SIZE` | `5` | Block reuse of last N passwords |
| `SERVER_PORT` | `8080` | HTTP port |

Copy `.env.example` if you make one, do not commit real secrets.

### Frontend environment (once you set it up)

```
VITE_API_BASE_URL=http://localhost:8080
VITE_TOKEN_STORAGE=memory     # or 'session'
```

---

## 13. How to Run the Project

### Option A — everything in Docker Compose (fastest for a new machine)

```bash
docker compose up --build
```

Brings up Postgres, Redis, and the backend. First boot runs Flyway migrations V1–V9 and seeds the
bootstrap admin. Backend listens on `http://localhost:8080`.

### Option B — Postgres + Redis in Docker, backend from IDE

```bash
docker compose up -d postgres redis
mvn spring-boot:run
```

### Verify the backend is up

```bash
# Health probe
curl -s http://localhost:8080/actuator/health
# {"status":"UP",...}

# Login as bootstrap admin
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"ChangeMe!Admin123"}'
# {"accessToken":"eyJhbG...", "refreshToken":"...", "tokenType":"Bearer", "expiresIn":900}

# Call a secured endpoint
TOKEN=<paste accessToken above>
curl -s http://localhost:8080/api/dashboard/summary -H "Authorization: Bearer $TOKEN"
# {"employees":0,"users":1,"departments":0,"roles":1}
```

### Full CI-equivalent local build

```bash
mvn spotless:apply             # format
mvn -B -ntp verify             # compile + unit + integration + all quality gates + JaCoCo 80% check
```

### Running the frontend against the local backend

Once you scaffold the app:

```bash
pnpm create vite ewos-web --template react-ts
cd ewos-web
echo 'VITE_API_BASE_URL=http://localhost:8080' > .env.local
pnpm i
pnpm dev
# open http://localhost:5173
```

**Important:** the browser will hit the backend on a different origin (`5173 → 8080`) and CORS is
**not yet configured**. See §14 — item 1.

### Regenerating API types (whenever backend changes)

```bash
pnpm dlx openapi-typescript http://localhost:8080/v3/api-docs -o src/types/api.d.ts
```

Commit the generated file with the change that triggered it, so a `git blame` on a UI break points
at the backend change.

---

## 14. Known Issues

Prioritised. Full list in [`PROJECT_STATUS.md § 7`](../PROJECT_STATUS.md).

### Blocks the frontend or the browser workflow

1. **CORS is not configured.** `SecurityConfig` calls `.cors(Customizer.withDefaults())` but no `CorsConfigurationSource` bean is registered — browser requests from `http://localhost:5173` (or any origin ≠ backend) will be blocked. **Backend owns the fix**; frontend can work via a Vite proxy in the interim:

   ```ts
   // vite.config.ts
   export default defineConfig({
     server: { proxy: { '/api': 'http://localhost:8080', '/v3': 'http://localhost:8080' } },
   });
   ```

   Tell the backend team when you need real CORS (needed for prod anyway).

2. **`JWT_SECRET` default is a placeholder.** Change it in every non-dev environment. Backend does not yet refuse to boot with the placeholder.

### Affects UX / feature completeness

3. **`Dashboard.employees` and `Dashboard.departments` return `0`** — Employment (Sprint 8.2) and any Department entity are not implemented. The API contract is stable; when 8.2 ships the counter flips automatically.
4. **Person search (`/persons/search`) and duplicate detection Name+DOB rule** scan open versions in memory. Fine at HR scale; performance debt at ~50k+ persons per tenant.
5. **Multi-tenancy isolation is not enforced at query time.** If a browser has an admin token and sends `tenantId=X`, the backend filters accordingly, but nothing prevents cross-tenant reads. Do not rely on client-side tenant switching as a security boundary.
6. **PII plaintext.** PAN / Aadhaar / passport are stored in plaintext today. Do not build features that require encryption promises to the user until backend delivers it.
7. **Non-DEFAULT tenants have no duplicate-detection rules seeded.** When you build tenant provisioning UI, add a rule-seed step.

### Housekeeping

8. **No `/api/v1/users/{id}/restore` endpoint.** Once soft-deleted, users can only be revived via SQL.
9. **No rate limiting / account lockout.** Login endpoint accepts unlimited attempts (login_history captures them).
10. **No CSRF protection** because auth is stateless JWT + `Authorization` header (correct for API clients; do NOT switch to cookie-based auth without adding CSRF back).
11. **No refresh-token device binding** — the `logout` endpoint revokes only the presented token, not a whole session family.

### Reviewed and documented

12. Dashboard endpoint lives at `/api/dashboard/summary` (not `/api/v1/…`). Deliberate deviation, documented in Sprint 8.1 review §16.1.
13. Person child entities (address / family / education / emergency / document) use soft-delete + `@Version`, not the full effective-dated versioning that the core profile uses.

---

## 15. Future Roadmap

### Backend (planned)

| Sprint | Deliverable | Frontend consequence |
|---|---|---|
| 8.2 | Employment engine — FKs `person_id` → `persons`, adds hire date, employment type, work location, cost centre, status | Employees list + detail; Person page gains "Employments" tab |
| 8.3+ | PII encryption (PAN / Aadhaar / passport → encrypted-at-rest + searchable hash) | No FE change; DB shape is invisible to browser |
| 8.3+ | Sprint 6.1 tenant isolation enforcement (Hibernate `@Filter` driven by JWT tenant claim) | Tenant claim added to JWT; token store must not conflate tenants |
| 9 | Payroll | Payroll UI (structure editor, run engine, payslip download) |
| 10 | Leave | Leave request + approval + calendar |
| 11 | Attendance | Clock-in/out, roster, exceptions |
| 12+ | Workflow, Reporting, Notifications, Documents | Cross-cutting UI |
| — | CORS + rate limiting + account lockout | Enables secure browser + mobile clients |
| — | `EXCLUDE USING gist` DB constraint for effective-date overlap | No FE change |

### Frontend (Kimi's plan)

Suggested first-90-days:

1. **Week 1–2** — scaffold Vite + React + TS, wire auth flow (login → token store → refresh → logout), Playwright e2e for login round-trip.
2. **Week 3–4** — Dashboard page (already an API), Users list + form.
3. **Week 5–6** — Company detail page (profile, statutory, banks, policies, shared services, versions).
4. **Week 7–8** — Organization tree (forest view, node detail, structural operations, inheritance override editor).
5. **Week 9–12** — Person module (create with duplicate check, detail, all sub-resources, readiness).

Every page should support "as-of" reads and version history where the API exposes them.

---

## Appendix — Where to look

- **Running status:** [`PROJECT_STATUS.md`](../PROJECT_STATUS.md)
- **Architecture overview:** [`docs/architecture/README.md`](architecture/README.md)
- **Architecture decisions:** [`docs/adr/`](adr/)
- **Business rules:** [`docs/business-rules/`](business-rules/)
- **Sprint index:** [`docs/sprints/README.md`](sprints/README.md)
- **CTO review checklist (mandatory pre-merge):** [`docs/reviews/CTO_REVIEW_CHECKLIST.md`](reviews/CTO_REVIEW_CHECKLIST.md)
- **Sprint 8.1 CTO review package:** [`docs/reviews/sprint-8.1-cto-review.md`](reviews/sprint-8.1-cto-review.md)
- **Contributing rules:** [`CONTRIBUTING.md`](../CONTRIBUTING.md)

If something is missing, wrong, or contradictory: file an issue, and ping the backend team on the
PR. Do not silently work around a documentation gap — flag it, and we'll fix it here.

Welcome aboard, Kimi.
