# EWOS — Project Status

_Last updated: 2026-07-13._

Snapshot of the backend after Sprints 1, 2, 4, 5 (hardening), 6
(Company Configuration), 7 (Organization Structure), and 8.1 (Person
Engine). This is the single source of truth for what is delivered,
what quality gates are enforced, and what technical debt still exists.

---

## 1. Delivered by sprint

### Sprint 1 — foundation
- Spring Boot 3.3 on Java 21, Maven build.
- PostgreSQL 16 + Flyway (V1 baseline extensions).
- Spring Data Redis (config only).
- Spring Security stateless config (CSRF off, session `STATELESS`).
- JWT primitives (`JwtService`, `JwtAuthenticationFilter`, `JwtProperties`).
- Actuator liveness / readiness / health.
- springdoc-openapi + Swagger UI.
- Profiles `dev` / `test` / `prod`; `logback-spring.xml` with prod rolling file appender.
- Global `ApiError` + `GlobalExceptionHandler`.
- Multi-stage Dockerfile, `docker-compose.yml` for app + Postgres + Redis (healthchecked).
- `AbstractIntegrationTest` on Testcontainers Postgres; `contextLoads` smoke test.

### Sprint 2 — identity module
- Flyway V2 (schema) + V3 (seed permissions + `SYSTEM_ADMIN` role).
- Entities: `Permission`, `Role`, `User`, `RefreshToken` under `com.ewos.identity.domain`.
- Spring Data repositories with `existsByUsername` / `existsByEmail` / `findByTokenHash`.
- `POST /api/v1/auth/login` — BCrypt verify → JWT access + opaque refresh.
- `POST /api/v1/auth/refresh` — rotates refresh tokens (old is revoked before the new pair is minted).
- Access tokens carry `authorities` (`ROLE_<name>` + permission codes); `JwtAuthenticationFilter` hydrates `GrantedAuthority` from the claim.
- `IdentityBootstrap` (`ApplicationRunner`, idempotent) creates the default admin from `app.security.bootstrap.admin.*`.
- Refresh tokens are 48-byte `SecureRandom` Base64Url values stored SHA-256-hashed.

### Sprint 4 — user management
- Flyway V4 — `password_history`, `login_history`, `created_by` / `updated_by` on identity tables, `password_changed_at` on users.
- `AuditableEntity` — `@CreatedDate` / `@LastModifiedDate` / `@CreatedBy` / `@LastModifiedBy` via Spring Data auditing; `AuditorProvider` reads the JWT subject.
- Configurable password policy (`PasswordPolicyProperties` + `PasswordPolicyValidator`) — length min/max, upper/lower/digit/special toggles, history size.
- `PasswordHistoryService` blocks reuse of the last N BCrypt hashes.
- `LoginHistoryRecorder` writes every login attempt (including unknown-username) in a `REQUIRES_NEW` transaction so failures aren't lost to outer rollback.
- `UserService` — create, update, setEnabled, resetPassword, changePassword, getById, `Specification`-driven search with `Pageable`.
- `UserController` — full user-management REST surface with springdoc annotations and method-level `@PreAuthorize`.

### Sprint 5 — hardening
- **CI**: `.github/workflows/ci.yml` runs Spotless, Checkstyle, PMD, SpotBugs, and full `mvn verify` on every push and PR.
- **Static analysis**: Spotless (Google Java Format AOSP), Checkstyle 10.18, PMD 7.6, SpotBugs — all bound to the `verify` phase and currently clean.
- **JaCoCo**: 80 % instruction-coverage floor on the BUNDLE (excludes bootstrap / config / DTO / entity / repository interface classes so the floor targets business logic).
- **Soft delete**: users / roles / permissions carry `deleted_at` + `version`; entities use `@SQLDelete` + `@SQLRestriction("deleted_at IS NULL")`; full-column UNIQUE constraints replaced with partial unique indexes so a soft-deleted row keeps its identifiers without blocking reuse.
- **Optimistic locking**: `@Version` on all three entities; concurrent-modification conflicts surface as `409 Conflict` via a `OptimisticLockingFailureException` handler.
- **Logout**: `POST /api/v1/auth/logout` — idempotent, always `204`, revokes the presented refresh token, records a `LOGOUT` audit event.
- **Login audit**: new `LoginEventType` enum (`LOGIN_SUCCESS`, `LOGIN_FAILURE`, `LOGOUT`, `REFRESH_SUCCESS`, `REFRESH_FAILURE`); refresh attempts audited with distinct failure reasons (`revoked` / `expired` / `unknown`); IP + User-Agent captured on refresh + logout too.
- **API consistency**: `CorrelationIdFilter` (HIGHEST_PRECEDENCE) reads/mints `X-Request-ID`, publishes it on the response, and places it in the SLF4J MDC. `ApiError` carries a `correlationId` field. `GlobalExceptionHandler` gained handlers for `HttpMessageNotReadable`, `MissingServletRequestParameter`, `MethodArgumentTypeMismatch`, `HttpRequestMethodNotSupported`, `NoResourceFound`, and `OptimisticLockingFailure` — every 4xx/5xx now returns the same `ApiError` shape.
- **README**: badges + `Quality gates & CI` section + endpoint updates + soft-delete / correlation-ID / login-audit docs.

### Sprint 6 — Company Configuration
- Flyway V6 — `tenants`, `companies`, `company_versions`, `statutory_registrations`, `company_bank_accounts`, `company_policy_assignments`, `company_shared_services`; seeds a `DEFAULT` tenant and the `COMPANY_READ` / `COMPANY_WRITE` / `COMPANY_DELETE` permissions granted to `SYSTEM_ADMIN`.
- **Effective-dated profile**: `company_versions` holds every profile snapshot. A profile edit closes the previous window and opens a new row; historical rows are immutable. Enforced by `ux_company_versions_open` (partial unique index) + service-layer validation.
- **Statutory registrations**: PAN / TAN / GST / PF / ESIC / PT / LWF, each with its own effective window; PAN is nationally unique across live rows.
- **Bank accounts**: multiple per company, keyed by purpose (SALARY / FULL_AND_FINAL / REIMBURSEMENT / STATUTORY / VENDOR / OTHER).
- **Policy assignments**: references-only (`policy_ref UUID`) to future policy tables (holiday calendar, payroll calendar, leave / attendance / shift, workflow). Non-overlap enforced per `(company, policy_type)`.
- **Shared services**: HR / Payroll / Finance / IT team assignments, effective-dated.
- **Clone**: `POST /api/v1/companies` accepts `cloneFromId` + optional `clonePolicyTypes` — blank / structure-only / reference-existing-policies modes. Statutory numbers and bank account numbers are never cloned (per-company entry required).
- **Soft delete + @Version + audit** on every entity that extends `AuditableEntity`.
- **APIs** at `/api/v1/companies/*`: create/get/search/update-profile/status/soft-delete + `/versions` + `/statutory-registrations` + `/bank-accounts` + `/policy-assignments` + `/shared-services`, each with its retire endpoint. Full springdoc annotations. Search supports paging / sorting / filtering.
- **Tenant model**: `tenants` table with `isolation_policy ∈ {SHARED, SEGREGATED}` seeded to a `DEFAULT` tenant. Enforcement of SHARED vs SEGREGATED at query time is deferred to Sprint 6.1 (see ADR-0001).
- **ADR-0001** (`docs/adr/0001-company-effective-dating-and-tenancy.md`) documents the effective-dating pattern, why overlap is enforced in the service layer rather than via `EXCLUDE USING gist`, why tenant isolation is stored-but-not-enforced this sprint, and the policy/team opaque-reference contract.

### Sprint 7 — Organization Structure Engine
- Flyway V7 — `organization_levels` (customer-configurable — nothing hardcoded), `organization_nodes` (master row referenced by employees), `organization_node_versions` (append-only structural history), `organization_node_inheritance_overrides` (per-node effective-dated override for ten inheritable resource kinds); seeds `ORGANIZATION_READ` / `ORGANIZATION_WRITE` / `ORGANIZATION_DELETE` and grants them to `SYSTEM_ADMIN`.
- **Configurable levels**: `OrganizationLevelService` CRUDs levels; `code`, `name`, `displaySequence`, `parentLevelId` are all data. The platform ships zero hardcoded vocabulary — no `enum Level { BU, DIVISION, … }` anywhere in the code.
- **Structural operations**: `OrganizationNodeService` supports rename, move (with cycle detection), merge (source deactivated + children reparented + versions on both sides), split (into N new nodes, optional deactivate-source), deactivate, reactivate. Every operation writes an `organization_node_versions` row so history is never lost.
- **Move-doesn't-touch-employees invariant**: employees reference `organization_nodes.id`; moving a node mutates that master row's `parent_node_id` and appends a version row — every referencing employee is automatically re-parented without a single employee-row update.
- **Inheritance engine**: `OrganizationInheritanceService.resolve(node, kind, asOf)` walks the parent chain returning the first live override, and returns unresolved so the consumer falls through to the company-level assignment (Sprint 6). Ten inheritable kinds: HOLIDAY_CALENDAR / PAYROLL_CALENDAR / LEAVE_POLICY / ATTENDANCE_POLICY / SHIFT_POLICY / WORKFLOW / COST_CENTRE / PROFIT_CENTRE / STATUTORY_REGISTRATION / SHARED_SERVICE.
- **Hierarchy-based security**: `OrganizationSecurityService.accessibleNodeIds(tenantId, rootIds)` expands the given roots to include every descendant, so a manager assigned to a parent node gains access to every child automatically; empty roots ⇒ empty access (no silent open-tree).
- **APIs** at `/api/v1/organization/*`:
  - Levels: create / list / get / update / status / soft-delete.
  - Nodes: create / search (paged + filtered) / get / tree / forest / rename / move / merge / split / deactivate / reactivate / soft-delete / versions.
  - Inheritance: set-override / list / retire / resolve (`GET /nodes/{id}/inheritance/{kind}?asOf=…`).
- **Soft delete + @Version + audit** on every entity; partial unique indexes on `(tenant_id, code)` for both levels and nodes so a soft-deleted row doesn't block reuse.
- **ADR-0002** (`docs/adr/0002-organization-structure-engine.md`) documents the level-as-data decision, the append-only version log that keeps history without repointing employees, and the walk-up inheritance algorithm.

### Sprint 8.1 — Person Engine
- Flyway V8 — `persons` (immutable Group Person ID + tenant), `person_versions` (append-only effective-dated core profile), `person_contacts` / `person_addresses` / `person_emergency_contacts` / `person_family_members` / `person_education` / `person_identity_documents`, `person_duplicate_rules` (per-tenant data-driven rules) + `person_id_sequence` (monotonic, never-reused); seeds `PERSON_READ` / `PERSON_WRITE` / `PERSON_DELETE` / `PERSON_DUPLICATE_OVERRIDE` and grants them to `SYSTEM_ADMIN`. Also seeds the six default duplicate rules for the DEFAULT tenant.
- **Group Person ID**: `PersonIdGenerator` produces `P000000001`-style ids via `nextval('person_id_sequence')`. Never reused, monotonic, safe under multi-node inserts.
- **Effective-dated profile**: creating a Person opens version #1; every profile edit appends a new version and closes the previous one — historical rows are immutable. `change_reason` + `changed_by` + optional `approved_by` on every version row for statutory audit.
- **Duplicate detection engine**: `DuplicateDetectionService` runs the enabled rules for the tenant (PAN / Aadhaar / Passport / Mobile / Email / Name+DOB), returns matches ordered by weight, and refuses create with `409` unless the caller supplies `overrideDuplicates=true` AND holds `PERSON_DUPLICATE_OVERRIDE` (else `403`). Rules are managed via `GET/PUT /api/v1/persons/duplicate-rules` — nothing is hardcoded.
- **Profile readiness**: `ProfileReadinessService` returns per-section (basic / contact / address / emergency / education / family / documents) + overall completion percentages. Readiness is a signal, never a gate — partial profiles are always creatable.
- **Uniqueness**: PAN + Aadhaar unique across all live rows in the DB via partial unique indexes; Group Person ID unique everywhere.
- **Fast search**: `GET /api/v1/persons/search?q=…` hits Person ID / name substring / mobile / email / any identity document number, and returns unique persons.
- **APIs** at `/api/v1/persons/*`: create / list / get / by-group-id / update-profile / status / soft-delete / versions / readiness / search / duplicate-check + sub-resources for contacts, addresses, emergency contacts, family, education, and identity documents. Full springdoc annotations, method-level `@PreAuthorize`.
- **Soft delete + @Version + audit** on every entity.
- **ADR-0003** (`docs/adr/0003-person-engine.md`) documents the identity model, sequence-backed IDs, effective-dating pattern, and data-driven duplicate detection.
- **Business rules** (`docs/business-rules/person-engine.md`) enumerates every rule the Person module encodes with its enforcement point.

---

## 2. Checklist status (Sprint 5 mandate)

| # | Item                                          | Status  | Evidence                                                                                       |
| - | --------------------------------------------- | ------- | ---------------------------------------------------------------------------------------------- |
| 1 | GitHub Actions CI/CD                          | ✅ Done | `.github/workflows/ci.yml`                                                                     |
| 2 | Spotless                                      | ✅ Done | `pom.xml`, config in `com.diffplug.spotless:spotless-maven-plugin` block                       |
| 3 | Checkstyle                                    | ✅ Done | `config/checkstyle/checkstyle.xml`                                                             |
| 4 | PMD                                           | ✅ Done | `config/pmd/pmd-ruleset.xml`                                                                   |
| 5 | SpotBugs                                      | ✅ Done | `config/spotbugs/spotbugs-exclude.xml`                                                         |
| 6 | JaCoCo ≥ 80 % coverage                        | ✅ Done | `jacoco-maven-plugin` `check` execution; BUNDLE / INSTRUCTION / COVEREDRATIO / 0.80            |
| 7 | Configurable password policy                  | ✅ Done | `PasswordPolicyProperties`, `PasswordPolicyValidator`, `app.security.password-policy.*` config |
| 8 | Soft delete for users / roles / permissions   | ✅ Done | V5 migration, `@SQLDelete` + `@SQLRestriction`, `DELETE /api/v1/users/{id}`                     |
| 9 | Complete audit fields                         | ✅ Done | `created_at` / `updated_at` / `created_by` / `updated_by` / `version` / `deleted_at`           |
| 10 | Refresh-token revocation on logout           | ✅ Done | `POST /api/v1/auth/logout`, `AuthenticationService.logout(...)`                                |
| 11 | Login audit improvements                     | ✅ Done | `LoginEventType`, `login_history.event_type`, refresh / logout events audited                  |
| 12 | Consistent API response model                | ✅ Done | `ApiError` (+ correlationId), broadened `GlobalExceptionHandler`, `CorrelationIdFilter`        |
| 13 | README improvements                          | ✅ Done | Badges, Quality-gates section, endpoint tables, soft-delete/correlation/audit docs             |

---

## 3. Technology inventory

| Concern       | Choice                                                    |
| ------------- | --------------------------------------------------------- |
| Language      | Java 21                                                   |
| Framework     | Spring Boot 3.3.5                                         |
| Build         | Maven 3.9                                                 |
| Database      | PostgreSQL 16                                             |
| Migrations    | Flyway (core + `flyway-database-postgresql`)              |
| Cache         | Redis 7 (config only, no runtime usage yet)               |
| Auth          | Spring Security + JJWT 0.12 (HS256)                       |
| ORM           | JPA / Hibernate 6                                         |
| API docs      | springdoc-openapi 2.6 + Swagger UI                        |
| Observability | Spring Boot Actuator + SLF4J MDC + `X-Request-ID`         |
| Tests         | JUnit 5 + AssertJ + Mockito + Testcontainers 1.20         |
| Container     | Docker + Docker Compose                                   |
| Formatter     | Spotless with Google Java Format (AOSP style)             |
| Linters       | Checkstyle 10.18, PMD 7.6, SpotBugs 4.8                   |
| Coverage      | JaCoCo 0.8 (≥ 80 % INSTRUCTION on the bundle)             |

---

## 4. Test coverage

| Suite                                | Kind         | Count | Runs locally without Docker? |
| ------------------------------------ | ------------ | ----- | ---------------------------- |
| `JwtServiceTest`                     | unit         | 2     | ✅                           |
| `PasswordPolicyValidatorTest`        | unit         | 7     | ✅                           |
| `UserServiceTest`                    | unit (mocks) | 12    | ✅                           |
| `AuthenticationServiceTest`          | unit (mocks) | 12    | ✅                           |
| `AuthControllerIntegrationTest`      | integration  | 5     | needs Docker (Testcontainers Postgres) |
| `UserControllerIntegrationTest`      | integration  | 12    | needs Docker                 |
| `EwosApplicationTests` (`contextLoads`) | smoke     | 1     | needs Docker                 |

**Unit total: 33 / 33 passing locally.** Integration suite (18 tests) runs in CI, where Docker is preinstalled on `ubuntu-latest`.

The 80 % JaCoCo floor is enforced against the aggregated coverage — i.e. unit + integration — and only takes effect during `mvn verify` (which CI runs).

---

## 5. Endpoint catalog

### Authentication (`com.ewos.identity.api.AuthController`)

| Method | Path                    | Auth              | Notes                                                          |
| ------ | ----------------------- | ----------------- | -------------------------------------------------------------- |
| POST   | `/api/v1/auth/login`    | public            | Issues JWT access + opaque refresh                             |
| POST   | `/api/v1/auth/refresh`  | public            | Rotates refresh token (old one revoked)                        |
| POST   | `/api/v1/auth/logout`   | public            | Idempotent; always 204; revokes token; audits `LOGOUT`         |

### User management (`com.ewos.identity.api.UserController`)

| Method | Path                                    | Required authority        | Notes                                              |
| ------ | --------------------------------------- | ------------------------- | -------------------------------------------------- |
| POST   | `/api/v1/users`                         | `SYSTEM_ADMIN`            | 409 on duplicate username/email                    |
| GET    | `/api/v1/users`                         | `USER_READ`               | Paged/sorted/filtered                              |
| GET    | `/api/v1/users/{id}`                    | `USER_READ`               | 404 if unknown or soft-deleted                     |
| PUT    | `/api/v1/users/{id}`                    | `USER_WRITE`              | Updates email + roles                              |
| PATCH  | `/api/v1/users/{id}/status`             | `USER_WRITE`              | Enable / disable                                   |
| POST   | `/api/v1/users/{id}/reset-password`     | `USER_WRITE`              | Admin reset (policy + reuse checks)                |
| POST   | `/api/v1/users/me/change-password`      | any authenticated         | Verifies current password first                    |
| DELETE | `/api/v1/users/{id}`                    | `USER_DELETE`             | **Soft** delete — sets `deleted_at`                |

---

## 6. Configuration surface (env vars)

| Variable                        | Purpose                                    | Default (dev)              |
| ------------------------------- | ------------------------------------------ | -------------------------- |
| `SPRING_PROFILES_ACTIVE`        | Active Spring profile                      | `dev`                      |
| `SPRING_DATASOURCE_URL`         | JDBC URL                                   | `jdbc:postgresql://...`    |
| `SPRING_DATASOURCE_USERNAME`    | DB user                                    | `ewos`                     |
| `SPRING_DATASOURCE_PASSWORD`    | DB password                                | `ewos`                     |
| `SPRING_DATA_REDIS_HOST/PORT`   | Redis host / port                          | `localhost:6379`           |
| `JWT_SECRET`                    | HMAC secret (≥ 256 bits in prod)           | placeholder                |
| `JWT_ISSUER`                    | JWT `iss` claim                            | `ewos`                     |
| `JWT_ACCESS_TTL`                | Access-token lifetime                      | `15m`                      |
| `JWT_REFRESH_TTL`               | Refresh-token lifetime                     | `7d`                       |
| `ADMIN_USERNAME/EMAIL/PASSWORD` | Default admin identity                     | `admin` / `admin@ewos.local` / `ChangeMe!Admin123` |
| `PASSWORD_MIN_LENGTH`           | Minimum password length                    | `8`                        |
| `PASSWORD_MAX_LENGTH`           | Maximum password length                    | `128`                      |
| `PASSWORD_REQUIRE_UPPERCASE`    | Require ≥ 1 uppercase                      | `true`                     |
| `PASSWORD_REQUIRE_LOWERCASE`    | Require ≥ 1 lowercase                      | `true`                     |
| `PASSWORD_REQUIRE_DIGIT`        | Require ≥ 1 digit                          | `true`                     |
| `PASSWORD_REQUIRE_SPECIAL`      | Require ≥ 1 special character              | `true`                     |
| `PASSWORD_HISTORY_SIZE`         | # of past passwords blocked from reuse     | `5`                        |

---

## 7. Remaining technical debt

Prioritized. None of these blocks moving into the next sprint, but each should be tackled before the platform reaches production.

### High priority
1. **`AbstractIntegrationTest` container reuse across classes** — currently each `@SpringBootTest` class starts its own Postgres container. In CI this doubles the integration-test wall time. Fix with a singleton container (`@Testcontainers(disabledWithoutDocker = true)` + static container reused via `@DynamicPropertySource` on a common base).
2. **No CORS bean is registered** — `SecurityConfig.cors(Customizer.withDefaults())` runs, but no `CorsConfigurationSource` bean exists, so cross-origin requests from a browser frontend will be blocked. Add a config-driven `CorsConfigurationSource` (planned in the login-API doc I published earlier — needs a follow-up commit).
3. **Actuator scrape endpoints are unauthenticated for `/health` / `/health/**` / `/info` only** — but `/actuator/prometheus`, `/actuator/metrics`, `/actuator/env` etc. become reachable the moment those exposures are turned on. Add an explicit authenticated policy for anything beyond health/info before enabling the Prometheus endpoint.
4. **`JWT_SECRET` default in `application.yml`** — dev-only placeholder. Production deployment should refuse to start if `JWT_SECRET` matches the placeholder. Add a `SmartLifecycle` check or `@PostConstruct` guard.

### Medium priority
5. **Role / Permission admin API is missing** — entities support soft delete + versioning but there's no controller to CRUD them. Blocked by product scope decision (assign / mint permissions at runtime vs. seed-only).
6. **Restore-from-soft-delete** — no `POST /api/v1/users/{id}/restore` yet. Simple to add (`UPDATE users SET deleted_at = NULL WHERE id = ?`), but partial-unique-index collision has to be handled first (a live row with the same username may exist).
7. **Refresh tokens aren't bound to a device / session** — reuse detection would be stronger if refresh tokens carried a family id and rotation-chain detection revoked the whole family on reuse. Currently only the presented token is revoked on `logout` / `refresh`.
8. **No account lockout / brute-force throttling** — Sprint 4 excluded this. `login_history` gives us the raw data; a simple threshold-based lock (N failed attempts within M minutes → set `accountNonLocked = false`) would close the gap.
9. **No `spring.jpa.hibernate.ddl-auto=validate`** — we run with `none`. Enabling `validate` in CI would catch entity/schema drift early. Requires cleaning up small mismatches (nullable / length) that today are harmless.
10. **No `@ControllerAdvice`-level request logging** — CorrelationIdFilter puts the id in MDC, but there is no access-log filter. `logbook`, `spring-boot-starter-actuator` request-metrics, or a simple `HandlerInterceptor` would give us the "one line per request" that ops teams expect.

### Low priority
11. **Testcontainers reuse via `.withReuse(true)` + `~/.testcontainers.properties` opt-in** — meaningful only for local iterative runs.
12. **Optimistic-lock retry policy** — currently 409s propagate to the client. A single retry on conflict for `changePassword` / `resetPassword` would improve UX under contention.
13. **OpenAPI examples on request/response bodies** — schemas are described, but only a handful of DTO fields carry `@Schema(example = ...)`.
14. **Refresh-token cleanup** — `RefreshTokenRepository` has a `deleteAllExpired(Instant)` query but no scheduled job runs it. Add `@Scheduled` daily sweep.
15. **Coverage exclusions could shrink** — `common/persistence/AuditorProvider` and `common/web/CorrelationIdFilter` deserve tests; currently the coverage exemption on `common/**` masks them.

### Sprint 7 deferrals (new)
16. **`EXCLUDE USING gist` for temporal non-overlap on `organization_node_inheritance_overrides`** — same reasoning and follow-up as Sprint 6.
17. **`override_ref` validation** — `override_ref` is an opaque UUID; the target tables (holiday calendar, cost centre, etc.) don't exist yet. Consumer modules must validate at read time when they land.
18. **Hierarchy-based security caching** — `OrganizationSecurityService` loads the tree per call today. When it starts firing on hot paths, memoise per (tenant, user) in Redis and invalidate on any node version write.

### Sprint 8.1 deferrals (new)
19. **PII encryption for PAN / Aadhaar / Passport** — plain-text in Sprint 8.1. When encryption lands, add a searchable hash column so duplicate detection still works.
20. **`PersonSearchService` and `DuplicateDetectionService.NAME_DOB`** load open versions into memory. Fine at HR scale; move to indexed `ILIKE` / trigram queries once a tenant crosses ~50k persons.
21. **Child-entity full history** — address / family / emergency / education / document rows are soft-delete + `@Version` today, not effective-dated versioned. Promote to full versioning if regulators require it.
22. **Duplicate-rule seeding for non-DEFAULT tenants** — V8 seeds only DEFAULT. Product must call `PUT /duplicate-rules/{id}` (or a seed script) for every new tenant.

### Not in scope for this repo
23. Employment, Payroll, Leave, Attendance modules — deferred to their respective sprints. Employment (Sprint 8.2) will FK `employments.person_id` → `persons.id` — never store person profile fields on the employment row.

---

## 8. How to run

Full instructions live in [`README.md`](./README.md). Quick reference:

```bash
# Format + full CI-equivalent pipeline (needs Docker)
mvn spotless:apply
mvn -q verify

# Unit tests only (no Docker required)
mvn -q test

# Boot the app with Postgres + Redis via compose
docker compose up --build
# Swagger UI: http://localhost:8080/swagger-ui.html
```

---

## 9. Change log for this document

- **2026-07-09** — Initial version. Reflects the tip of the `claude/quality-hardening` branch after the Sprint 5 hardening PR.
- **2026-07-13** — Sprint 6 (Company Configuration) added: `tenants` + `companies` + `company_versions` (effective-dated) + `statutory_registrations` + `company_bank_accounts` + `company_policy_assignments` + `company_shared_services`, clone modes, full REST surface at `/api/v1/companies/*`, ADR-0001 documenting effective-dating and tenancy. Deferred to Sprint 6.1: query-time enforcement of the tenant isolation policy (SHARED vs SEGREGATED), FK-enforced or read-time validation of opaque `policy_ref` / `team_ref`, DB-level temporal-overlap enforcement via `EXCLUDE USING gist`.
- **2026-07-13** — Sprint 7 (Organization Structure Engine) added: configurable `organization_levels` (nothing hardcoded), `organization_nodes` master + append-only `organization_node_versions` history, effective-dated `organization_node_inheritance_overrides` (ten inheritable kinds), full REST surface at `/api/v1/organization/*` covering CRUD + tree + forest + move/merge/split/rename/deactivate/reactivate/version-history/inheritance-resolve, `OrganizationSecurityService` for hierarchy-based access expansion, ADR-0002 documenting level-as-data + append-only history + walk-up inheritance. Employees FK the node id so moves cost O(1) writes.
- **2026-07-13** — Sprint 8.1 (Person Engine) added: `persons` (immutable `group_person_id` P000000001), append-only `person_versions` core profile, effective-dated `person_contacts`, `person_addresses` / `person_emergency_contacts` / `person_family_members` / `person_education` / `person_identity_documents` sub-resources, configurable `person_duplicate_rules` with six kinds (PAN / Aadhaar / Passport / Mobile / Email / Name+DOB), sequence-backed never-reused Person ID, profile-readiness engine that never blocks creation, full REST surface at `/api/v1/persons/*`, ADR-0003 + business-rules document. Deferred: PII encryption for PAN/Aadhaar/Passport, indexed name/DOB search, full-history versioning of child entities, per-tenant duplicate-rule seeding.
