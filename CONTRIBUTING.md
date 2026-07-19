# Contributing to EWOS

This document is the source of truth for **how work lands in this repository**. Read it before your first PR. If a rule here conflicts with something you're about to do, either the rule needs to change (open a discussion) or your change needs to.

## TL;DR

- Fork of `main` → feature branch → PR back into `main`. No direct pushes to `main`.
- Fill in the PR template (`.github/pull_request_template.md`) — every checkbox.
- Run `mvn verify` locally before requesting review. Docker must be running (Testcontainers).
- **Never** add `@Testcontainers` / `@Container` in a test class — inherit `AbstractIntegrationTest` instead. See § 6.4.
- Do not disable or loosen quality gates without team agreement.
- Do not add HRMS domain modules (Employee, Payroll, Leave, Attendance, Organization) outside their designated sprint.

---

## 1. Development environment

### Prerequisites

- JDK **21** (Temurin recommended)
- Maven **3.9+**
- Docker **24+** with Docker Compose v2 — integration tests and the compose stack both depend on it

### First-time setup

```bash
git clone https://github.com/buntychavan-web/EWOS.git
cd EWOS
mvn spotless:apply       # normalise formatting to Google Java Format (AOSP)
mvn verify               # full CI-equivalent pipeline; ~2 min with Docker
```

If `mvn verify` fails on your machine but not on CI, the most common cause is a stopped or misconfigured Docker daemon. Check `docker ps` before opening an issue.

---

## 2. Branching, commits, PRs

### Branches

- All work off `main` via a topic branch. Suggested prefixes: `claude/**`, `feature/**`, `fix/**`, `chore/**`, `docs/**`.
- Branch name mentions the sprint or task, not the person, e.g. `feature/employee-profile-api` or `fix/refresh-token-rotation-race`.
- Keep the branch short-lived. Rebase onto `main` before requesting review; do not merge `main` into your feature branch.

### Commits

- Imperative subject, ≤ 72 chars. Body explains **why**, not what.
- One logical change per commit. If a review round produces new changes, add a fixup commit; squash before merge.
- Never `--amend` or `--force-push` a branch that another engineer has already reviewed unless you've told them.

### Pull requests

- One PR per topic. If your work grew, split it before requesting review.
- Fill in the PR template completely. Blank checklists tell the reviewer you didn't run the checks — expect to be asked to redo them.
- CI must be green before merge. If it's red for a pre-existing reason, link the tracking issue.
- Two clean states before merge: (1) all quality gates green, (2) reviewer has approved.

Prohibited without explicit reviewer + team-lead consent:

- Skipping hooks: `--no-verify`, disabling `spotless`/`checkstyle`/`pmd`/`spotbugs`/`jacoco`.
- Rewriting merged history.
- Lowering the JaCoCo floor or adding broad JaCoCo `<excludes>`.
- Force-pushing to `main` or to any released branch.

---

## 3. Repository rules

### Layout

- Package-by-feature under `com.ewos.<bounded-context>` (`identity`, future: `employee`, etc.).
- Within a context, use `api / application / domain / infrastructure` sub-packages. Do not cross layers upward.
- Cross-cutting code lives under `com.ewos.common.*` and `com.ewos.config.*`.

### Dependency injection

- **Constructor injection only.** No `@Autowired` on fields; no setter injection.
- Prefer records for immutable value types: DTOs, `@ConfigurationProperties`, response envelopes.

### API design

- Every endpoint carries `@Operation`, `@ApiResponses` (or an equivalent OpenAPI annotation) so springdoc emits an accurate schema.
- Every mutating endpoint carries `@PreAuthorize` with an explicit authority — never `permitAll()` for state-changing operations.
- Errors go through `ApiError` + `GlobalExceptionHandler`. Do not build error JSON by hand in controllers.
- Responses that carry a page of data use `Page<T>` (Spring Data), never a hand-rolled envelope.

### Persistence

- Every entity extends `com.ewos.common.persistence.AuditableEntity`. That gets you `id`, `created_at`, `updated_at`, `created_by`, `updated_by` for free.
- Soft-deletable entities also declare `deleted_at` + `@Version` and use `@SQLDelete` / `@SQLRestriction("deleted_at IS NULL")` — see `User`, `Role`, `Permission`.
- Human-visible unique columns on soft-deletable tables use **partial** unique indexes (`WHERE deleted_at IS NULL`), not full-column `UNIQUE`. See § 6 and V5 migration.
- Repositories are Spring Data interfaces under `infrastructure/persistence/`. Any custom SQL lives there, not in services.

### Configuration

- Configuration comes from `application.yml` layered by profile (`application-{dev,test,prod}.yml`), overridable via environment variables.
- Secrets **never** land in `application.yml` — use env vars with a placeholder default that a production deployment must override (e.g. `JWT_SECRET`, `ADMIN_PASSWORD`).
- New `@ConfigurationProperties` records live in the owning context's `application` package. Add them to the env-var table in `README.md`.

### Logging

- SLF4J via `private static final Logger log = LoggerFactory.getLogger(Foo.class);`. Yes, lowercase `log` — `ConstantName` is intentionally omitted from Checkstyle (see § 6.5).
- Never log secrets, tokens, or password hashes.
- The `CorrelationIdFilter` puts `correlationId` into the MDC — logback pattern already renders it. Rely on it; don't invent a parallel tracing key.

---

## 4. Database migrations

- Flyway is **append-only**. Once a `V<n>__...sql` has landed on `main`, it is frozen — never edit, rename, or delete it. Ship a new `V<next>__...` that alters/undoes.
- Naming: `V<version>__<snake_case_description>.sql`. Example: `V6__add_employee_table.sql`.
- Every non-lookup table gets `created_at`, `updated_at`, `created_by`, `updated_by`, `deleted_at`, `version` (see `AuditableEntity`).
- Human-facing uniqueness on soft-deletable tables: **partial** unique index filtered on `deleted_at IS NULL`. Full-column `UNIQUE` collides with re-created rows.

---

## 5. Security

- Never commit `.env`, credential files, JKS/PEM stores, or anything in that shape. `.env` / `.env.local` are gitignored.
- `JWT_SECRET` and `ADMIN_PASSWORD` defaults in `application.yml` are placeholders — a real deployment must override them.
- BCrypt for password hashing. SHA-256 (hex-encoded) for opaque token storage. No plaintext credentials in the database, ever.
- New endpoints inherit `SecurityConfig`'s stateless + CSRF-off policy. Add the path to `PUBLIC_ENDPOINTS` **only** if it is genuinely unauthenticated (login/refresh/logout, actuator health/info, Swagger). Everything else is authenticated by default.

---

## 6. Testing

### 6.1 Unit tests

- Fast, deterministic, no I/O. No Spring context.
- Named `*Test.java` in the same package as the class under test.
- Mockito for collaborators; AssertJ for assertions.
- If you can't test it without a Spring context, it's an integration test — see below.

### 6.2 Integration tests

- Named `*IntegrationTest.java`.
- **MUST** extend `com.ewos.AbstractIntegrationTest`. That base class wires the singleton Postgres container and `@DynamicPropertySource`. See § 6.4 for why this matters.
- Use unique identifiers to isolate test data (UUID + monotonic counter, as in `UserControllerIntegrationTest#uniqueUsername`). Never assume an empty database — the container is shared.
- Do not truncate tables between tests; if a test needs fresh data, insert it and assert against what you inserted. If a test genuinely needs an empty view of a table, use `@Sql(scripts = "…", executionPhase = BEFORE_TEST_METHOD)` for that specific test.

### 6.3 What each gate enforces

| Gate       | Command                    | Fails on                                                    |
| ---------- | -------------------------- | ----------------------------------------------------------- |
| Format     | `mvn spotless:check`       | Any file not matching Google Java Format (AOSP)             |
| Style      | `mvn checkstyle:check`     | `config/checkstyle/checkstyle.xml` violations               |
| Static     | `mvn pmd:check`            | `config/pmd/pmd-ruleset.xml` violations                     |
| Bugs       | `mvn spotbugs:check`       | Medium+ findings not in `config/spotbugs/spotbugs-exclude.xml` |
| Coverage   | `mvn jacoco:check`         | < 80 % instruction coverage on the BUNDLE                   |
| Tests      | `mvn test`                 | Any failing unit or integration test                        |

`mvn verify` chains all six.

### 6.4 Testcontainers best practices — READ THIS

**Rule:** in this repo, all Postgres-backed integration tests inherit `AbstractIntegrationTest`, which uses the **singleton container** pattern. Do **not** add `@Testcontainers` or `@Container` to any test class.

**Why the singleton pattern:**

The obvious approach — a `@Container` static field + `@Testcontainers` on the class — scopes the container's lifecycle to a single test class. JUnit calls `container.start()` before the first test in that class and `container.stop()` after the last. When a *second* `@SpringBootTest` class runs in the same JVM, JUnit calls `start()` again on the same static reference. Testcontainers `GenericContainer.start()` is a **silent no-op on a container that has already been started and removed** — no exception is raised, no new container is launched, and the port that `@DynamicPropertySource` handed to Spring is now dead. Hikari waits `connection-timeout` (default 30 s) per test attempt before failing with `PSQLException: Connection refused`. On a suite with 15+ tests you burn ≈ 7 minutes of CI time returning 500s that all have the same JDBC root cause.

That is exactly what happened in [run 28996473363](https://github.com/buntychavan-web/EWOS/actions/runs/28996473363), and the fix is why this section exists.

**The singleton pattern:**

```java
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("ewos_test")
                .withUsername("ewos")
                .withPassword("ewos");
        POSTGRES.start();          // once per JVM
    }
    // no stop() call anywhere — Ryuk terminates the container at JVM exit

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

Every `@SpringBootTest` integration class extends this. All classes in one JVM see the same container, the same JDBC URL, and the same DB state.

**When you might legitimately need something else:**

1. **A new container image (Redis, Kafka, LocalStack, …)** — add another static-initialized field to `AbstractIntegrationTest`, or a peer base class if the concern is orthogonal. Same rule: `start()` in a `static { }` block, never call `stop()`.
2. **A test that genuinely needs a pristine database** — do not spin up a second container. Use `@Sql` scripts to truncate/reseed for that one test, or (better) design the test to be idempotent against a shared DB.
3. **Parallel test execution across JVMs** — Surefire forks are OK; each fork gets its own singleton container. Do **not** run `@SpringBootTest` classes in parallel *within* one JVM — Spring's context caching and our singleton container both assume single-threaded class scheduling.

**Anti-patterns that get rejected in review:**

- `@Testcontainers` on a test class.
- `@Container` on a field.
- `container.stop()` in a `@BeforeAll` / `@AfterAll` / static initializer.
- Adding `@DirtiesContext` "to make it work" — that hides a container-lifecycle bug and slows every test run to a crawl.

### 6.5 Coverage

- JaCoCo floor is 80 % **instruction coverage on the BUNDLE**.
- Exclusions live in `pom.xml`: `EwosApplication`, `config/**`, DTOs, entities, repository interfaces. Everything else counts. Do not broaden these without a reviewer sign-off — the point of the floor is to catch untested branches in services and controllers.

---

## 7. Definition of Done

A PR is done when:

- The PR template checklist is fully checked.
- CI is green.
- A reviewer has approved.
- Documentation that mentions the changed behavior (`README.md`, `PROJECT_STATUS.md`, this file, OpenAPI schemas) has been updated in the same PR.
- If it introduces observable behavior or a new endpoint, the acceptance criteria in the PR's Test Plan have been executed by the author.

---

## 8. Reporting

- Bugs → open a GitHub issue, include reproduction steps and the CI run URL if it's a test failure.
- Security concerns → **do not** open a public issue. Contact the maintainers privately.
- Ambiguous requirements → ask before writing code. It's cheaper than the follow-up PR.
