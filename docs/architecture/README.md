# EWOS Architecture

_Last updated: 2026-07-14 (Sprint 8.1)._

The single-page overview of how the backend is put together. For deep decisions see
[`/docs/adr/`](../adr/); for business rules see [`/docs/business-rules/`](../business-rules/);
for sprint-by-sprint deliverables see [`/docs/sprints/`](../sprints/).

---

## 1. Stack

- **Runtime:** Java 21 on Spring Boot 3.3.
- **Build:** Maven 3.9 with Spotless (Google Java Format AOSP), Checkstyle 10.18, PMD 7.6, SpotBugs 4.8, JaCoCo 0.8.12 (80 % BUNDLE instruction floor) all bound to `verify`.
- **Database:** PostgreSQL 16. Schema owned by Flyway (`spring.jpa.hibernate.ddl-auto=none`). Migrations are strictly append-only.
- **Persistence:** Spring Data JPA / Hibernate 6, `@UuidGenerator` PKs, `@SQLDelete` + `@SQLRestriction("deleted_at IS NULL")` soft delete, `@Version` optimistic locking.
- **Security:** Spring Security stateless. HS256 JWT via JJWT 0.12.6; opaque 48-byte refresh tokens SHA-256 hashed in DB. Method-level `@PreAuthorize`.
- **API docs:** springdoc-openapi 2.6, Swagger UI at `/swagger-ui.html`.
- **Tests:** JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers Postgres (singleton pattern in `AbstractIntegrationTest`).
- **Observability:** Actuator health/info/ready, SLF4J + logback, `CorrelationIdFilter` propagates `X-Request-ID` into MDC and into every `ApiError`.

---

## 2. Package layout — package-by-feature, four layers per module

```
com.ewos
├── EwosApplication              ← main
├── common                       ← platform primitives shared by every module
│   ├── exception/               ← ApiException, ApiError, GlobalExceptionHandler
│   ├── persistence/             ← AuditableEntity, AuditorProvider
│   └── web/                     ← CorrelationIdFilter
├── config/                      ← Spring configuration classes
├── security/                    ← SecurityConfig, JwtService, JwtAuthenticationFilter
├── identity/                    ← Sprint 2, 4, 5 — users, roles, permissions, refresh tokens
│   ├── api / application / domain / infrastructure
├── company/                     ← Sprint 6 — tenants + companies + statutory + banks + policies
├── organization/                ← Sprint 7 — configurable levels, nodes, version history, inheritance
├── person/                      ← Sprint 8.1 — permanent identity, versioned profile, duplicate engine
└── dashboard/                   ← Sprint 8.1.1 — aggregate counters
```

Every business module follows the same four-layer split, no exceptions:

- **`api`** — REST controllers + request/response DTOs (Java `record`s). This layer is the ONLY
  place framework annotations for HTTP or Swagger appear.
- **`api/dto`** — request/response records with `jakarta.validation` constraints and
  `@Schema` annotations. No JPA entity ever crosses the API boundary.
- **`application`** — services orchestrating business rules; `@Transactional` boundaries live here.
  Mappers are package-private. Services depend on repositories, never on other modules' services
  without an ADR.
- **`domain`** — JPA entities + enums. No Spring annotations except JPA + Hibernate ones. Extends
  `AuditableEntity` for auditor fields + UUID PK.
- **`infrastructure/persistence`** — Spring Data JPA `interface … extends JpaRepository`, plus
  optional `Specifications` classes for dynamic filtering.

**Cross-module rule.** A module may only reference another module's `domain` for a foreign-key
target (e.g. `person.domain.Person` references `company.domain.Tenant`). No cross-module service
imports without an ADR.

---

## 3. Persistence patterns

### 3.1 Auditing (every entity)
`AuditableEntity` contributes `id UUID` + `created_at` / `updated_at` / `created_by` / `updated_by`.
The `AuditorProvider` reads the JWT subject and populates the "by" columns automatically.

### 3.2 Soft delete
`@SQLDelete("UPDATE … SET deleted_at = NOW(), version = version + 1 WHERE id = ?")` +
`@SQLRestriction("deleted_at IS NULL")`. Uniqueness that must survive soft-delete uses
**partial unique indexes** (`WHERE deleted_at IS NULL`), never table-level `UNIQUE`.

### 3.3 Optimistic locking
`@Version` on every mutable entity. Conflicts surface as `409 CONFLICT` via
`GlobalExceptionHandler.handleOptimisticLocking`.

### 3.4 Effective-dated versioning (master + version pattern)
Used by Company profile (Sprint 6), Organization node history (Sprint 7), and Person profile
(Sprint 8.1). Rules:

- Master row holds only identity + status.
- Version rows are immutable except for `effective_to`.
- A change closes the current row (`effective_to = newFrom - 1`) and appends a new open row.
- Partial unique index `WHERE effective_to IS NULL` guarantees at most one open row per key.
- `?asOf=YYYY-MM-DD` reads select the row whose window covers the date.

### 3.5 Effective-dated child rows (single-row-with-window)
Used by contacts, addresses, identity documents, policy assignments, statutory registrations,
bank accounts. Same window semantics but no version-number chain — the row IS the fact.

### 3.6 Sequences for user-facing identifiers
Postgres `SEQUENCE` (monotonic, `NO CYCLE`) generates human-readable IDs (`P000000001` for Person).
Rollbacks consume numbers so IDs can never be reused.

---

## 4. Request/response conventions

- Versioned prefix: `/api/v1/<resource>/…` (single exception: `/api/dashboard/summary`, delivered
  under the literal path requested in the sprint brief — flagged in the Sprint 8.1 review §16.1).
- Status codes: 201 create, 200 read/update, 204 delete, 400 validation, 401 unauth, 403 forbidden,
  404 missing, 409 conflict.
- Every error response is an `ApiError` envelope carrying `timestamp`, `status`, `error`,
  `message`, `path`, and `correlationId`.
- Pagination: `Pageable` with `@PageableDefault(size = 20, sort = …)`. No endpoint returns an
  unbounded list of user-generated rows.
- Every mutating endpoint carries `@PreAuthorize("hasAuthority('…')")`. No bare `isAuthenticated()`
  on writes.
- Every endpoint carries `@Operation(summary=…)` and `@ApiResponses` covering each non-2xx it can
  produce.

---

## 5. Security architecture

- **Authentication:** stateless JWT. `JwtAuthenticationFilter` validates the Bearer token and
  hydrates `GrantedAuthority`s from the `authorities` claim.
- **Authorisation:** authority-based (`hasAuthority('X_READ')`), not role-based. Permissions are
  seeded via Flyway (V3, V6, V7, V8, V9) and mirrored in `com.ewos.identity.domain.Permissions`.
- **Bootstrap admin:** created by `IdentityBootstrap` at first boot from
  `app.security.bootstrap.admin.*` — password must be changed via `/change-password`.
- **Refresh tokens:** 48-byte `SecureRandom` Base64Url values, SHA-256 hashed in DB, rotated on
  every use, revocable at logout, always audited.
- **Login history:** every attempt (including unknown-username) written in a `REQUIRES_NEW`
  transaction so failures survive outer rollbacks.
- **Correlation ID:** `CorrelationIdFilter` at `Ordered.HIGHEST_PRECEDENCE` reads or mints
  `X-Request-ID`, publishes it on the response, and stamps it into SLF4J MDC.
- **Multi-tenancy:** every tenant-scoped entity carries `tenant_id`; query-time enforcement of the
  SHARED/SEGREGATED isolation policy is deferred to Sprint 6.1 (documented, tracked).

---

## 6. Cross-module dependency map (as of Sprint 8.1)

```
identity   ─┐
            ├─ (no upward deps)
company    ─┤   (depends on identity for Permissions constants only)
            ├─ Tenant → referenced by:
organization┤       │
person     ─┤       ├─ organization.OrganizationLevel.tenant
dashboard  ─┤       ├─ organization.OrganizationNode.tenant
            │       ├─ person.Person.tenant
            │       └─ person.PersonDuplicateRule.tenant
            │
            └─ dashboard/DashboardCountsRepository queries the users and roles tables
              directly via native SQL (single-round-trip aggregate).
```

Everything else is inside-a-module. No module reaches into another module's `application` or
`api` package.

---

## 7. Testing architecture

- **Unit tests** — `@ExtendWith(MockitoExtension.class)`, mock repositories, exercise service
  logic. No Spring context.
- **Integration tests** — extend `AbstractIntegrationTest`. `@SpringBootTest` +
  `@AutoConfigureMockMvc`. Testcontainers Postgres is started once at class-load time
  (`static { POSTGRES.start(); }`) and reused across every integration test class in the JVM —
  Ryuk cleans it up at exit. Never call `.stop()`.
- **CI** — GitHub Actions runs `mvn -B -ntp verify` on every push and PR. Green requires all
  quality gates + JaCoCo ≥ 80 % BUNDLE instruction coverage.

---

## 8. Where to look next

- [`/docs/adr/`](../adr/) — architecture decisions (currently 3).
- [`/docs/business-rules/`](../business-rules/) — enumerated business rules per module + their
  enforcement points.
- [`/docs/reviews/CTO_REVIEW_CHECKLIST.md`](../reviews/CTO_REVIEW_CHECKLIST.md) — mandatory
  pre-merge checklist.
- [`/docs/reviews/sprint-<N>-cto-review.md`](../reviews/) — completed sprint review packages.
- [`/docs/sprints/`](../sprints/) — per-sprint index pointing at each sprint's ADR, business
  rules, and review package.
- [`/PROJECT_STATUS.md`](../../PROJECT_STATUS.md) — the always-current single source of truth for
  what is delivered, what's on the debt list, and how to run.
