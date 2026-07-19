# WP-001 — Platform Foundation — CTO Review Package

- **Date:** 2026-07-14
- **Work package:** WP-001 — Platform Foundation
- **Delivered by:** Backend lead
- **Reviewer (blocking):** CTO
- **Repository:** `buntychavan-web/EWOS`
- **Head commit for review:** `main` @ `c63947c` (already merged) — plus the follow-up CORS/security PRs currently open for CTO adjudication (see §14)

---

## 0. Reviewer stance — read this first

- Every implementation decision recorded in ADR-0001 … ADR-0004 is submitted as a **proposal**. Any decision the CTO overturns will be reverted or amended before further work packages start.
- **No new architecture decisions** were made in preparing this delivery. This document only assembles what already exists into a WP-001-shaped review artifact.
- Per instruction, I am **not proceeding to WP-002** until the CTO records a verdict on this package.

Where a design choice matches the WP-001 brief exactly, this document says so plainly; where it exceeds the WP-001 brief (e.g. user CRUD, business modules), that surface is called out in §10 as **out of WP-001 scope** so it does not muddy the review.

---

## 1. WP-001 scope — line-by-line coverage

| WP-001 requirement | Where delivered | Status |
|---|---|---|
| Java 21 | `pom.xml` `<java.version>21</java.version>` | ✅ |
| Spring Boot 3.3 | `pom.xml` `spring-boot-starter-parent 3.3.5` | ✅ |
| PostgreSQL | `docker-compose.yml` (`postgres:16-alpine`) + `spring.datasource.*` | ✅ |
| Flyway | `pom.xml` (`flyway-core` + `flyway-database-postgresql`) + `src/main/resources/db/migration/V1..V5__*.sql` (foundation) | ✅ |
| Spring Security | `com.ewos.security.SecurityConfig` — stateless, CSRF off, session `STATELESS`, method-security enabled | ✅ |
| JWT | `com.ewos.security.jwt.JwtService`, `JwtAuthenticationFilter`, `JwtProperties` (JJWT 0.12.6, HS256) | ✅ |
| Redis | `spring-boot-starter-data-redis` + `application.yml` `spring.data.redis.*` (config wired; not yet used at runtime — flagged in §11) | ✅ (config) |
| Docker | multi-stage `Dockerfile` + `docker-compose.yml` (app + Postgres + Redis, healthchecked) | ✅ |
| Maven | `pom.xml` with quality-gate plugins bound to `verify` | ✅ |
| JUnit | JUnit 5, Mockito, AssertJ, Spring MockMvc, Testcontainers Postgres | ✅ |
| Authentication | `POST /api/v1/auth/login` / `/refresh` / `/logout` | ✅ |
| RBAC | `permissions` + `roles` + `role_permissions` + `user_roles` tables, `@PreAuthorize("hasAuthority(...)")` on every mutating endpoint, `com.ewos.identity.domain.Permissions` constants | ✅ |
| Audit framework | `AuditableEntity` (`@CreatedDate` / `@LastModifiedDate` / `@CreatedBy` / `@LastModifiedBy`), `AuditorProvider` (reads JWT subject), `login_history` table + `LoginHistoryRecorder` (in a `REQUIRES_NEW` tx so failures survive rollback), `X-Request-ID` correlation via `CorrelationIdFilter` | ✅ |
| Health check | Spring Boot Actuator — `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`, `/actuator/metrics` | ✅ |
| CI-ready | `.github/workflows/ci.yml` runs `mvn -B -ntp verify` on every push + PR; gates: Spotless (Google Java Format AOSP), Checkstyle 10.18, PMD 7.6, SpotBugs 4.8, JaCoCo 0.8.12 (80 % BUNDLE instruction floor) | ✅ |

Every WP-001 line item is delivered.

---

## 2. Architecture

**Style:** package-by-feature, four layers per bounded context (`api`, `application`, `domain`, `infrastructure`). Foundation layer lives under `com.ewos.{common,config,security,identity}`.

```
com.ewos
├── EwosApplication              ← @SpringBootApplication entry point
├── common
│   ├── exception/               ← ApiException, ApiError, GlobalExceptionHandler
│   ├── persistence/             ← AuditableEntity, AuditorProvider
│   └── web/                     ← CorrelationIdFilter (Ordered.HIGHEST_PRECEDENCE)
├── config/                      ← Spring configuration
├── security/                    ← SecurityConfig, JwtService, JwtAuthenticationFilter, JwtProperties
└── identity/
    ├── api/                     ← AuthController, UserController + DTOs
    ├── application/             ← AuthenticationService, UserService, PasswordPolicyValidator, PasswordHistoryService, IdentityBootstrap
    ├── domain/                  ← User, Role, Permission, RefreshToken, PasswordHistoryEntry, LoginHistoryEntry, Permissions constants
    └── infrastructure/persistence/ ← UserRepository, RoleRepository, PermissionRepository, RefreshTokenRepository, LoginHistoryRepository, PasswordHistoryRepository, UserSpecifications
```

**Cross-module rules established here:**
- Entities never cross the API boundary — DTOs are `record`s under `<module>.api.dto`.
- `application` services own `@Transactional` boundaries.
- `domain` has no Spring annotations except JPA + Hibernate.
- No module reaches into another module's `application` or `api` — only the shared `common` primitives are transverse.

Full architecture write-up: [`docs/architecture/README.md`](../architecture/README.md).

---

## 3. Database — foundation migrations (V1 – V5)

| File | Purpose |
|---|---|
| `V1__baseline.sql` | Postgres extensions (`pgcrypto`, `citext`) |
| `V2__create_identity_tables.sql` | `users`, `roles`, `permissions`, `user_roles`, `role_permissions`, `refresh_tokens` |
| `V3__seed_default_roles_permissions.sql` | `SYSTEM_ADMIN` role + default `USER_*` / `ROLE_*` permissions + role_permissions grants |
| `V4__user_management.sql` | `password_history`, `login_history`, `created_by`/`updated_by` on identity tables, `password_changed_at` on users *(user CRUD lives beyond WP-001 scope — but the audit tables belong here, so V4 straddles both)* |
| `V5__soft_delete_and_versioning.sql` | `deleted_at` + `@Version` columns on users/roles/permissions + partial unique indexes so soft-deleted rows don't block reuse |

**Migration discipline:** append-only, `ddl-auto=none`, Flyway owns the schema, no edits to prior V-files.

**Effective-dated / soft-delete pattern conventions** are documented in the ADRs (each covering the module that first used them) — the CTO may adjust these before further modules land.

---

## 4. Authentication flow

```
Client                                 EWOS
   │                                    │
   │ 1. POST /api/v1/auth/login         │
   │    {username, password}            │
   │───────────────────────────────────►│  bcrypt verify → issue tokens
   │◄───────────────────────────────────│
   │ 200 { accessToken (JWT, 15m),      │
   │       refreshToken (opaque, 7d),   │
   │       tokenType: "Bearer",         │
   │       expiresIn: 900 }             │
   │                                    │
   │ 2. Secured call                    │
   │    Authorization: Bearer <access>  │
   │───────────────────────────────────►│  JwtAuthenticationFilter validates,
   │◄───────────────────────────────────│  loads GrantedAuthority list from
   │ 200 …                              │  the "authorities" claim
   │                                    │
   │ 3. POST /api/v1/auth/refresh       │  Rotate: revoke old, mint new
   │◄───────────────────────────────────│
   │                                    │
   │ 4. POST /api/v1/auth/logout        │  Revoke presented refresh
   │◄───────────────────────────────────│  204 (idempotent)
```

**Access token.** HS256 JWT. Default TTL 15 minutes. Claims: `sub` = user id, `authorities` = permission codes, `iss` = `${JWT_ISSUER}`, `iat`, `exp`.
**Refresh token.** 48-byte `SecureRandom` Base64Url (approx 64 chars). Default TTL 7 days. Stored SHA-256-hashed in `refresh_tokens`. Rotated on every use.
**Bootstrap admin.** `IdentityBootstrap` (ApplicationRunner, idempotent) creates the default admin from `app.security.bootstrap.admin.*`. Must be changed on first login.

Public paths (`SecurityConfig.PUBLIC_ENDPOINTS`):
`/actuator/health`, `/actuator/health/**`, `/actuator/info`, `/v3/api-docs`, `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**`, `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout`.

---

## 5. RBAC

Authority-based, not role-based. `hasAuthority('X_READ')` on every mutating endpoint.

**Schema:**
```
users ─┬─ user_roles ─┬─ roles ─┬─ role_permissions ─┬─ permissions
       │              │         │                     │
       └─ (many-to-many via user_roles)               └─ code (string)
```

**Foundation permissions (V3 seed):** `SYSTEM_ADMIN`, `USER_READ`, `USER_WRITE`, `USER_DELETE`, `ROLE_READ`, `ROLE_WRITE`.
All granted to `SYSTEM_ADMIN` role by seed.

**Enforcement pattern:**
```java
@PostMapping
@PreAuthorize("hasAuthority('USER_WRITE')")
public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest req) { … }
```

**Constants:** `com.ewos.identity.domain.Permissions` — used as compile-time-checked references from `@PreAuthorize` SpEL.

---

## 6. Audit framework

Three layers.

### 6.1 Row-level (every entity)
`AuditableEntity` (`@MappedSuperclass`, `@EntityListeners(AuditingEntityListener.class)`) contributes:
- `id UUID` (`@UuidGenerator`)
- `created_at`, `updated_at` (`@CreatedDate` / `@LastModifiedDate`)
- `created_by`, `updated_by` (`@CreatedBy` / `@LastModifiedBy`)

`AuditorProvider` reads the JWT subject from `SecurityContextHolder` and populates the "by" columns automatically.

### 6.2 Optimistic locking
`@Version` on every mutable entity. Conflicts surface as `409 CONFLICT` via `GlobalExceptionHandler.handleOptimisticLocking`.

### 6.3 Business-event audit (login history)
`LoginHistoryRecorder` writes to `login_history` on every login attempt (including unknown-username) in a `REQUIRES_NEW` transaction so failures survive outer rollbacks. Event types: `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `LOGOUT`, `REFRESH_SUCCESS`, `REFRESH_FAILURE`. Captures IP + User-Agent.

### 6.4 Correlation ID
`CorrelationIdFilter` (Ordered.HIGHEST_PRECEDENCE) reads or mints `X-Request-ID`, echoes it on the response, and stamps it into SLF4J MDC. Every `ApiError` response body carries `correlationId`.

---

## 7. Health check

**Actuator surface:**

| Path | Auth | Purpose |
|---|---|---|
| `/actuator/health` | Public | Overall UP/DOWN |
| `/actuator/health/liveness` | Public | K8s liveness probe |
| `/actuator/health/readiness` | Public | K8s readiness probe |
| `/actuator/info` | Public | Build info |
| `/actuator/metrics` | Authenticated | Micrometer registry |

Prod profile locks the exposure list to `health,info` in `application-prod.yml`.

**Docker healthcheck.** `docker-compose.yml` health-checks Postgres and Redis before starting the app.

---

## 8. CI + quality gates

`.github/workflows/ci.yml`:

- Trigger: every push + every PR.
- Java 21 (Temurin), Maven cached.
- Command: `mvn -B -ntp verify`.

Gates bound to `verify` in `pom.xml`:

| Gate | Version | Enforcement |
|---|---|---|
| Spotless (Google Java Format AOSP) | 2.43.0 | `spotless:check` fails on format drift |
| Checkstyle | 10.18 | `checkstyle:check` — 0-tolerance |
| PMD | 7.6 | `pmd:check` — 0-tolerance |
| SpotBugs | 4.8 | `spotbugs:check` — 0-tolerance |
| JaCoCo | 0.8.12 | 80 % instruction coverage BUNDLE floor; excludes bootstrap / config / DTO / entity / repo-interface packages so the floor targets business logic |

Test infrastructure — singleton Testcontainers Postgres in `com.ewos.AbstractIntegrationTest` (documented in ADR-… debt entry and in `CONTRIBUTING.md § 6.4`). All integration tests extend this class.

---

## 9. Test results (local, sandbox — 2026-07-14)

`mvn spotless:check checkstyle:check pmd:check compile spotbugs:check` → **BUILD SUCCESS**.

**Unit tests (foundation surface only):**

| Suite | Tests | Status |
|---|---|---|
| `AuthenticationServiceTest` | 12 | ✅ |
| `UserServiceTest` | 12 | ✅ |
| `PasswordPolicyValidatorTest` | 7 | ✅ |
| `JwtServiceTest` | 2 | ✅ |
| **Foundation total** | **33 / 33** | ✅ |

**Integration tests (foundation surface):**
- `AuthControllerIntegrationTest` — 5 tests (login / bad creds / refresh rotation / logout / unknown refresh).
- `UserControllerIntegrationTest` — 15 tests (CRUD + soft delete + reuse + change-password + validation + correlation header).
- `EwosApplicationTests` — 1 (context load).

Integration tests run on CI (Docker available); the review sandbox has no Docker daemon so they are not executed here. The last successful CI run for this exact test infrastructure is documented in PR #4.

**Coverage.** JaCoCo BUNDLE floor of 80 % holds on CI. Excluded packages target boilerplate; measured surface is services + controllers + specifications. A fresh CI run is required to attach the exact snapshot number to this review.

---

## 10. What is IN vs OUT of WP-001 scope

To keep the CTO review focused, I've drawn the line clearly.

### IN WP-001 (this review)
- Foundation setup (Sprint 1)
- Identity + auth (Sprint 2)
- Audit framework + soft delete + `@Version` + logout + correlation IDs + CI quality gates (Sprint 5 hardening)
- Testcontainers singleton pattern (CI fix branch, PR #4)

### OUT of WP-001 (adjacent work, listed for CTO awareness)
- **User management CRUD** (Sprint 4) — full user-management REST surface (create, update, enable/disable, reset/change password, paged search). Adjacent to identity but goes beyond "foundation". *Recommendation: package as WP-002 (User Management) for a separate review.*
- **Business modules:** Sprint 6 (Company Configuration), Sprint 7 (Organization Structure), Sprint 8.1 (Person Engine), Sprint 8.1.1 (Dashboard) — all live on `claude/repository-selection-575dn9`, un-PRed. These are business-domain work packages, not foundation.
- **CORS configuration** (PR #7, `claude/cors-configuration`) — arguably foundation, delivered as a follow-up because the initial foundation didn't wire `CorsConfigurationSource`. **CTO to decide whether this belongs to WP-001 or its own follow-up.**
- **Process docs** (PR #5) — `CONTRIBUTING.md`, PR template, Common Pitfalls playbook. Not code; recommend accepting alongside WP-001.

---

## 11. Known limitations of WP-001 as delivered

Each of these is a candidate topic for a CTO override.

**Blocking for prod (recommend WP-001 does NOT get "shippable" sign-off without addressing these):**
1. **`JWT_SECRET` placeholder default** — app still boots with the dev placeholder. Should refuse to start when the placeholder is present outside `dev`/`test` profiles.
2. **CORS not wired in `main`** — PR #7 fixes this on a follow-up branch; if the CTO wants a browser-usable foundation, this PR must be part of WP-001 delivery.
3. **No rate limiting / account lockout on `/api/v1/auth/*`** — `login_history` captures attempts, but there is no throttling.

**Acceptable-with-follow-up (deferred by prior ADRs):**
4. Actuator `/prometheus`, `/env`, `/metrics` are protected but the exposure list is prod-locked to `health,info` — enabling `prometheus` needs a documented auth policy.
5. Refresh tokens are per-token revocable but not device-family-bound.
6. No purge job for soft-deleted rows.

**Trivial (defensible either way):**
7. `AuditorProvider` returns `Optional.empty()` when no JWT is present — `created_by` / `updated_by` end up NULL on system-initiated writes (bootstrap admin creation). Documented behaviour.

---

## 12. Files delivered — exhaustive map

Grouped by where they live. Every path is real; every path is on `main` (or on the follow-up branches in §10, called out).

### Root
- `pom.xml`, `Dockerfile`, `docker-compose.yml`, `README.md`, `PROJECT_STATUS.md`, `.github/workflows/ci.yml`, `config/checkstyle/checkstyle.xml`, `config/pmd/pmd-ruleset.xml`, `config/spotbugs/spotbugs-exclude.xml`

### Migrations
- `src/main/resources/db/migration/V1__baseline.sql`
- `src/main/resources/db/migration/V2__create_identity_tables.sql`
- `src/main/resources/db/migration/V3__seed_default_roles_permissions.sql`
- `src/main/resources/db/migration/V4__user_management.sql`
- `src/main/resources/db/migration/V5__soft_delete_and_versioning.sql`

### Application entry & config
- `src/main/java/com/ewos/EwosApplication.java`
- `src/main/resources/application.yml`, `application-dev.yml`, `application-test.yml`, `application-prod.yml`, `logback-spring.xml`

### Common
- `src/main/java/com/ewos/common/exception/ApiException.java`
- `src/main/java/com/ewos/common/exception/ApiError.java`
- `src/main/java/com/ewos/common/exception/GlobalExceptionHandler.java`
- `src/main/java/com/ewos/common/persistence/AuditableEntity.java`
- `src/main/java/com/ewos/common/persistence/AuditorProvider.java`
- `src/main/java/com/ewos/common/web/CorrelationIdFilter.java`

### Security
- `src/main/java/com/ewos/security/SecurityConfig.java`
- `src/main/java/com/ewos/security/jwt/JwtService.java`
- `src/main/java/com/ewos/security/jwt/JwtAuthenticationFilter.java`
- `src/main/java/com/ewos/security/jwt/JwtProperties.java`

### Identity — domain & persistence
- `src/main/java/com/ewos/identity/domain/{User,Role,Permission,RefreshToken,PasswordHistoryEntry,LoginHistoryEntry,LoginEventType,Permissions}.java`
- `src/main/java/com/ewos/identity/infrastructure/persistence/{UserRepository,RoleRepository,PermissionRepository,RefreshTokenRepository,PasswordHistoryRepository,LoginHistoryRepository,UserSpecifications}.java`

### Identity — application & API
- `src/main/java/com/ewos/identity/application/{AuthenticationService,UserService,PasswordPolicyValidator,PasswordHistoryService,PasswordPolicyProperties,BootstrapProperties,IdentityBootstrap,LoginHistoryRecorder}.java`
- `src/main/java/com/ewos/identity/api/{AuthController,UserController}.java`
- `src/main/java/com/ewos/identity/api/dto/{LoginRequest,RefreshRequest,TokenResponse,CreateUserRequest,UpdateUserRequest,UserResponse,ChangePasswordRequest,ResetPasswordRequest,StatusRequest,RoleSummary,UserSearchCriteria}.java`

### Tests
- `src/test/java/com/ewos/AbstractIntegrationTest.java` (singleton Testcontainers pattern)
- `src/test/java/com/ewos/EwosApplicationTests.java`
- `src/test/java/com/ewos/identity/application/{AuthenticationServiceTest,UserServiceTest,PasswordPolicyValidatorTest}.java`
- `src/test/java/com/ewos/security/jwt/JwtServiceTest.java`
- `src/test/java/com/ewos/identity/api/{AuthControllerIntegrationTest,UserControllerIntegrationTest}.java`

---

## 13. Verification steps for the CTO

Recommended review flow (est. 30–45 minutes):

1. **Boot locally.** `docker compose up --build`. Watch for `Started EwosApplication`. Hit `curl http://localhost:8080/actuator/health` → expect `{"status":"UP", ...}`.
2. **Login round-trip.** `curl -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"ChangeMe!Admin123"}'` → expect a `TokenResponse`. Copy `accessToken`.
3. **Smoke a secured call.** `curl http://localhost:8080/api/v1/users -H "Authorization: Bearer $TOKEN"` → expect a paged empty result.
4. **Fail a forbidden call.** Same call without the header → expect `401` with an `ApiError` envelope carrying `correlationId`.
5. **Check migrations applied.** `docker exec … psql -d ewos -c '\dt'` → expect `flyway_schema_history`, `users`, `roles`, `permissions`, `user_roles`, `role_permissions`, `refresh_tokens`, `login_history`, `password_history`.
6. **Run gates.** `mvn -B -ntp verify` in a machine with Docker → expect all gates green, JaCoCo BUNDLE ≥ 80 %.

Or, if you'd rather review the artifacts than boot the app:

- **Config:** `src/main/resources/application.yml` (72 lines)
- **Security:** `com.ewos.security.SecurityConfig` (82 lines)
- **Auth service:** `com.ewos.identity.application.AuthenticationService`
- **Audit contract:** `com.ewos.common.persistence.AuditableEntity` + `AuditorProvider`
- **ApiError envelope:** `com.ewos.common.exception.ApiError` + `GlobalExceptionHandler`
- **CI:** `.github/workflows/ci.yml`

---

## 14. Open PRs relevant to WP-001

For the CTO's convenience, the four open PRs and their relationship to WP-001:

| # | Title | WP-001 relevance | Recommendation |
|---|---|---|---|
| #4 | Fix CI: singleton Testcontainers | **Foundation** — CI stability | Merge; test infrastructure will not otherwise pass reliably |
| #5 | Docs & process | **Foundation** — engineering discipline | Merge; docs-only, no runtime risk |
| #6 | Sprint 6 Company Configuration | **Business module (not WP-001)** | Do not merge until WP-001 is accepted; then package as its own WP |
| #7 | Production-ready CORS | **Foundation, follow-up** | CTO decides: (a) fold into WP-001 and merge with it, or (b) keep as WP-001.1 follow-up |

`claude/repository-selection-575dn9` (un-PRed) carries Sprints 7 + 8.1 + 8.1.1 + docs. All business modules or documentation — none are WP-001 material.

---

## 15. What I need from the CTO

Please respond with one of:

- **APPROVED** — WP-001 accepted as delivered. I proceed to package WP-002 (recommended: User Management, currently in Sprint 4 code).
- **APPROVED WITH CONDITIONS** — list the conditions; I address each on this branch, re-verify, and re-submit before starting WP-002.
- **REJECTED — RESTART** — clean-slate rebuild required. I create a fresh branch off `main` and rebuild only the WP-001 surface per the CTO's specifications.
- **PARTIAL** — enumerate the parts of §1 that are accepted vs. rejected. I keep the accepted parts and rebuild the rejected parts.

Per the standing instruction, **I do not proceed to any next work package until this verdict lands.**

---

## Appendix — ADRs open for CTO adjudication

The following ADRs record decisions made under my authority to date. Any of them may be overturned by the CTO's WP-001 verdict.

- [ADR-0001](../adr/0001-company-effective-dating-and-tenancy.md) — Company effective-dating and tenancy model
- [ADR-0002](../adr/0002-organization-structure-engine.md) — Organization structure engine (level-as-data, walk-up inheritance)
- [ADR-0003](../adr/0003-person-engine.md) — Person engine (sequence-backed ID, effective-dated profile, data-driven duplicate detection)
- [ADR-0004](../adr/0004-cors-configuration.md) — CORS configuration (deny-by-default in prod, wildcards rejected)

ADRs 0001–0003 govern out-of-scope business modules; the CTO may leave them for their respective work-package reviews. ADR-0004 is directly relevant to WP-001 if PR #7 is folded in.
