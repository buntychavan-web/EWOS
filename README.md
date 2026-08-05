# EWOS — Enterprise Workforce Operating System

![CI](https://github.com/buntychavan-web/EWOS/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen)
![Coverage floor](https://img.shields.io/badge/JaCoCo-%E2%89%A545%25%20enforced-yellow)

Backend for the EWOS HRMS platform — a multi-tenant, multi-module Human Resource Management System
covering the employee lifecycle from recruitment through exit, with Payroll (India-focused statutory
compliance) as its most complete module.

> **Contributing?** Read [`CONTRIBUTING.md`](./CONTRIBUTING.md) first — it covers the branching
> model, the PR checklist ([`.github/pull_request_template.md`](./.github/pull_request_template.md)),
> quality-gate expectations, and Testcontainers rules. [`PROJECT_STATUS.md`](./PROJECT_STATUS.md) is
> the sprint-by-sprint historical record and the authoritative source for open technical debt;
> [`docs/modules/`](./docs/modules) has one design doc per HRMS module.

---

## 1. Current project status

This backend has grown from a Sprint 1–5 identity/auth foundation into a 29-package HRMS platform.
As of this document, `main` carries every sprint through **Sprint 25B**, an independent-audit
verification pass (see [`PROJECT_STATUS.md`](./PROJECT_STATUS.md) for the full sprint-by-sprint
history).

- **Payroll** is the most mature module: full lifecycle (draft → process → finalize → freeze,
  including supplementary runs and an authorized reopen/correction path), the Indian statutory
  stack (PF/ESI/PT/LWF/TDS/Gratuity), maker-checker approval, an attendance-driven loss-of-pay
  engine, an employee loan & recovery engine, and encrypted bank-advice generation. **Payroll V1 is
  explicitly not declared "frozen."** An earlier freeze was superseded once an independent audit
  found genuine gaps (separation of duties, reopen/correction, real attendance-based LOP, loan
  tracking, a bank-advice data bug, and a payslip access-control gap); those were closed, but the
  module has not been re-certified complete. Treat any documentation elsewhere in this repo that
  still says "FROZEN" (e.g. an older revision of `docs/modules/payroll.md`) as superseded by this
  paragraph.
- **23 other HRMS modules** are implemented and wired end-to-end (see §2) — Recruitment/ATS through
  Exit, full talent management (Performance/Goals/Competency/Learning/Succession/Probation), a
  metadata-driven Workflow approval engine, Attendance, and Leave.
- **4 packages are reserved namespaces only** (`ai`, `analytics`, `governance`, `talent`) — see §2
  for what that means precisely; nothing under them is implemented yet, by design.
- **Test coverage**: 1,611 backend tests. The enforced JaCoCo floor is **45%** instruction coverage
  (`pom.xml`'s `jacoco.line.coverage.min`), not 80% — an earlier claim in this file was aspirational
  and never matched what was actually enforced. Measured coverage from this session's own run is
  ~60% (unit tests only; Docker-backed integration tests add more when run in CI). See §15 for the
  staged 45%→80% roadmap already on record in `PROJECT_STATUS.md`.
- **CI is green** on `main`, including the Docker-backed Testcontainers suite (identity auth flows,
  tenant resolution, CORS preflight, a full payroll-run end-to-end test, and a Flyway
  apply-all-65-migrations-against-a-fresh-database test) — GitHub Actions has Docker; not every local
  sandbox does (see §13).

## 2. Implemented modules

Each module lives under `com.ewos.<name>` with the standard `api / application / domain /
infrastructure` sublayers. Most have a dedicated design doc in [`docs/modules/`](./docs/modules).

| Module | Package | Summary |
|---|---|---|
| Identity | `identity` | Users, roles, permissions, JWT auth, refresh-token rotation, account lockout, rate limiting, password policy/history. |
| Tenancy | `tenancy` | Tenant → Client → Company hierarchy, Payroll Service Provider registry, Client Assignments (the "Chinese Wall" — see §5). |
| Employee | `employee` | Employee master record, identity linking, effective-dated profile data. |
| Organization | `organization` | Configurable org-unit types (Company/Department/Designation/Location/Grade/…), append-only structure history. |
| Workflow | `workflow` | Metadata-driven approval state machine consumed by Leave, Timesheet, Recruitment, Offers, Performance appraisals. |
| Attendance | `attendance` | Policies, time entries (immutable clock events), timesheets, holiday calendar, attendance-driven LOP calculator (feeds Payroll). |
| Leave | `leave` | Leave-type catalogue, allocations, running balances, workflow-driven leave requests. |
| Payroll | `payroll` | See §3 — the largest module by far (386 files). |
| Recruitment | `recruitment` | Job positions and requisitions with workflow approval. |
| ATS | `ats` | Candidates, resumes/documents, applications tying candidates to requisitions. |
| Interview | `interview` | Interview templates, rounds, panels, structured scorecards, feedback. |
| Offer | `offer` | Offer drafting/versioning/approval, digital acceptance, negotiation, pre-boarding checklist. |
| Onboarding | `onboarding` | Onboarding plans, task assignment, 30/60/90-day surveys; auto-created from offer pre-boarding. |
| Probation | `probation` | Probation lifecycle on top of the employee master: extend, confirm, terminate. |
| Performance | `performance` | Performance cycles, appraisal templates, bulk launch, cycle state machine, reports. |
| Goals | `goals` | Goal/OKR/KPI library, per-employee/team/department/company assignment. |
| Competency | `competency` | Competency library, skill matrix, development plans. |
| Learning | `learning` | Course catalogue, learning paths, enrollments, certifications. |
| Succession | `succession` | Career paths, promotion eligibility. |
| Exit | `exit` | Resignation, notice management, knowledge transfer, clearance, alumni record. |
| Data Exchange | `dataexchange` | Operational audit trail for exchanging payroll/HR data with an external system (event-driven + REST). |
| Import/Export | `importexport` | Shared bulk-import job/error audit trail used by Goals/Competency/Development-Plan imports. |
| Integration | `integration` | Generic integration adapter framework (REST/SFTP/CSV/Excel/upload), integration monitoring, Operations Dashboard. |
| Notification | `notification` | In-app + email notification delivery, templates, workflow-triggered notifications. *(Its own `package-info.java` still says "RESERVED" — that comment is stale; the module has 17 real classes including controllers, services, and repositories. Worth fixing in a future pass; not touched in this one, since this sprint's mandate is `README.md` only.)* |
| Shared kernel | `shared` | `AuditableEntity` base class, global exception handling, purge/retention jobs, cross-cutting config. |
| *Reserved* | `ai`, `analytics`, `governance`, `talent` | Namespace-only (`package-info.java`, no classes) — reserved per the platform's module-naming convention, not yet built. `ai` specifically is the reserved slot for a *future* LLM integration; see §6 for what already exists today that such an integration would sit behind. |

## 3. Payroll capabilities

Evidence-verified against the actual source (class / method), not asserted from memory —
see [`docs/modules/payroll.md`](./docs/modules/payroll.md) for full design detail.

| Capability | Where |
|---|---|
| Lifecycle (start / supplementary / finalize / freeze) | `payroll.application.PayrollRunService` — `start()`, `startSupplementary()`, `finalizeRun()`, `freeze()` |
| Provident Fund (PF) | `payroll.application.PfCalculationService.calculate()` |
| ESI | `payroll.application.EsiCalculationService.calculate()` |
| Professional Tax (PT) | `payroll.application.ProfessionalTaxCalculationService.calculate()` |
| Labour Welfare Fund (LWF) | `payroll.application.LwfCalculationService.calculate()` |
| Income Tax / TDS (old + new regime) | `payroll.application.IncomeTaxCalculationService.calculate()` |
| Gratuity | `payroll.application.GratuityCalculationService.calculate()`, consumed by `FinalSettlementService` |
| LTA block management | `payroll.application.LtaBlockService` — `configure()`, `creditAnnualEligibility()`, `claimJourney()`, `carryForwardUnusedClaim()`, `blockSummary()` |
| Tax on variable/one-time payments | `IncomeTaxCalculationService.calculate()`, incremental-tax-over-recurring-baseline logic (§8.3) |
| Prorated monthly tax recovery | `IncomeTaxCalculationService.calculate()`, even-share-vs-payable-earnings proration (§8.2) |
| Loss of pay — leave-driven | `payroll.domain.LopCalculator` |
| Loss of pay — attendance-driven | `attendance.domain.AttendanceLopCalculator.compute()` + `attendance.application.AttendanceLopService.computeForRun()` — additive on top of leave LOP, opt-in per company |
| Loan & recovery engine | `payroll.application.EmployeeLoanService` (create/early-closure/schedule) + `payroll.application.LoanRecoveryService` (queue + auto-recover via arrears) |
| Maker-checker | `payroll.application.PayrollApprovalService` — `submitForApproval()`, `decide()`; a run's own preparer can never finalize it (enforced in `PayrollRunService.finalizeRun()`, not just policy) |
| Bank advice | `payroll.application.BankAdviceService.generate()` / `.export()` — encrypted account numbers in the export, masked in API responses |
| Supplementary payroll | `PayrollRunService.startSupplementary()` |
| Reopen framework | `payroll.application.PayrollReopenService.authorizeReopen()` + `payroll.application.PayrollPeriodService.reopen()`, with an append-only `PayrollPeriodReopenLog` |

Also present: payroll simulation (dry run), bulk variable-input upload, payslip PDF generation,
payroll comparison & exception reports, a rule-based (non-LLM) payslip-insight engine (§6), and the
Knowledge Centre document foundation (§7).

## 4. Security

| Area | Status |
|---|---|
| Transport | Stateless JWT bearer auth (HS256), no session cookies, no HTTP Basic/form login. |
| CSRF | Disabled deliberately — there is no cookie-based ambient auth for a forged cross-site request to ride on; this is correct for a pure bearer-token API, not an oversight. |
| CORS | Deny-by-default; `prod` profile refuses to boot with a wildcard origin or `allowCredentials=true` + wildcard (`identity.infrastructure.security.cors.CorsConfig`, fails fast via `InitializingBean`). |
| JWT validation | `JwtService` verifies the HMAC signature and requires the configured issuer claim (`Jwts.parser().verifyWith(...).requireIssuer(...)`); expiry is enforced by the underlying JJWT parser. `JwtAuthenticationFilter` rejects and clears the security context on any `JwtException` rather than failing open. |
| Authorization | Method-level `@PreAuthorize` with explicit authorities on essentially every mutating admin endpoint (checked across all 131 controllers, 401 mutating mappings). The handful of exceptions are intentional: `/auth/login`, `/auth/refresh`, `/auth/logout` are public by design, and self-service endpoints are covered by the ownership-validation row below instead. |
| Ownership validation | Self-service endpoints (leave, payroll tax declaration, payslips, …) resolve the caller's own employee id server-side from the JWT (`employee.application.EmployeeContext`) — the endpoint has no `employeeId` parameter for a client to override, so cross-employee access isn't just checked, it's structurally impossible at that layer. |
| Tenant isolation | Two independent layers: `tenancy.infrastructure.security.TenantHeaderValidationFilter` rejects an `X-Tenant-Id` header that doesn't match the caller's JWT-derived home tenant (or an active cross-tenant grant); `tenancy.application.ClientAccessGuard` (the "Chinese Wall") additionally scopes provider-staff access per client company, re-checked live on every call rather than baked into the JWT. |
| Secrets at rest | Bank account numbers/routing codes are AES-256-GCM encrypted (`payroll.infrastructure.crypto.BankAccountFieldEncryptor`); passwords are BCrypt-hashed. |
| Startup validation | `JwtSecretGuard`, `AdminPasswordGuard`, and `BankAccountEncryptionKeyGuard` each refuse to boot outside `dev`/`test` if their respective secret is a placeholder or too short — see §11. |
| Brute-force protection | Configurable auth rate limiting (`InMemoryRateLimiter`) and account lockout (`AccountLockoutService`) on the login path. |

**Verdict: production-ready** for all of the above. No gap was found in this pass that isn't already
tracked as open technical debt elsewhere (§15) — e.g. coverage percentage, not a security defect.

## 5. Multi-tenancy

Tenant model: `Tenant → Client → Company`, with an outsourced-payroll-service-provider use case in
mind (`tenancy` module, §2). A user's home tenant is resolved from their JWT claim; every request
asserting a different tenant via `X-Tenant-Id` is checked against that claim or an explicit,
time-bounded cross-tenant grant (`TenantAccessGrant`) before the request is allowed to proceed —
enforced by `TenantHeaderValidationFilter`, not left to each controller to remember. `ClientAccessGuard`
adds a second, narrower scoping layer for the specific case of a payroll-service-provider's own staff
serving multiple client companies (see §4). Ordinary tenant employees/managers/HR staff are not
subject to that second layer — only genuine external provider operators are.

## 6. AI foundation

**No LLM is integrated anywhere in this codebase.** What exists is architectural preparation for one:
`payroll.application.PayrollInsightProvider` is an interface (`explainPayslip()`,
`explainRunExceptions()`) with exactly one implementation today —
`RuleBasedPayrollInsightProvider`, which is 100% deterministic and reuses existing calculation/audit
data. The intent, per its own class javadoc, is that a future LLM-backed implementation would sit
behind this same interface and use the rule-based provider's output as grounding context — this
codebase's calculations remain the source of truth, never the model. The separate `com.ewos.ai`
package is an unrelated, currently-empty reserved namespace for a possible future standalone AI
platform module — do not confuse the two.

## 7. Knowledge Centre foundation

`payroll.domain.KnowledgeDocument` + `payroll.application.KnowledgeDocumentService` implement
versioned metadata (never the document body itself) for statutory source documents — an Income Tax
Act section, a CBDT/EPFO/ESIC/PT/LWF circular, or a company payroll policy. Lifecycle:
create (DRAFT, v1) → `createNewVersion()` → `publish()` (supersedes whichever version was
previously published) → `archive()`; `effectiveAsOf()` and `search()` support point-in-time and
free-text lookup. This is a real, working feature today, not a placeholder — it just doesn't yet do
anything AI-specific (no embeddings, no vector search); "foundation" describes its role as the future
grounding-document source for §6, not its current sophistication.

## 8. Tech stack

| Concern            | Choice                                  |
| ------------------- | --------------------------------------- |
| Language            | Java 21                                 |
| Framework           | Spring Boot 3.3                         |
| Build               | Maven                                   |
| Database            | PostgreSQL 16                           |
| Migrations          | Flyway (65 migrations, `V1`–`V65`)      |
| Cache               | Redis 7                                 |
| Auth                | Spring Security + JWT (HS256)           |
| ORM                 | JPA / Hibernate                         |
| API docs            | springdoc-openapi (Swagger UI)          |
| Observability       | Spring Boot Actuator                    |
| Tests               | JUnit 5 + Testcontainers (1,611 tests)  |
| Container runtime   | Docker + Docker Compose                 |

## 9. Repository structure

```
src/main/java/com/ewos
├── EwosApplication.java
├── ai/ analytics/ governance/ talent/     # reserved namespaces — package-info.java only (§2)
├── identity/        tenancy/       employee/      organization/   workflow/
├── attendance/      leave/         payroll/       recruitment/    ats/
├── interview/       offer/         onboarding/    probation/      performance/
├── goals/           competency/    learning/      succession/     exit/
├── dataexchange/    importexport/  integration/   notification/
└── shared/                                        # kernel: AuditableEntity, exceptions, purge jobs
    (each module follows api / application / domain / infrastructure)

src/main/resources
├── application.yml                   # shared defaults
├── application-{dev,test,prod}.yml   # profile overrides
├── logback-spring.xml
└── db/migration/                     # V1__baseline.sql ... V65__attendance_lop_integration.sql

docs/
├── modules/       # one design doc per HRMS module
├── reviews/       # sprint/CTO review packages
├── operations/    # deployment, backup/DR, Flyway conventions, indexing, benchmarks
├── security/      # security checklist
└── adr/           # architecture decision records
```

## 10. Prerequisites

- JDK **21**
- Maven **3.9+**
- Docker **24+** and Docker Compose **v2** (required for `mvn test` — the suite includes
  Testcontainers-backed integration tests; see §13's CI note)

## Quick start (Docker Compose)

Boots app + PostgreSQL + Redis together:

```bash
docker compose up --build
```

Once healthy:

- App:            http://localhost:8080
- Health:         http://localhost:8080/actuator/health
- Swagger UI:     http://localhost:8080/swagger-ui.html
- OpenAPI JSON:   http://localhost:8080/v3/api-docs

Stop and remove volumes:

```bash
docker compose down -v
```

## Local development (no container for the app)

Start Postgres + Redis only:

```bash
docker compose up -d postgres redis
```

Run the app on the `dev` profile:

```bash
./mvnw spring-boot:run
# or
mvn spring-boot:run
```

Package a fat jar:

```bash
mvn clean package
java -jar target/ewos.jar
```

## Configuration

Configuration is layered:

1. `application.yml` — shared defaults
2. `application-{dev,test,prod}.yml` — profile overrides
3. Environment variables — take precedence over everything

Key environment variables:

| Variable                        | Purpose                              | Default (dev)               |
| -------------------------------- | ------------------------------------- | --------------------------- |
| `SPRING_PROFILES_ACTIVE`        | Active Spring profile                | `dev`                        |
| `SPRING_DATASOURCE_URL`         | JDBC URL                             | `jdbc:postgresql://...`      |
| `SPRING_DATASOURCE_USERNAME`    | DB username                          | `ewos`                       |
| `SPRING_DATASOURCE_PASSWORD`    | DB password                          | `ewos`                       |
| `SPRING_DATA_REDIS_HOST`        | Redis host                           | `localhost`                  |
| `SPRING_DATA_REDIS_PORT`        | Redis port                           | `6379`                       |
| `JWT_SECRET`                    | HMAC secret for signing tokens       | placeholder — replace        |
| `JWT_ISSUER`                    | JWT `iss` claim                      | `ewos`                       |
| `JWT_ACCESS_TTL`                | Access-token lifetime                | `15m`                        |
| `JWT_REFRESH_TTL`               | Refresh-token lifetime               | `7d`                         |
| `BANK_ACCOUNT_ENCRYPTION_KEY`   | AES-256 key material for bank fields | placeholder — replace        |
| `ADMIN_USERNAME`                | Default admin username               | `admin`                      |
| `ADMIN_EMAIL`                   | Default admin email                  | `admin@ewos.local`           |
| `ADMIN_PASSWORD`                | Default admin bootstrap password     | `ChangeMe!Admin123`          |
| `APP_CORS_ALLOWED_ORIGINS`      | CSV of allowed CORS origins          | dev-only localhost origins   |
| `PASSWORD_MIN_LENGTH`           | Minimum password length              | `8`                          |
| `PASSWORD_MAX_LENGTH`           | Maximum password length              | `128`                        |
| `PASSWORD_REQUIRE_UPPERCASE`    | Require at least one uppercase       | `true`                       |
| `PASSWORD_REQUIRE_LOWERCASE`    | Require at least one lowercase       | `true`                       |
| `PASSWORD_REQUIRE_DIGIT`        | Require at least one digit           | `true`                       |
| `PASSWORD_REQUIRE_SPECIAL`      | Require at least one special char    | `true`                       |
| `PASSWORD_HISTORY_SIZE`         | Number of past passwords blocked from reuse | `5`                    |
| `AUTH_LOCKOUT_ENABLED`          | Enable account lockout               | `true`                       |
| `AUTH_LOCKOUT_MAX_ATTEMPTS`     | Failed attempts before lockout       | `5`                          |
| `AUTH_RATELIMIT_ENABLED`        | Enable login rate limiting           | `true`                       |

## 11. Production prerequisites — fail-fast by design

Four independent `InitializingBean` guards refuse to let the application context finish starting
outside the `dev`/`test`/`default` profiles if a required secret is missing, too short, or still a
placeholder — this is enforced in code, not just documented:

| Guard | Class | Refuses to boot when… |
|---|---|---|
| JWT secret | `identity.infrastructure.security.jwt.JwtSecretGuard` | `JWT_SECRET` is unset, contains a placeholder marker (`change-me`, `example`, …), or is under 32 bytes |
| Admin password | `identity.application.AdminPasswordGuard` | `ADMIN_PASSWORD` is unset or contains a placeholder marker |
| Bank encryption key | `payroll.infrastructure.crypto.BankAccountEncryptionKeyGuard` | `BANK_ACCOUNT_ENCRYPTION_KEY` is unset, placeholder-looking, or under 32 bytes |
| CORS config | `identity.infrastructure.security.cors.CorsConfig` | `prod` profile has a wildcard (`*`) origin, or a wildcard combined with `allowCredentials=true` |

All four run during Spring context refresh (`afterPropertiesSet()`), strictly before
`IdentityBootstrap` (an `ApplicationRunner`, which only executes once the context has fully
refreshed) ever creates the default admin user — so a placeholder can never reach a running
production database. `dev`/`test` keep their hardcoded, clearly-marked-unsafe defaults (e.g.
`test-secret-key-for-integration-tests-only-do-not-use-in-production-envs` in
`application-test.yml`) with only a warning logged, never a hard failure. Datasource credentials
(`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`) need no separate guard — `application-prod.yml`
supplies no default for them, so Spring Boot's own property resolution fails startup if they're
missing, the same fail-fast outcome by a different, framework-native mechanism.

Flyway (`spring.flyway.enabled: true`) runs automatically on every boot in every profile.

## 12. Health checks

- Liveness:  `GET /actuator/health/liveness`
- Readiness: `GET /actuator/health/readiness`
- Aggregate: `GET /actuator/health`

The Dockerfile wires the container `HEALTHCHECK` to the liveness probe.

## 13. Tests

Integration tests extend `AbstractIntegrationTest`, which spins up a `postgres:16-alpine`
Testcontainer and points the datasource at it. Run the suite with:

```bash
mvn test
```

**Docker must be running on the host** for Testcontainers to work — without it, the ~40
Docker-backed test classes fail with `NoClassDefFoundError`/`ExceptionInInitializerError` on
`AbstractIntegrationTest`'s static initializer while the remaining ~1,600 pure-unit tests still run
and pass normally. That failure signature is the tell for "no Docker here," not a code defect; CI
(GitHub Actions) has Docker and runs the full suite, including a Flyway migration test that applies
all 65 migrations against a fresh schema end-to-end and a full payroll-run HTTP round-trip test.

## 14. Quality gates & CI

Every push to `main`, `claude/**`, `feature/**`, `release/**` — and every PR into `main` — runs
[`.github/workflows/ci.yml`](.github/workflows/ci.yml). The workflow executes `mvn verify`, which
enforces:

| Gate       | Tool                                    | Fails the build on…                                     |
| ---------- | ---------------------------------------- | -------------------------------------------------------- |
| Formatting | Spotless + Google Java Format (AOSP)    | any file not matching the formatter                       |
| Style      | Checkstyle                              | rule violations from `config/checkstyle/checkstyle.xml`   |
| Static     | PMD                                     | rules from `config/pmd/pmd-ruleset.xml`                   |
| Bugs       | SpotBugs                                | Medium+ findings, filtered by `config/spotbugs/spotbugs-exclude.xml` |
| Coverage   | JaCoCo                                  | < **45%** instruction coverage on the bundle (staged toward 80% — see §15) |
| Tests      | JUnit 5 + Testcontainers                | any failing unit or integration test                       |

Local commands:

```bash
mvn spotless:apply       # auto-format
mvn -q verify            # full pipeline (needs Docker for Testcontainers)
mvn -q test              # unit + integration tests (needs Docker for the latter)
```

JaCoCo excludes bootstrap / configuration / DTO / entity / repository interface classes (see
`pom.xml` for the exact list) so the floor targets business logic — services, controllers, security,
and error handling.

### Soft delete

`users`, `roles`, and `permissions` (and most business entities across the HRMS modules) extend
`AuditableEntity` and carry a `deleted_at` column plus a `version` field for optimistic locking.
Hibernate `@SQLDelete` rewrites DELETE statements into `UPDATE … SET deleted_at = NOW()`, and
`@SQLRestriction("deleted_at IS NULL")` transparently filters every query so soft-deleted rows never
leak through. Uniqueness on human-visible columns is enforced by **partial unique indexes** — a
deleted row keeps its historical value without blocking reuse.

### Correlation IDs

`CorrelationIdFilter` (`@Order(HIGHEST_PRECEDENCE)`) reads inbound `X-Request-ID` or mints a UUID,
publishes it back on the response header, and puts it into the SLF4J MDC. Every log line and every
`ApiError` body carries the same id.

### Default administrator

On first boot `IdentityBootstrap` creates a `SYSTEM_ADMIN` user from `app.security.bootstrap.admin.*`
if none exists. In non-dev environments, `AdminPasswordGuard` (§11) refuses to start unless
`ADMIN_PASSWORD` has been overridden; rotate it immediately after first login regardless. The
bootstrap is idempotent — it never touches an existing user.

### Database migrations

Flyway runs on startup from `classpath:db/migration`.

- Naming: `V<version>__<snake_case_description>.sql`
- 65 migrations as of this document, strictly sequential (`V1`–`V65`), no gaps, no duplicates —
  verified this sprint both structurally and by a real `FlywayMigrationValidationTest` run against
  Postgres in CI.
- Migrations are append-only — never edit a merged migration.

## 15. Current roadmap

Tracked in full detail in [`PROJECT_STATUS.md`](./PROJECT_STATUS.md); summarized here:

- **Test coverage**: staged plan already on record — 35% (done, historical) → **45% (current,
  enforced)** → 50% before Beta → 65% before RC → 80% before GA. Raise the `pom.xml` floor only as
  fast as real tests land at each stage, per the existing convention.
- **Payroll V1 completeness**: not yet re-certified after Sprint 24L's gap closure (§1). No new
  Testcontainers-backed integration tests exist yet for the newest Payroll features (maker-checker,
  reopen, attendance LOP, loan recovery) — only unit tests, written in a sandbox without Docker;
  their behavior is otherwise confirmed via CI's Docker-backed run of the pre-existing payroll-run
  integration test, but dedicated new integration tests for these specific features are still owed.
- **Branch/release hygiene**: `main` is the single production baseline as of Sprint 25A; several
  older branches remain on `origin` whose content is already fully superseded by `main` — safe to
  delete when convenient, not yet actioned.
- **Frontend↔backend production wiring**: same-origin relative API paths assume a reverse
  proxy/rewrite the operator must configure per their actual deployment topology — not automatic.
- **Kafka messaging**: present but off by default (`APP_MESSAGING_KAFKA_ENABLED=false`) and untested
  against a real broker outside local Docker Compose.
- **Helm charts**: structurally reviewed, not run through `helm lint`/`helm template` in any sandbox
  used so far (no Helm CLI reachable) — do this before a first real cluster install.
- Reserved namespaces (`ai`, `analytics`, `governance`, `talent`, §2) remain intentionally
  unimplemented; there is no committed timeline for building them out.

## Coding standards

- Package-by-feature under `com.ewos.<context>`; foundation code stays in `identity`, `shared`.
- Constructor injection only; no field injection.
- Records for immutable configuration, DTOs, and value objects.
- No business logic in controllers or repositories.
- All error responses return `ApiError` produced by `GlobalExceptionHandler`.
- Migrations are append-only — never edit a merged migration.
