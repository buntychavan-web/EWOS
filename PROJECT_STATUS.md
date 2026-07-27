# EWOS — Project Status

_Last updated: 2026-07-27._

Snapshot of the backend after Sprints 1, 2, 4, 5 (hardening), the Sprint
1.1–1.4/2.x/4 program (multi-tenancy, RBAC hardening, employee identity
linking, workflow/approval engine), and the 2026-07-27 CTO Production
Readiness Audit remediation. This is the single source of truth for what is
delivered, what quality gates are enforced, and what technical debt still
exists. §§ 1–10 below are the original Sprint 1–5 snapshot from 2026-07-09
and are historical; §11 covers everything since.

---

## 0. CTO Production Readiness Audit — 2026-07-27

A full pass across both repos to close production-readiness gaps found in
a CTO-level audit. Commits on `ewos-main` (this repo) and `main`
(`enterprise-core`, the frontend):

- **CI**: the `ci.yml` branch trigger never matched `ewos-main` — the branch
  every sprint since Sprint 1.1 actually developed on — so CI had not run on
  real work for the entire Sprint 1–4 program. Fixed. Also reformatted 139
  pre-existing files that had drifted out of Spotless compliance (cosmetic
  only — verified against the full unit suite before/after).
- **Security**: added `AdminPasswordGuard` (mirrors the existing
  `JwtSecretGuard`) — refuses to boot outside dev/test if `ADMIN_PASSWORD`
  is still the `ChangeMe!Admin123` placeholder. Flipped
  `server.error.include-message` / `include-binding-errors` to
  secure-by-default (`never`) in the base `application.yml`, with dev/test
  opting back into verbose messages explicitly (prod already had this right,
  but nothing stopped a profile-less or new deployment from leaking).
- **Deployment**: added `k8s/`, `helm/ewos/`, `.env.example`, and
  `docs/operations/deployment.md` — see that guide for the full picture.
  None of this existed before.
- **Frontend** (`enterprise-core`): added a CI workflow (it had none),
  Vitest/RTL component tests (it had none) and a Playwright e2e smoke suite,
  a React error boundary, a feature-flag framework, a `beforeLoad` route
  guard replacing a `useEffect`-based one, global 401/session-expiry
  handling, and a Dockerfile + deployment guide. See that repo's
  `PROJECT_STATUS.md`/README for details.

Known limitations carried forward, not fixed by this pass (see §11.4):
GitHub's repository **default branch** could not be changed via any
available tool/API in this environment — `main` was fast-forwarded to match
`ewos-main`'s tip via a merged PR, but an operator with repo admin access
still needs to flip the default-branch setting in GitHub's UI/API if
`ewos-main` should stop being the branch developers push to.

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
1. ~~**`AbstractIntegrationTest` container reuse across classes**~~ ✅ **Resolved by PR #4.** `AbstractIntegrationTest` now uses the singleton-container pattern: one Postgres per JVM, started in a static initializer, terminated by Ryuk at JVM exit. See `CONTRIBUTING.md` § 6.4 and "Common pitfalls" below for the failure mode this closes.
2. ~~**No CORS bean is registered**~~ ✅ **Resolved.** `CorsConfig` registers a config-driven `CorsConfigurationSource` (`app.cors.*`), with a prod-profile fail-fast guard against wildcard origins — see ADR-0004.
3. ~~**Actuator scrape endpoints are unauthenticated beyond health/info**~~ ✅ **Resolved.** `application-prod.yml` restricts `management.endpoints.web.exposure.include` to `health,info` only; nothing else is exposed in prod.
4. ~~**`JWT_SECRET` default in `application.yml`**~~ ✅ **Resolved.** `JwtSecretGuard` refuses to boot outside dev/test with a placeholder or under-length secret. As of the 2026-07-27 audit, `AdminPasswordGuard` closes the equivalent gap for `ADMIN_PASSWORD`.

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

## 8. Common pitfalls

Real issues we've hit in this repo, the failure modes, and how the codebase now prevents them. Read this before making changes in the neighborhood.

### 8.1 Testcontainers + multiple `@SpringBootTest` classes in one JVM

**Symptom.** Every request in one integration-test class returns `500` with a JDBC connection-refused root cause, while a *different* integration-test class in the same CI run passed. Local unit tests are all green.

**Failure mode.** A static `@Container` field on an `@Testcontainers`-annotated base class scopes the container's lifecycle to *one* test class. The JUnit extension calls `container.start()` before the class and `container.stop()` after. The next `@SpringBootTest` class calls `start()` again on the same reference — but Testcontainers `GenericContainer.start()` is a **silent no-op on a container that has already been started and removed**. No exception, no restart. Spring's `@DynamicPropertySource` hands the *dead* port to Hikari, which times out at `connection-timeout` (default 30 s) per test attempt.

We hit this in CI [run 28996473363](https://github.com/buntychavan-web/EWOS/actions/runs/28996473363): 14 `UserControllerIntegrationTest` cases each burned 30 s of "connection refused" before failing.

**Prevention (already in place).** `AbstractIntegrationTest` uses the singleton-container pattern — start once in a `static { }` block, never call `stop()`, let Ryuk clean up at JVM exit. All `@SpringBootTest` classes in the JVM share one container. `CONTRIBUTING.md` § 6.4 documents this and forbids `@Testcontainers` / `@Container` in the codebase.

### 8.2 Full-column `UNIQUE` on soft-deletable tables

**Symptom.** Soft-deleting a user, then creating a new user with the same username, fails with a constraint violation instead of succeeding.

**Failure mode.** A full-column `UNIQUE (username)` constraint counts the soft-deleted row. `SELECT` queries filter it out via `@SQLRestriction`; `INSERT` doesn't.

**Prevention.** V5 migration replaced full-column `UNIQUE` on `users.username`, `users.email`, `roles.name`, `permissions.code` with **partial unique indexes** (`WHERE deleted_at IS NULL`). Any future soft-deletable table must follow the same pattern — `CONTRIBUTING.md` § 4 states the rule.

### 8.3 Editing a merged Flyway migration

**Symptom.** Production deploy aborts with `Validate failed: Migration checksum mismatch for migration V<n>`.

**Failure mode.** Flyway records the checksum of each migration when it first runs. Editing the file — even a comment or whitespace — changes the checksum; Flyway refuses to continue.

**Prevention.** Migrations are append-only. `CONTRIBUTING.md` § 4 forbids editing merged migrations; ship a new `V<next>__...sql` that alters/undoes.

### 8.4 Auditor is null on public / bootstrap flows

**Symptom.** Rows created by `IdentityBootstrap` or during login/refresh/logout have `created_by = NULL`.

**Failure mode.** `AuditorProvider` returns `Optional.empty()` when the `SecurityContext` has no authenticated principal — which is exactly the case for the initial admin bootstrap and for anything hit through `/api/v1/auth/*` (those endpoints are `permitAll()`).

**Prevention.** This is intentional and documented in `AuditorProvider`. Do **not** invent a "system" auditor to paper over it — that would obscure genuine anonymous writes. If a real use case emerges (e.g. attributing scheduled-job writes), add a distinct sentinel UUID with an explicit contract, don't reuse a real user id.

### 8.5 Logging secrets or tokens

**Symptom.** Access tokens, refresh tokens, or BCrypt hashes appear in stdout / log files.

**Failure mode.** A well-meaning `log.info("...{}", tokenResponse)` prints the full `TokenResponse#toString()`, including the access + refresh tokens.

**Prevention.** SLF4J is available; use it *only* on non-sensitive payloads. Refresh tokens are SHA-256 hashed before storage, so the plaintext exists in memory only during a single request. Do not log `TokenResponse`, `LoginRequest`, `ChangePasswordRequest`, `ResetPasswordRequest`, or their fields. Correlation IDs are safe to log — that's the point of the MDC.

### 8.6 `Checkstyle` `ConstantName` vs SLF4J `log`

**Symptom.** Checkstyle fails on every `private static final Logger log = ...` line because the default `ConstantName` rule expects `UPPER_SNAKE`.

**Failure mode.** Checkstyle's out-of-the-box `ConstantName` treats `static final` fields as constants and enforces the case convention.

**Prevention.** `config/checkstyle/checkstyle.xml` deliberately omits `ConstantName`. Lowercase `log` is idiomatic SLF4J and this repo prefers it. If you add a *real* constant (`public static final int MAX_FOO = 42`), do use `UPPER_SNAKE`.

### 8.7 Loosening a quality gate to make CI green

**Symptom.** Someone raises a JaCoCo threshold from 80 % to 60 %, or adds a broad `<exclude>com/ewos/**</exclude>` to shut up SpotBugs.

**Failure mode.** The gate stops enforcing what it was there for. Regressions land quietly.

**Prevention.** `CONTRIBUTING.md` § 2 lists this as prohibited without explicit reviewer + team-lead consent. The PR template's "Scope guardrails" checklist asks the author to confirm no gate was loosened. Reviewers should reject PRs that touch `pom.xml` gate configuration without a linked justification.

---

## 9. How to run

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

## 11. Remaining known risks (2026-07-27 audit)

- **GitHub default branch** — still `main` at the GitHub settings level in
  a way this environment's tools couldn't change (no repo-settings API
  available). `main` has been fast-forwarded to `ewos-main`'s tip so there
  is no code divergence, but a repo admin should still flip the default
  branch in GitHub's UI if the intent is for `ewos-main` to stop being a
  separate line developers push to.
- **Backend↔frontend API routing in production** — the frontend's
  `/api/v1/*` calls are same-origin relative paths with no production
  reverse-proxy/rewrite configured (see `enterprise-core`'s
  `docs/DEPLOYMENT.md`). An operator must wire this up per their actual
  Cloudflare/ingress topology before both sides can talk to each other in a
  real deployment.
- **Kafka messaging is off by default** (`APP_MESSAGING_KAFKA_ENABLED=false`)
  and untested against a real broker outside `docker-compose.yml`'s local
  dev setup.
- **`helm/ewos` charts were not run through `helm lint`/`helm template`** —
  no Helm CLI was reachable in the sandbox this audit ran in (network
  policy blocked `get.helm.sh`). Reviewed by hand against the raw `k8s/`
  manifests they mirror; run `helm lint` before a first real install.
- Items 5–16 in §7 above (Role/Permission admin API gaps notwithstanding —
  much of that has since shipped in Sprint 1.4 — refresh-token device
  binding, restore-from-soft-delete, JPA `ddl-auto=validate` in CI, request
  access logging, Testcontainers reuse, optimistic-lock retry, OpenAPI
  examples, refresh-token cleanup job, coverage exclusions) were not
  re-verified in this audit pass and should be treated as still open unless
  a later section of this document says otherwise.

---

## 12. Change log for this document

- **2026-07-09** — Initial version. Reflects the tip of the `claude/quality-hardening` branch after the Sprint 5 hardening PR.
- **2026-07-09** — Added § 8 "Common pitfalls" with the Testcontainers singleton-container writeup, soft-delete/UNIQUE, Flyway checksum, null auditor, log-hygiene, Checkstyle-vs-SLF4J-log, and gate-loosening. Marked tech-debt item #1 as resolved by PR #4. Renumbered § 8 → § 9 and § 9 → § 10.
- **2026-07-27** — CTO Production Readiness Audit: added §0 summarizing the audit's changes, marked tech-debt items #2–#4 in §7 as resolved (CORS bean, actuator exposure, JWT secret guard — plus the new `AdminPasswordGuard`), added §11 "Remaining known risks" reflecting this pass's findings, and noted that this document's §§1–10 predate the Sprint 1.1–4/2.x program and were not rewritten wholesale in this pass — treat sprint-by-sprint detail past Sprint 5 as living in each sprint's own completion report rather than here.
