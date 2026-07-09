# EWOS — Project Status

_Last updated: 2026-07-09._

Snapshot of the backend after Sprints 1, 2, 4, and 5 (hardening). This is
the single source of truth for what is delivered, what quality gates are
enforced, and what technical debt still exists.

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

### Not in scope for this repo
16. Employee, Payroll, Leave, Attendance, and Organization modules — deferred to their respective sprints.

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
